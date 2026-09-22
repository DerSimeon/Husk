package lol.simeon.husk.session

/** Protocol phases a connection moves through. Packet ids are namespaced per phase. */
enum class ConnectionState {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION, // 1.20.2+
    PLAY,
}
