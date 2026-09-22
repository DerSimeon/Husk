package lol.simeon.husk.net

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import lol.simeon.husk.config.HuskConfig
import lol.simeon.husk.config.PingConfig
import lol.simeon.husk.protocol.RegistryStore
import lol.simeon.husk.session.Connection
import lol.simeon.husk.session.ConnectionState
import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version
import kotlin.test.Test
import kotlin.test.assertEquals

private val testConfig = HuskConfig(
    host = "0.0.0.0", port = 25565, maxPlayers = 20, rootsDir = "roots", brand = "Husk",
    dimension = "minecraft:the_nether", playerList = true,
    ping = PingConfig(versionName = "Husk", protocol = -1, description = "<gray>test"),
)

class PacketDecoderTest {
    @Test
    fun `decodes a framed handshake and transitions state and version`() {
        val connection = Connection()
        val channel = EmbeddedChannel(
            VarIntFrameDecoder(),
            PacketDecoder(connection),
            ConnectionHandler(connection, testConfig, RegistryStore()),
        )

        // Build the packet body: id(0x00), protocol, address, port, nextState(2 = login).
        val body = Unpooled.buffer()
        ByteMessage(body).apply {
            writeVarInt(0x00)
            writeVarInt(770) // 1.21.5
            writeString("localhost")
            buf.writeShort(25565)
            writeVarInt(2)
        }
        // Prefix with the VarInt length.
        val frame = Unpooled.buffer()
        ByteMessage(frame).writeVarInt(body.readableBytes())
        frame.writeBytes(body)

        channel.writeInbound(frame)

        assertEquals(ConnectionState.LOGIN, connection.state)
        assertEquals(Version.MINECRAFT_1_21_5, connection.protocolVersion)
        channel.finishAndReleaseAll()
    }
}
