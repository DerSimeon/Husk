package lol.simeon.husk

import lol.simeon.husk.api.HuskServer
import lol.simeon.husk.api.player.Player
import net.kyori.adventure.text.Component
import java.util.concurrent.CopyOnWriteArrayList

/** Default [HuskServer]. The session layer registers/unregisters players as they connect. */
class HuskServerImpl(override val version: String) : HuskServer {
    private val onlinePlayers = CopyOnWriteArrayList<Player>()

    override fun players(): Collection<Player> = onlinePlayers

    override fun broadcast(message: Component) = onlinePlayers.forEach { it.sendMessage(message) }

    fun register(player: Player) = onlinePlayers.add(player).let {}
    fun unregister(player: Player) = onlinePlayers.remove(player).let {}
}
