package lol.simeon.husk.net

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import lol.simeon.husk.config.HuskConfig
import lol.simeon.husk.config.PingConfig
import lol.simeon.husk.protocol.PacketRegistry
import lol.simeon.husk.protocol.RegistryStore
import lol.simeon.husk.protocol.packet.JoinGamePacket
import lol.simeon.husk.protocol.packet.PlayerPositionPacket
import lol.simeon.husk.session.Connection
import lol.simeon.husk.session.ConnectionState
import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real pipeline from handshake to spawn for every supported protocol and checks the
 * client ends up in PLAY having received Join Game and the spawn position. An encoder that throws
 * (missing id, unsupported shape) drops its frame, so a missing packet fails here.
 */
class JoinFlowTest {
    private val config = HuskConfig(
        host = "0.0.0.0", port = 25565, maxPlayers = 20, rootsDir = "roots", brand = "Husk",
        dimension = "minecraft:overworld", playerList = true,
        ping = PingConfig(versionName = "Husk", protocol = -1, description = "test"),
    )
    private val registries = RegistryStore()

    // Every Version plus in-between protocols the enum can't express (1.9.1, 1.12.1, 1.16.1,
    // 1.16.4, 1.17.1, 1.20.1, 1.20.4).
    private val protocols = (Version.entries.filter { it.protocol > 0 }.map { it.protocol } +
        listOf(108, 110, 338, 736, 754, 756, 763, 765, 777)).distinct().sorted()

    @Test
    fun `every protocol reaches PLAY with join game and spawn position`() {
        for (protocol in protocols) join(protocol)
    }

    private fun join(protocol: Int) {
        val connection = Connection()
        val channel = EmbeddedChannel(
            VarIntFrameDecoder(), VarIntFrameEncoder(),
            PacketDecoder(connection), PacketEncoder(connection),
            ConnectionHandler(connection, config, registries),
        )
        val version = connection.also { it.protocol = protocol }.protocolVersion
        val out = mutableListOf<Int>() // outbound packet ids, in order

        fun send(id: Int, body: ByteMessage.() -> Unit) {
            val payload = Unpooled.buffer().also { ByteMessage(it).apply { writeVarInt(id); body() } }
            val frame = Unpooled.buffer()
            ByteMessage(frame).writeVarInt(payload.readableBytes())
            frame.writeBytes(payload)
            channel.writeInbound(frame)
            while (true) {
                val f = channel.readOutbound<ByteBuf>() ?: break
                val msg = ByteMessage(f)
                msg.readVarInt() // frame length
                out += msg.readVarInt()
                f.release()
            }
        }

        send(0x00) { writeVarInt(protocol); writeString("localhost"); buf.writeShort(25565); writeVarInt(2) }
        send(0x00) {
            writeString("Tester")
            when {
                version.isAtLeast(Version.MINECRAFT_1_20_2) -> writeUUID(UUID(1, 2))
                version.isAtLeast(Version.MINECRAFT_1_19_3) -> writeBoolean(false)
                version.isAtLeast(Version.MINECRAFT_1_19_1) -> { writeBoolean(false); writeBoolean(false) }
                version.isAtLeast(Version.MINECRAFT_1_19) -> writeBoolean(false)
            }
        }
        if (version.isAtLeast(Version.MINECRAFT_1_20_2)) {
            send(0x03) {} // login acknowledged
            if (version.isAtLeast(Version.MINECRAFT_1_20_5)) {
                // known packs reply: confirm minecraft:core, so registries go out keys-only
                send(0x07) { writeVarInt(1); writeString("minecraft"); writeString("core"); writeString("test") }
            }
            send(if (version.isAtLeast(Version.MINECRAFT_1_20_5)) 0x03 else 0x02) {} // finish configuration ack
        }

        val play = PacketRegistry[ConnectionState.PLAY]
        val joinId = play.idOf(JoinGamePacket(0, 0, "", 0), protocol)
        val positionId = play.idOf(PlayerPositionPacket(0.0, 0.0, 0.0, 0f, 0f, 0, 0), protocol)
        assertEquals(ConnectionState.PLAY, connection.state, "protocol $protocol")
        assertTrue(channel.isActive, "protocol $protocol: channel closed (decode/handler error)")
        assertTrue(joinId in out, "protocol $protocol ($version): no Join Game in $out")
        assertTrue(positionId in out, "protocol $protocol ($version): no spawn position in $out")
        channel.finishAndReleaseAll()
    }

    @Test
    fun `1_9 writes an i8 dimension, 1_9_1 an i32`() {
        fun size(protocol: Int): Int {
            val buf = ByteMessage(Unpooled.buffer())
            JoinGamePacket(1, 0, "minecraft:overworld", 3).encode(buf, Version.MINECRAFT_1_9, protocol)
            return buf.buf.readableBytes()
        }
        // entity(4) + gamemode(1) + dimension + difficulty(1) + max(1) + "default"(8) + reducedDebug(1)
        assertEquals(17, size(107))
        assertEquals(20, size(108))
    }
}
