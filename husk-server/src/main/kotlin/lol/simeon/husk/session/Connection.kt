package lol.simeon.husk.session

import lol.simeon.mycelium.Version

/**
 * Per-connection protocol state. The decoder reads [state] (and [protocolVersion]) to pick the
 * right registry entry, so both must be kept current as the connection advances:
 *  - set [protocolVersion] from the Handshake packet
 *  - move [state] HANDSHAKE → STATUS/LOGIN → CONFIGURATION → PLAY as each phase completes
 *
 * A channel's handlers all run on its single event-loop thread, but [protocolVersion]/[state] may
 * be read from other threads (e.g. a scheduler sending packets), so both are @Volatile.
 */
class Connection {
    @Volatile
    var state: ConnectionState = ConnectionState.HANDSHAKE

    // Baseline until the handshake sets the real version. Handshake/status ids are
    // version-invariant, so this is a safe lookup value for the first packet.
    /** The client's exact protocol number. Packet ids resolve against this (finer than [Version]). */
    @Volatile
    var protocol: Int = Version.MINECRAFT_1_8.protocol
        set(value) {
            field = value
            protocolVersion = resolveVersion(value)
        }

    /** [protocol] snapped to the nearest [Version] at or below it; encoders branch on this. */
    @Volatile
    var protocolVersion: Version = Version.MINECRAFT_1_8
        private set
}

/** Highest known [Version] with a protocol ≤ [protocol]; falls back to 1.8. */
fun resolveVersion(protocol: Int): Version =
    Version.entries.filter { it.protocol in 0..protocol }.maxByOrNull { it.protocol } ?: Version.MINECRAFT_1_8
