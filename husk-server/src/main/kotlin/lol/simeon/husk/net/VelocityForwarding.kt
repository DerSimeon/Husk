package lol.simeon.husk.net

import io.netty.buffer.Unpooled
import lol.simeon.mycelium.ByteMessage
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The player Velocity vouches for in its modern-forwarding response. */
class ForwardedProfile(val uuid: UUID, val username: String)

/**
 * Velocity "modern" player-info forwarding: the server sends a login plugin request on
 * [CHANNEL]; the proxy answers with `HMAC-SHA256(secret, payload) ++ payload`, where the payload
 * carries the real address, UUID, name and profile properties. Only the signed payload is trusted.
 */
object VelocityForwarding {
    const val CHANNEL = "velocity:player_info"

    /** Forwarding version asked for: 1 = the original format (address, UUID, name, properties). */
    const val REQUEST_VERSION: Byte = 1

    private const val SIGNATURE_LENGTH = 32
    private const val HMAC = "HmacSHA256"

    /** Returns the forwarded profile, or null if the signature is wrong or the payload is malformed. */
    fun verify(secret: String, data: ByteArray): ForwardedProfile? {
        val body = if (data.size > SIGNATURE_LENGTH) data.copyOfRange(SIGNATURE_LENGTH, data.size) else return null
        val mac = Mac.getInstance(HMAC).apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC)) }
        val signed = MessageDigest.isEqual(mac.doFinal(body), data.copyOfRange(0, SIGNATURE_LENGTH))
        return if (signed) runCatching { parse(body) }.getOrNull() else null
    }

    private fun parse(body: ByteArray): ForwardedProfile {
        val buf = ByteMessage(Unpooled.wrappedBuffer(body))
        buf.readVarInt() // forwarding version
        buf.readString(Short.MAX_VALUE.toInt()) // client address
        val uuid = buf.readUUID()
        val name = buf.readString(16)
        return ForwardedProfile(uuid, name)
    }
}
