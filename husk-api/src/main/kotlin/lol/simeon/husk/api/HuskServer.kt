package lol.simeon.husk.api

import lol.simeon.husk.api.player.Player
import net.kyori.adventure.text.Component

/** The running server, as roots see it. */
interface HuskServer {
    /** Server version string. */
    val version: String

    /** Currently connected players. */
    fun players(): Collection<Player>

    /** Send a chat message to every connected player. */
    fun broadcast(message: Component)
}
