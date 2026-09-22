package lol.simeon.husk.net

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import lol.simeon.husk.protocol.packet.LoginSuccessPacket
import lol.simeon.husk.session.Connection
import lol.simeon.husk.session.ConnectionState
import lol.simeon.mycelium.Version
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginSuccessEncodeTest {
    private val uuid = UUID.fromString("df2f0771-a360-489b-a704-508d8ab72b86")

    /** Encodes Login Success through the full outbound path and returns the framed bytes. */
    private fun frame(version: Version): ByteArray {
        val connection = Connection().apply {
            state = ConnectionState.LOGIN
            protocol = version.protocol
        }
        val channel = EmbeddedChannel(VarIntFrameEncoder(), PacketEncoder(connection))
        channel.writeOutbound(LoginSuccessPacket(uuid, "JustSimeon"))
        val out = channel.readOutbound<ByteBuf>()
        return ByteArray(out.readableBytes()).also { out.readBytes(it); channel.finishAndReleaseAll() }
    }

    @Test
    fun `26_2 appends a session-id UUID (16 bytes) that 26_1 omits`() {
        // len(1) + id(1) + uuid(16) + strlen(1) + "JustSimeon"(10) + props(1) = 30
        assertEquals(30, frame(Version.MINECRAFT_26_1).size)
        // 26.2 adds the trailing session-id UUID: +16 bytes.
        assertEquals(46, frame(Version.MINECRAFT_26_2).size)
    }
}
