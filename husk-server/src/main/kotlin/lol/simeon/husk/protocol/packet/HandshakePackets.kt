package lol.simeon.husk.protocol.packet

import lol.simeon.husk.protocol.PacketMapping
import lol.simeon.husk.protocol.ServerboundPacket
import lol.simeon.husk.protocol.StateRegistry
import lol.simeon.mycelium.Version

/** First packet a client sends. `nextState`: 1 = status, 2 = login, 3 = transfer (1.20.5+). */
class HandshakePacket(
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Int,
    val nextState: Int,
) : ServerboundPacket

object HandshakePackets {
    fun register(reg: StateRegistry) {
        // id 0x00 for the whole 1.8 – 26.3 range
        reg.serverbound("set_protocol", PacketMapping.of(Version.MINECRAFT_1_8 to 0x00)) { buf, _ ->
            HandshakePacket(
                protocolVersion = buf.readVarInt(),
                serverAddress = buf.readString(255),
                serverPort = buf.buf.readUnsignedShort(),
                nextState = buf.readVarInt(),
            )
        }
    }
}
