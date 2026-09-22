package lol.simeon.husk.protocol

import lol.simeon.mycelium.Version

/**
 * Maps a packet to its wire id across protocol versions.
 *
 * List only the versions where the id *changes*; [idFor] resolves any protocol to the id of
 * the highest listed version ≤ it. A packet absent before some version simply has no entry
 * for earlier ones, and [idFor] returns null there (and for an unfilled [todo] mapping).
 *
 * Immutable and validated at construction: protocols are non-negative and distinct.
 */
class PacketMapping private constructor(private val entries: List<Entry>) {
    private class Entry(val protocol: Int, val id: Int)

    /** Resolve by a [Version]. */
    fun idFor(version: Version): Int? = idFor(version.protocol)

    /** Resolve by a raw protocol number (what the decoder has after handshake). */
    fun idFor(protocol: Int): Int? {
        for (entry in entries) if (protocol >= entry.protocol) return entry.id
        return null
    }

    /** True once at least one id has been filled in (i.e. not [todo]). */
    val isFilled: Boolean get() = entries.isNotEmpty()

    /** Change-points as `(protocol, id)`, descending by protocol — for building per-version indexes. */
    fun points(): List<Pair<Int, Int>> = entries.map { it.protocol to it.id }

    override fun toString(): String =
        entries.joinToString(prefix = "PacketMapping(", postfix = ")") { "${it.protocol}=0x%02x".format(it.id) }

    companion object {
        /** e.g. `of(Version.MINECRAFT_1_8 to 0x01, Version.MINECRAFT_1_9 to 0x1F)`. */
        fun of(vararg points: Pair<Version, Int>): PacketMapping =
            build(points.map { it.first.protocol to it.second })

        /**
         * Raw protocol-number form, e.g. `ofProtocol(47 to 0x21, 340 to 0x0B)`.
         * Independent of the [Version] enum's granularity — used by the generated tables
         * (tools/generate-packet-ids.sh) so id change-points land on exact protocol numbers.
         */
        fun ofProtocol(vararg points: Pair<Int, Int>): PacketMapping =
            build(points.map { it.first to it.second })

        /** Placeholder for an id table not yet filled from minecraft-data. */
        fun todo(): PacketMapping = PacketMapping(emptyList())

        private fun build(points: List<Pair<Int, Int>>): PacketMapping {
            val protocols = points.map { it.first }
            require(protocols.all { it >= 0 }) { "PacketMapping protocols must be >= 0: $protocols" }
            require(protocols.toSet().size == protocols.size) { "PacketMapping has duplicate protocols: $protocols" }
            return PacketMapping(points.map { Entry(it.first, it.second) }.sortedByDescending { it.protocol })
        }
    }
}
