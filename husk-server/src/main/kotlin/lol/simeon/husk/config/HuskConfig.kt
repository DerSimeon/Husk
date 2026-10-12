package lol.simeon.husk.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import lol.simeon.husk.logging.Log
import java.nio.file.Files
import java.nio.file.Path

/** Server-list ping settings. [protocol] of -1 echoes the client's own protocol. */
data class PingConfig(
    val versionName: String,
    val protocol: Int,
    val description: String,
)

/**
 * Player-info forwarding. [velocityModern] makes Husk complete Velocity's modern forwarding
 * handshake with [secret] and take the player's name and UUID from it, instead of trusting
 * the Login Start packet. Required when the proxy runs `player-info-forwarding-mode = "modern"`.
 */
data class ForwardingConfig(
    val velocityModern: Boolean,
    val secret: String,
)

/** Server configuration, loaded from `husk.conf` (HOCON) with classpath defaults as fallback. */
data class HuskConfig(
    val host: String,
    val port: Int,
    val maxPlayers: Int,
    val rootsDir: String,
    val brand: String,
    val dimension: String,
    val playerList: Boolean,
    val ping: PingConfig,
    val forwarding: ForwardingConfig = ForwardingConfig(velocityModern = false, secret = ""),
    val readTimeout: Long = 30000L,
) {
    companion object {
        private const val FILE = "husk.conf"

        /** Loads config from cwd, generating the default file on first run. */
        fun load(): HuskConfig {
            val file = Path.of(FILE)
            if (Files.notExists(file)) {
                javaClass.getResourceAsStream("/$FILE")?.use { Files.copy(it, file) }
                Log.success("Generated default $FILE")
            }
            val merged = ConfigFactory.parseFile(file.toFile())
                .withFallback(ConfigFactory.parseResources(FILE))
                .resolve()
            return from(merged.getConfig("husk"))
        }

        private fun from(c: Config) = HuskConfig(
            host = c.getString("host"),
            port = c.getInt("port"),
            maxPlayers = c.getInt("max-players"),
            rootsDir = c.getString("roots-dir"),
            brand = c.getString("brand"),
            dimension = c.getString("dimension"),
            playerList = c.getBoolean("player-list"),
            readTimeout = c.getLong("read-timeout"),
            forwarding = c.getConfig("forwarding").let {
                val mode = it.getString("mode").lowercase()
                require(mode == "none" || mode == "velocity") { "forwarding.mode must be none or velocity: '$mode'" }
                val secret = it.getString("secret")
                require(mode == "none" || secret.isNotBlank()) { "forwarding.mode = velocity needs forwarding.secret" }
                ForwardingConfig(velocityModern = mode == "velocity", secret = secret)
            },
            ping = c.getConfig("ping").let {
                PingConfig(
                    versionName = it.getString("version-name"),
                    protocol = it.getInt("protocol"),
                    description = it.getString("description"),
                )
            },
        )
    }
}
