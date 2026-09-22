package lol.simeon.husk.protocol.packet

import lol.simeon.husk.protocol.ClientboundPacket
import lol.simeon.husk.protocol.PacketMapping
import lol.simeon.husk.protocol.ServerboundPacket
import lol.simeon.husk.protocol.StateRegistry
import lol.simeon.husk.protocol.clientbound
import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version
import java.util.UUID

class LoginStartPacket(val username: String, val uuid: UUID?) : ServerboundPacket

/** Sent by the client after Login Success to enter the configuration phase (1.20.2+). */
class LoginAcknowledgedPacket : ServerboundPacket

/** Login-phase disconnect. Always a raw JSON chat string, even on modern versions. */
class LoginDisconnectPacket(val reasonJson: String) : ClientboundPacket {
    override fun encode(buf: ByteMessage, version: Version) = buf.writeString(reasonJson)
}

class SetCompressionPacket(val threshold: Int) : ClientboundPacket {
    override fun encode(buf: ByteMessage, version: Version) = buf.writeVarInt(threshold)
}

class LoginSuccessPacket(val uuid: UUID, val username: String) : ClientboundPacket {
    override fun encode(buf: ByteMessage, version: Version) {
        if (version.isAtLeast(Version.MINECRAFT_1_16)) {
            buf.writeUUID(uuid)
        } else {
            buf.writeString(uuid.toString()) // 1.8–1.15 sent the UUID as a string
        }
        buf.writeString(username)
        if (version.isAtLeast(Version.MINECRAFT_1_19)) {
            buf.writeVarInt(0) // property count (empty — offline mode)
        }
        // "strict error handling" boolean existed only 1.20.5 (766) .. 1.21 (767); removed in 1.21.2.
        if (version.isAtLeast(Version.MINECRAFT_1_20_5) && !version.isAtLeast(Version.MINECRAFT_1_21_2)) {
            buf.buf.writeBoolean(false)
        }
        // 26.2 (proto 776) appended a session-id UUID after the game profile. Offline mode has no
        // real session, so reuse the profile UUID — the client doesn't validate it here.
        if (version.isAtLeast(Version.MINECRAFT_26_2)) {
            buf.writeUUID(uuid)
        }
    }
}

object LoginPackets {
    fun register(reg: StateRegistry) {
        // clientbound login ids are stable across the range
        reg.clientbound<LoginDisconnectPacket>("disconnect", PacketMapping.of(Version.MINECRAFT_1_8 to 0x00))
        reg.clientbound<LoginSuccessPacket>("success", PacketMapping.of(Version.MINECRAFT_1_8 to 0x02))
        reg.clientbound<SetCompressionPacket>("compress", PacketMapping.of(Version.MINECRAFT_1_8 to 0x03))

        reg.serverbound("login_start", PacketMapping.of(Version.MINECRAFT_1_8 to 0x00)) { buf, version ->
            val name = buf.readString(16)
            val uuid = when {
                version.isAtLeast(Version.MINECRAFT_1_20_2) -> buf.readUUID()
                version.isAtLeast(Version.MINECRAFT_1_19_3) -> buf.readOptional { buf.readUUID() }
                version.isAtLeast(Version.MINECRAFT_1_19) -> {
                    // 1.19–1.19.2: optional chat-signing key (timestamp, public key, signature) — skipped.
                    if (buf.readBoolean()) {
                        buf.buf.skipBytes(Long.SIZE_BYTES)
                        repeat(2) { buf.buf.skipBytes(buf.readVarInt()) }
                    }
                    if (version.isAtLeast(Version.MINECRAFT_1_19_1)) buf.readOptional { buf.readUUID() } else null
                }
                else -> null // pre-1.19: name only
            }
            LoginStartPacket(name, uuid)
        }
        reg.serverbound("login_acknowledged", PacketMapping.of(Version.MINECRAFT_1_20_2 to 0x03)) { _, _ ->
            LoginAcknowledgedPacket()
        }
    }
}
