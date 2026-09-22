package lol.simeon.husk.protocol.packet

import lol.simeon.husk.protocol.ClientboundPacket
import lol.simeon.husk.protocol.PacketMapping
import lol.simeon.husk.protocol.ServerboundPacket
import lol.simeon.husk.protocol.StateRegistry
import lol.simeon.husk.protocol.clientbound
import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version

class StatusRequestPacket : ServerboundPacket
class PingRequestPacket(val payload: Long) : ServerboundPacket

/** JSON status document (favicon, players, version, description). */
class StatusResponsePacket(val json: String) : ClientboundPacket {
    override fun encode(buf: ByteMessage, version: Version) = buf.writeString(json)
}

class PongResponsePacket(val payload: Long) : ClientboundPacket {
    override fun encode(buf: ByteMessage, version: Version) {
        buf.buf.writeLong(payload)
    }
}

object StatusPackets {
    fun register(reg: StateRegistry) {
        // status ids are 0x00 / 0x01 across the whole range
        reg.serverbound("ping_start", PacketMapping.of(Version.MINECRAFT_1_8 to 0x00)) { _, _ -> StatusRequestPacket() }
        reg.serverbound("ping", PacketMapping.of(Version.MINECRAFT_1_8 to 0x01)) { buf, _ ->
            PingRequestPacket(buf.buf.readLong())
        }
        reg.clientbound<StatusResponsePacket>("server_info", PacketMapping.of(Version.MINECRAFT_1_8 to 0x00))
        reg.clientbound<PongResponsePacket>("ping", PacketMapping.of(Version.MINECRAFT_1_8 to 0x01))
    }
}
