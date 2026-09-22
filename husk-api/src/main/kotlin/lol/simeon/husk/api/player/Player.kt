package lol.simeon.husk.api.player

import lol.simeon.mycelium.Version
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import java.util.UUID

/**
 * A connected client.
 *
 * Extends Adventure [Audience], so `sendMessage`, `showTitle`, `sendActionBar`,
 * `showBossBar`/`hideBossBar` and `playSound` all come for free — that covers
 * chat, titles/subtitles and boss bars with the API everyone already knows.
 */
interface Player : Audience {
    val uuid: UUID
    val username: String
    val protocolVersion: Version

    fun disconnect(reason: Component)
}
