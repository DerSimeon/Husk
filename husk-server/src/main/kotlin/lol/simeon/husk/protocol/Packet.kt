package lol.simeon.husk.protocol

import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version

/** Marker for any protocol packet. */
interface Packet

/** A packet the server sends. Serialises itself for a given protocol [version]. */
interface ClientboundPacket : Packet {
    fun encode(buf: ByteMessage, version: Version)

    /**
     * Encode with the client's exact [protocol] as well. Override only when a format changed at a
     * protocol the coarse [Version] enum can't express (e.g. 1.9.1); defaults to [encode].
     */
    fun encode(buf: ByteMessage, version: Version, protocol: Int) = encode(buf, version)
}

/** A packet the server receives. Decoding is done by a registered [PacketDecoder]. */
interface ServerboundPacket : Packet

/** Builds a [ServerboundPacket] from the buffer for a given protocol version. */
fun interface PacketDecoder {
    fun decode(buf: ByteMessage, version: Version): ServerboundPacket
}
