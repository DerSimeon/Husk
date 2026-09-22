package lol.simeon.husk.protocol

import lol.simeon.husk.protocol.packet.RegistryDataPacket
import lol.simeon.husk.protocol.packet.UpdateTagsPacket
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.IntArrayBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import java.util.concurrent.ConcurrentHashMap

/** The CONFIGURATION-phase registry data + tags to send a client, prebuilt for its protocol. */
class PreparedRegistry(
    /** The raw captured codec: embedded in Join Game (1.16–1.20.1) or sent whole (1.20.2–1.20.4). */
    val codec: CompoundBinaryTag,
    val registryData: List<RegistryDataPacket>,
    val tags: UpdateTagsPacket,
) {
    private val dimensionEntries =
        registryData.firstOrNull { it.registryId == "minecraft:dimension_type" }?.entries.orEmpty()

    /** dimension_type entry id -> its registry index, for the Join Game SpawnInfo. */
    val dimensionIndex: Map<String, Int> =
        dimensionEntries.withIndex().associate { (i, e) -> e.key to i }

    /** dimension_type entry id -> its section count (height / 16), for empty-chunk sizing. */
    val dimensionSections: Map<String, Int> =
        dimensionEntries.associate { it.key to ((it.data?.getInt("height") ?: 256) / 16) }
}

/**
 * Loads the captured vanilla registry codecs and tag sets (one merged NBT each, keyed by protocol
 * number) and turns them into ready-to-send CONFIGURATION packets per client protocol.
 *
 * A client's protocol resolves to the highest captured protocol ≤ it, so in-between point releases
 * reuse the nearest older capture. Prepared packets are cached per resolved protocol.
 */
class RegistryStore(
    codecResource: String = "/codecs.nbt",
    tagsResource: String = "/tags.nbt",
) {
    private val codecs: CompoundBinaryTag = read(codecResource)
    private val tags: CompoundBinaryTag = read(tagsResource)

    /** Captured codec protocols, ascending — the resolvable set. */
    private val codecProtocols: List<Int> = codecs.keySet().map(String::toInt).sorted()

    private val cache = ConcurrentHashMap<Int, PreparedRegistry>()

    /** Prepared registry packets for [protocol], or null if no capture is applicable. */
    fun forProtocol(protocol: Int): PreparedRegistry? {
        val resolved = codecProtocols.lastOrNull { it <= protocol } ?: return null
        return cache.getOrPut(resolved) { build(resolved) }
    }

    private fun build(protocol: Int): PreparedRegistry {
        val key = protocol.toString()
        val codec = codecs.getCompound(key)
        return PreparedRegistry(codec, buildRegistryData(codec), buildTags(tags.getCompound(key)))
    }

    /** Codec form: registryId -> { type, value: [ { name, id, element } ] }. */
    private fun buildRegistryData(codec: CompoundBinaryTag): List<RegistryDataPacket> =
        codec.keySet().map { registryId ->
            val entries = codec.getCompound(registryId).getList("value", BinaryTagTypes.COMPOUND)
                .map { it as CompoundBinaryTag }
                .sortedBy { it.getInt("id") }
                .map { RegistryDataPacket.Entry(it.getString("name"), it.getCompound("element")) }
            RegistryDataPacket(registryId, entries)
        }

    /** Tag form: registryId -> { tagName -> int-array/list of entry indices }. */
    private fun buildTags(tags: CompoundBinaryTag): UpdateTagsPacket {
        val registries = tags.keySet().map { registryId ->
            val perRegistry = tags.getCompound(registryId)
            val tagList = perRegistry.keySet().map { tagName ->
                UpdateTagsPacket.Tag(tagName, intIds(perRegistry.get(tagName)))
            }
            UpdateTagsPacket.TagRegistry(registryId, tagList)
        }
        return UpdateTagsPacket(registries)
    }

    /** A tag's entries may be stored as a TAG_Int_Array or a TAG_List of ints. */
    private fun intIds(tag: net.kyori.adventure.nbt.BinaryTag?): IntArray = when (tag) {
        is IntArrayBinaryTag -> tag.value()
        is ListBinaryTag -> IntArray(tag.size()) { tag.getInt(it) }
        else -> IntArray(0)
    }

    private fun read(resource: String): CompoundBinaryTag {
        val stream = javaClass.getResourceAsStream(resource)
            ?: error("Missing registry resource $resource")
        return stream.use { BinaryTagIO.unlimitedReader().read(it, BinaryTagIO.Compression.GZIP) }
    }
}
