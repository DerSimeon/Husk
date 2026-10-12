package lol.simeon.husk.net

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import lol.simeon.husk.config.HuskConfig
import lol.simeon.husk.logging.Log
import lol.simeon.husk.protocol.RegistryStore
import lol.simeon.husk.protocol.ServerboundPacket
import lol.simeon.husk.protocol.packet.CodecRegistryDataPacket
import lol.simeon.husk.protocol.packet.FinishConfigurationAckPacket
import lol.simeon.husk.protocol.packet.FinishConfigurationPacket
import lol.simeon.husk.protocol.packet.EmptyChunkPacket
import lol.simeon.husk.protocol.packet.GameEventPacket
import lol.simeon.husk.protocol.packet.HandshakePacket
import lol.simeon.husk.protocol.packet.JoinGamePacket
import lol.simeon.husk.protocol.packet.KnownPack
import lol.simeon.husk.protocol.packet.KnownPacksResponsePacket
import lol.simeon.husk.protocol.packet.KeepAlivePacket
import lol.simeon.husk.protocol.packet.LoginAcknowledgedPacket
import lol.simeon.husk.protocol.packet.LoginDisconnectPacket
import lol.simeon.husk.protocol.packet.LoginPluginRequestPacket
import lol.simeon.husk.protocol.packet.LoginPluginResponsePacket
import lol.simeon.husk.protocol.packet.LoginStartPacket
import lol.simeon.husk.protocol.packet.LoginSuccessPacket
import lol.simeon.husk.protocol.packet.PingRequestPacket
import lol.simeon.husk.protocol.packet.PlayerAbilitiesPacket
import lol.simeon.husk.protocol.packet.PlayerInfoAddPacket
import lol.simeon.husk.protocol.packet.PlayerPositionPacket
import lol.simeon.husk.protocol.packet.PongResponsePacket
import lol.simeon.husk.protocol.packet.RegistryDataPacket
import lol.simeon.husk.protocol.packet.SelectKnownPacksPacket
import lol.simeon.husk.protocol.packet.ServerKeepAlivePacket
import lol.simeon.husk.protocol.packet.SetCenterChunkPacket
import lol.simeon.husk.protocol.packet.StatusRequestPacket
import lol.simeon.husk.protocol.packet.StatusResponsePacket
import lol.simeon.husk.session.Connection
import lol.simeon.husk.session.ConnectionState
import lol.simeon.mycelium.Version
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Terminal inbound handler: reacts to decoded packets and drives the connection state machine.
 *
 * Handles handshake, the status ping exchange, and login through Login Success. [Connection.state]
 * / [Connection.protocolVersion] are updated here so the decoder/encoder resolve the next packet
 * against the right phase.
 */
class ConnectionHandler(
    private val connection: Connection,
    private val config: HuskConfig,
    private val registries: RegistryStore,
) : SimpleChannelInboundHandler<ServerboundPacket>() {

    override fun channelRead0(ctx: ChannelHandlerContext, packet: ServerboundPacket) {
        when (packet) {
            is HandshakePacket -> {
                connection.protocol = packet.protocolVersion
                connection.state = when (packet.nextState) {
                    STATUS_INTENT -> ConnectionState.STATUS
                    else -> ConnectionState.LOGIN // 2 = login, 3 = transfer (both enter login)
                }
                Log.info(
                    "Handshake from ${ctx.channel().remoteAddress()}: proto ${packet.protocolVersion} " +
                        "(${connection.protocolVersion}) -> ${connection.state}",
                )
            }

            is StatusRequestPacket -> ctx.writeAndFlush(StatusResponsePacket(statusJson()))

            // Echo the payload straight back, then close — the ping is the last status exchange.
            is PingRequestPacket -> ctx.writeAndFlush(PongResponsePacket(packet.payload))
                .addListener { ctx.close() }

            is LoginStartPacket -> onLoginStart(ctx, packet)

            is LoginPluginResponsePacket -> onForwardingResponse(ctx, packet)

            is LoginAcknowledgedPacket -> {
                connection.state = ConnectionState.CONFIGURATION
                Log.info("Login acknowledged -> CONFIGURATION")
                if (connection.protocolVersion.isAtLeast(Version.MINECRAFT_1_20_5)) {
                    // Registry data waits for the client's answer (handled below).
                    ctx.writeAndFlush(SelectKnownPacksPacket(corePacks(connection.protocol)))
                } else {
                    sendConfiguration(ctx, clientHasCore = false)
                }
            }

            is KnownPacksResponsePacket -> {
                val hasCore = packet.packs.any { it.namespace == "minecraft" && it.id == "core" }
                Log.debug { "Client knows minecraft:core: $hasCore" }
                sendConfiguration(ctx, clientHasCore = hasCore)
            }

            is FinishConfigurationAckPacket -> {
                connection.state = ConnectionState.PLAY
                Log.info("Configuration finished -> PLAY")
                enterPlay(ctx)
            }

            is ServerKeepAlivePacket -> Unit // client alive; nothing to do

            else -> Log.info("Received ${packet.javaClass.simpleName} in ${connection.state}")
        }
    }

    /**
     * Sends the captured registry data (+ tags on 1.20.5+) for the client's protocol, then Finish
     * Configuration. The client applies the registries, validates its built-in tag references, and
     * acks to PLAY. 1.20.2–1.20.4 take the whole codec in one packet and don't need tags.
     *
     * [clientHasCore]: the client confirmed our `minecraft:core` pack, so entries go out as keys only
     * and the client fills in its own data — needed when its registry formats differ from the capture.
     */
    private fun sendConfiguration(ctx: ChannelHandlerContext, clientHasCore: Boolean) {
        val prepared = registries.forProtocol(connection.protocol)
        if (prepared == null) {
            Log.warn("No registry capture for protocol ${connection.protocol}; sending empty config")
        } else if (connection.protocolVersion.isAtLeast(Version.MINECRAFT_1_20_5)) {
            prepared.registryData.forEach { registry ->
                ctx.write(
                    if (clientHasCore) {
                        RegistryDataPacket(registry.registryId, registry.entries.map { RegistryDataPacket.Entry(it.key, null) })
                    } else {
                        registry
                    },
                )
            }
            ctx.write(prepared.tags)
        } else {
            ctx.write(CodecRegistryDataPacket(prepared.codec))
        }
        ctx.writeAndFlush(FinishConfigurationPacket())
    }

    private fun onLoginStart(ctx: ChannelHandlerContext, packet: LoginStartPacket) {
        if (!config.forwarding.velocityModern) {
            completeLogin(ctx, packet.uuid ?: offlineUuid(packet.username), packet.username)
        } else if (connection.protocol < VELOCITY_MIN_PROTOCOL) {
            ctx.disconnectLogin("This server requires a 1.13+ client connecting through the proxy.")
        } else {
            // Don't trust the claimed name/UUID: ask the proxy for its signed player info first.
            pendingLoginName = packet.username
            val request = byteArrayOf(VelocityForwarding.REQUEST_VERSION)
            ctx.writeAndFlush(LoginPluginRequestPacket(VELOCITY_MESSAGE_ID, VelocityForwarding.CHANNEL, request))
        }
    }

    private fun onForwardingResponse(ctx: ChannelHandlerContext, packet: LoginPluginResponsePacket) {
        val expected = config.forwarding.velocityModern && packet.messageId == VELOCITY_MESSAGE_ID && packet.successful
        val profile = if (expected) VelocityForwarding.verify(config.forwarding.secret, packet.data) else null
        if (profile == null) {
            val from = ctx.channel().remoteAddress()
            Log.warn("Rejected ${pendingLoginName ?: "?"} from $from: no valid Velocity forwarding data")
            ctx.disconnectLogin("Unable to verify player details. Connect through the proxy.")
        } else {
            completeLogin(ctx, profile.uuid, profile.username)
        }
    }

    private fun completeLogin(ctx: ChannelHandlerContext, uuid: UUID, username: String) {
        playerUuid = uuid
        playerName = username
        Log.info("Login: $username ($uuid) on ${connection.protocolVersion}")
        ctx.writeAndFlush(LoginSuccessPacket(uuid, username))
        // Pre-1.20.2 clients jump straight to PLAY after Login Success; newer ones send
        // Login Acknowledged first (handled above) to enter CONFIGURATION.
        if (!connection.protocolVersion.isAtLeast(Version.MINECRAFT_1_20_2)) {
            connection.state = ConnectionState.PLAY
            enterPlay(ctx)
        }
    }

    private fun enterPlay(ctx: ChannelHandlerContext) {
        val version = connection.protocolVersion
        // null below 1.16 (no registries on the wire); from 1.16 there is always a capture.
        val prepared = registries.forProtocol(connection.protocol)
        val dimensionId = prepared?.dimensionIndex?.get(config.dimension) ?: 0
        val sections = prepared?.dimensionSections?.get(config.dimension) ?: DEFAULT_SECTIONS
        if (version.isAtLeast(Version.MINECRAFT_1_20_5) && prepared != null && config.dimension !in prepared.dimensionIndex) {
            Log.warn("Dimension ${config.dimension} not in registry; defaulting to index 0")
        }

        ctx.write(
            JoinGamePacket(
                entityId = 1, dimensionId = dimensionId, dimensionName = config.dimension,
                gamemode = SPECTATOR, viewDistance = VIEW_DISTANCE, codec = prepared?.codec,
            ),
        )
        // flags: invulnerable | flying | allow-flying; fly speed 0 = spectator can't move.
        ctx.write(PlayerAbilitiesPacket(flags = 0x07, flyingSpeed = 0f, fovModifier = 0.1f))
        if (config.playerList) ctx.write(PlayerInfoAddPacket(playerUuid, playerName, gamemode = SPECTATOR))

        // 1.20.3+ holds the "Loading terrain" screen until the spawn chunk arrives, so send empty
        // chunks around spawn. Older clients get none: pre-1.18 close the screen on the position
        // packet, and 1.18–1.20.2 close it once the player's chunk renders *or* the player is outside
        // the build height — so they spawn above it (a void either way).
        val waitsForChunks = version.isAtLeast(Version.MINECRAFT_1_20_3)
        if (waitsForChunks) {
            ctx.write(SetCenterChunkPacket(0, 0))
            for (cx in -VIEW_DISTANCE..VIEW_DISTANCE) {
                for (cz in -VIEW_DISTANCE..VIEW_DISTANCE) {
                    ctx.write(EmptyChunkPacket(cx, cz, sections, biomeId = 0))
                }
            }
        }

        ctx.write(PlayerPositionPacket(x = 0.0, y = if (waitsForChunks) 64.0 else ABOVE_BUILD_LIMIT, z = 0.0, yaw = 0f, pitch = 0f, flags = 0, teleportId = 1))
        if (waitsForChunks) ctx.write(GameEventPacket(reason = LEVEL_CHUNKS_LOAD_START))
        ctx.flush()
        startKeepAlive(ctx)
    }

    private fun startKeepAlive(ctx: ChannelHandlerContext) {
        // Keep the interval under the read timeout so the client's response resets it before it fires.
        val intervalMs = (config.readTimeout / 2).coerceAtLeast(1000)
        keepAlive = ctx.executor().scheduleAtFixedRate({
            ctx.writeAndFlush(KeepAlivePacket(System.currentTimeMillis()))
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        keepAlive?.cancel(false)
        keepAlive = null
    }

    /**
     * Server-list-ping response. Protocol -1 echoes the client's own so the ping always shows as
     * compatible; the description is parsed as MiniMessage and embedded as a chat component.
     */
    private fun statusJson(): String {
        val protocol = if (config.ping.protocol >= 0) config.ping.protocol else connection.protocol
        val description = GsonComponentSerializer.gson()
            .serialize(MiniMessage.miniMessage().deserialize(config.ping.description))
        return """{"version":{"name":"${escape(config.ping.versionName)}","protocol":$protocol},""" +
            """"players":{"max":${config.maxPlayers},"online":0,"sample":[]},""" +
            """"description":$description}"""
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.error("Connection error from ${ctx.channel().remoteAddress()}: ${cause.message}")
        ctx.close()
    }

    private var keepAlive: ScheduledFuture<*>? = null
    private var playerUuid: UUID = UUID(0, 0)
    private var playerName: String = "player"
    private var pendingLoginName: String? = null

    private companion object {
        /**
         * The `minecraft:core` pack as the client names it: its game version string. Protocols shared
         * by several releases offer each name; the client confirms the one it is. Unlisted protocols
         * get no offer and fall back to full registry data.
         */
        fun corePacks(protocol: Int): List<KnownPack> = CORE_VERSIONS[protocol].orEmpty()
            .map { KnownPack("minecraft", "core", it) }

        val CORE_VERSIONS = mapOf(
            766 to listOf("1.20.5", "1.20.6"),
            767 to listOf("1.21", "1.21.1"),
            768 to listOf("1.21.2", "1.21.3"),
            769 to listOf("1.21.4"),
            770 to listOf("1.21.5"),
            771 to listOf("1.21.6"),
            772 to listOf("1.21.7", "1.21.8"),
            773 to listOf("1.21.9", "1.21.10"),
            774 to listOf("1.21.11"),
            775 to listOf("26.1", "26.1.1", "26.1.2"),
            776 to listOf("26.2"),
            777 to listOf("26.3"),
        )

        const val VELOCITY_MESSAGE_ID = 1
        const val VELOCITY_MIN_PROTOCOL = 393 // login plugin messages exist from 1.13
        const val STATUS_INTENT = 1
        const val SPECTATOR = 3
        const val LEVEL_CHUNKS_LOAD_START = 13
        const val VIEW_DISTANCE = 2 // send a (2r+1)² square of empty chunks around spawn
        const val DEFAULT_SECTIONS = 16 // fallback dimension height (256) / 16
        const val ABOVE_BUILD_LIMIT = 1024.0 // above every vanilla dimension's max build height
    }
}
