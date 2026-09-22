package lol.simeon.husk.net

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollIoHandler
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueIoHandler
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import lol.simeon.husk.config.HuskConfig
import lol.simeon.husk.logging.Log
import lol.simeon.husk.protocol.RegistryStore
import lol.simeon.husk.session.Connection
import java.util.concurrent.TimeUnit

/** Owns the Netty accept loop. Picks the fastest available transport. */
class NettyServer(private val config: HuskConfig) {
    private val boss: EventLoopGroup
    private val worker: EventLoopGroup
    private val serverChannel: Class<out ServerChannel>
    val transport: String

    init {
        when {
            Epoll.isAvailable() -> {
                boss = MultiThreadIoEventLoopGroup(1, EpollIoHandler.newFactory());
                worker = MultiThreadIoEventLoopGroup(1, EpollIoHandler.newFactory())
                serverChannel = EpollServerSocketChannel::class.java; transport = "epoll"
            }

            KQueue.isAvailable() -> {
                boss = MultiThreadIoEventLoopGroup(1, KQueueIoHandler.newFactory());
                worker = MultiThreadIoEventLoopGroup(1, KQueueIoHandler.newFactory())
                serverChannel = KQueueServerSocketChannel::class.java; transport = "kqueue"
            }

            else -> {
                boss = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
                worker = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
                serverChannel = NioServerSocketChannel::class.java; transport = "nio"
            }
        }
    }

    private var channel: Channel? = null
    private val registries = RegistryStore()

    fun start() {
        val bootstrap = ServerBootstrap().group(boss, worker).channel(serverChannel)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val connection = Connection()
                    val pipeline = ch.pipeline()


                    pipeline.addLast("timeout", ReadTimeoutHandler(config.readTimeout, TimeUnit.MILLISECONDS))
                    pipeline.addLast("frame_decoder", VarIntFrameDecoder())
                    pipeline.addLast("frame_encoder", VarIntFrameEncoder())

                    pipeline.addLast("packet_decoder", PacketDecoder(connection))
                    pipeline.addLast("packet_encoder", PacketEncoder(connection))
                    pipeline.addLast("handler", ConnectionHandler(connection, config, registries))
                    // TODO: compression stages once Set Compression is sent.
                }
            })

        channel = bootstrap.bind(config.host, config.port).sync().channel()
        Log.success("Listening on ${config.host}:${config.port} ($transport transport)")
    }

    fun stop() {
        channel?.close()?.sync()
        boss.shutdownGracefully()
        worker.shutdownGracefully()
    }
}
