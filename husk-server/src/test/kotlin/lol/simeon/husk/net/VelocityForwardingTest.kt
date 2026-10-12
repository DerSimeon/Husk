package lol.simeon.husk.net

import io.netty.buffer.Unpooled
import lol.simeon.mycelium.ByteMessage
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VelocityForwardingTest {
    private val uuid = UUID(0x1122334455667788, 0x99aabbccddeeff00uL.toLong())

    private fun payload(name: String): ByteArray {
        val buf = ByteMessage(Unpooled.buffer())
        buf.writeVarInt(1)
        buf.writeString("203.0.113.9")
        buf.writeUUID(uuid)
        buf.writeString(name)
        buf.writeVarInt(0) // no properties
        return ByteArray(buf.buf.readableBytes()).also { buf.buf.readBytes(it) }
    }

    private fun signed(secret: String, body: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        return mac.doFinal(body) + body
    }

    @Test
    fun `accepts data signed with the shared secret and keeps the forwarded identity`() {
        val profile = assertNotNull(VelocityForwarding.verify("s3cret", signed("s3cret", payload("Tester"))))
        assertEquals(uuid, profile.uuid)
        assertEquals("Tester", profile.username)
    }

    @Test
    fun `rejects a wrong secret, tampered data and truncated data`() {
        assertNull(VelocityForwarding.verify("other", signed("s3cret", payload("Tester"))))
        val tampered = signed("s3cret", payload("Tester")).also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        assertNull(VelocityForwarding.verify("s3cret", tampered))
        assertNull(VelocityForwarding.verify("s3cret", ByteArray(10)))
    }
}
