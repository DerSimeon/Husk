package lol.simeon.husk.protocol

import lol.simeon.husk.protocol.packet.ConfigurationPackets
import lol.simeon.husk.protocol.packet.HandshakePackets
import lol.simeon.husk.protocol.packet.LoginPackets
import lol.simeon.husk.protocol.packet.PlayPackets
import lol.simeon.husk.protocol.packet.StatusPackets
import lol.simeon.husk.session.ConnectionState
import java.util.EnumMap

/** Central registry: one [StateRegistry] per connection state. */
object PacketRegistry {
    private val states = EnumMap<ConnectionState, StateRegistry>(ConnectionState::class.java)

    operator fun get(state: ConnectionState): StateRegistry =
        states[state] ?: error("No packet registry for state $state")

    private fun register(state: ConnectionState, block: StateRegistry.() -> Unit) {
        states[state] = StateRegistry().apply(block)
    }

    init {
        register(ConnectionState.HANDSHAKE) { HandshakePackets.register(this) }
        register(ConnectionState.STATUS) { StatusPackets.register(this) }
        register(ConnectionState.LOGIN) { LoginPackets.register(this) }
        register(ConnectionState.CONFIGURATION) { ConfigurationPackets.register(this) }
        register(ConnectionState.PLAY) { PlayPackets.register(this) }
    }
}
