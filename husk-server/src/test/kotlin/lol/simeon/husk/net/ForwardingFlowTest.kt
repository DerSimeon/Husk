package lol.simeon.husk.net

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import lol.simeon.husk.config.ForwardingConfig
import lol.simeon.husk.config.HuskConfig
import lol.simeon.husk.config.PingConfig
import lol.simeon.husk.protocol.RegistryStore
import lol.simeon.husk.session.Connection
import lol.simeon.mycelium.ByteMessage
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Login through the real pipeline with Velocity modern forwarding enabled (1.21.8, protocol 772). */
class ForwardingFlowTest {
    private val secret = "s3cret"
    private val forwardedUuid = UUID(7, 8)
    private val config = HuskConfig(
        host = "0.0.0.0", port = 25565, maxPlayers = 20, rootsDir = "roots", brand = "Husk",
        dimension = "minecraft:overworld", playerList = true,
        ping = PingConfig(versionName = "Husk", protocol = -1, description = "test"),
        forwarding = ForwardingConfig(velocityModern = true, secret = secret),
    )

    private fun channel(): EmbeddedChannel {
        val connection = Connection()
        return EmbeddedChannel(
            VarIntFrameDecoder(), VarIntFrameEncoder(),
            PacketDecoder(connection), PacketEncoder(connection),
            ConnectionHandler(connection, config, RegistryStore()),
        )
    }

    private fun EmbeddedChannel.send(id: Int, body: ByteMessage.() -> Unit) {
        val payload = Unpooled.buffer().also { ByteMessage(it).apply { writeVarInt(id); body() } }
        val frame = Unpooled.buffer()
        ByteMessage(frame).writeVarInt(payload.readableBytes())
        frame.writeBytes(payload)
        writeInbound(frame)
    }

    /** Outbound packet ids and, for each, the body after the id. */
    private fun EmbeddedChannel.drain(): List<Pair<Int, ByteMessage>> {
        val out = mutableListOf<Pair<Int, ByteMessage>>()
        while (true) {
            val f = readOutbound<ByteBuf>() ?: break
            val msg = ByteMessage(f)
            msg.readVarInt() // frame length
            out += msg.readVarInt() to msg
        }
        return out
    }

    private fun handshakeAndLogin(ch: EmbeddedChannel) {
        ch.send(0x00) { writeVarInt(772); writeString("localhost"); buf.writeShort(25565); writeVarInt(2) }
        ch.send(0x00) { writeString("Spoofed"); writeUUID(UUID(1, 1)) }
    }

    private fun forwardingResponse(key: String): ByteArray {
        val body = ByteMessage(Unpooled.buffer()).apply {
            writeVarInt(1); writeString("203.0.113.9"); writeUUID(forwardedUuid); writeString("Real"); writeVarInt(0)
        }.let { ByteArray(it.buf.readableBytes()).also { b -> it.buf.readBytes(b) } }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
        return mac.doFinal(body) + body
    }

    @Test
    fun `asks the proxy for forwarding data, then logs in as the forwarded player`() {
        val ch = channel()
        handshakeAndLogin(ch)
        val request = ch.drain().single()
        assertEquals(0x04, request.first)
        assertEquals(1, request.second.readVarInt()) // message id
        assertEquals("velocity:player_info", request.second.readString(Short.MAX_VALUE.toInt()))

        ch.send(0x02) { writeVarInt(1); writeBoolean(true); buf.writeBytes(forwardingResponse(secret)) }
        val success = ch.drain().first()
        assertEquals(0x02, success.first)
        assertEquals(forwardedUuid, success.second.readUUID())
        assertEquals("Real", success.second.readString(16))
        ch.finishAndReleaseAll()
    }

    @Test
    fun `kicks a connection whose forwarding data is signed with the wrong secret`() {
        val ch = channel()
        handshakeAndLogin(ch)
        ch.drain()
        ch.send(0x02) { writeVarInt(1); writeBoolean(true); buf.writeBytes(forwardingResponse("wrong")) }
        assertEquals(0x00, ch.drain().first().first) // login disconnect
        assertFalse(ch.isActive)
        ch.finishAndReleaseAll()
    }

    @Test
    fun `kicks a client that has no forwarding to offer`() {
        val ch = channel()
        handshakeAndLogin(ch)
        ch.drain()
        ch.send(0x02) { writeVarInt(1); writeBoolean(false) }
        assertEquals(0x00, ch.drain().first().first)
        ch.finishAndReleaseAll()
    }
}
