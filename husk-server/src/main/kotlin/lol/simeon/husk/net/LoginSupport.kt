package lol.simeon.husk.net

import io.netty.channel.ChannelHandlerContext
import lol.simeon.husk.protocol.packet.LoginDisconnectPacket
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.util.UUID

/** Offline-mode UUID: name-based, matching the vanilla `OfflinePlayer:<name>` scheme. */
internal fun offlineUuid(name: String): UUID =
    UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

/** Kicks the player during login with a plain-text reason, then closes the connection. */
internal fun ChannelHandlerContext.disconnectLogin(message: String) {
    val json = GsonComponentSerializer.gson().serialize(Component.text(message))
    writeAndFlush(LoginDisconnectPacket(json)).addListener { close() }
}
