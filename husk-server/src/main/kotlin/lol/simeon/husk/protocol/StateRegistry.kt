package lol.simeon.husk.protocol

import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version

/** Packet id ↔ type mapping for one connection state (both directions). */
class StateRegistry {
    private class Registration(val name: String, val mapping: PacketMapping)
    private class ServerboundEntry(val name: String, val mapping: PacketMapping, val decoder: PacketDecoder)

    private val clientbound = LinkedHashMap<Class<out ClientboundPacket>, Registration>()
    private val serverbound = ArrayList<ServerboundEntry>()

    /** `name` is the minecraft-data packet name (keeps id tables greppable/fillable). */
    fun clientbound(type: Class<out ClientboundPacket>, name: String, mapping: PacketMapping) {
        clientbound[type] = Registration(name, mapping)
    }

    fun serverbound(name: String, mapping: PacketMapping, decoder: PacketDecoder) {
        serverbound += ServerboundEntry(name, mapping, decoder)
    }

    /** Wire id for [packet] at raw [protocol], or null if it has no id there (absent/unfilled). */
    fun idOf(packet: ClientboundPacket, protocol: Int): Int? =
        clientbound[packet.javaClass]?.mapping?.idFor(protocol)

    fun idOf(packet: ClientboundPacket, version: Version): Int? = idOf(packet, version.protocol)

    /** Decodes the serverbound packet with wire [id] at raw [protocol], or null if unknown. */
    fun decode(id: Int, protocol: Int, version: Version, buf: ByteMessage): ServerboundPacket? {
        for (entry in serverbound) if (entry.mapping.idFor(protocol) == id) return entry.decoder.decode(buf, version)
        return null
    }

    fun decode(id: Int, version: Version, buf: ByteMessage): ServerboundPacket? =
        decode(id, version.protocol, version, buf)
}

/** Reified sugar: `reg.clientbound<JoinGamePacket>("login", mapping)`. */
inline fun <reified T : ClientboundPacket> StateRegistry.clientbound(name: String, mapping: PacketMapping) =
    clientbound(T::class.java, name, mapping)
