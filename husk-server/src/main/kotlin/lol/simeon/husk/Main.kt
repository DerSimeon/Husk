package lol.simeon.husk

import lol.simeon.husk.config.HuskConfig
import lol.simeon.husk.event.EventBusImpl
import lol.simeon.husk.logging.HuskLogging
import lol.simeon.husk.logging.Log
import lol.simeon.husk.net.NettyServer
import lol.simeon.husk.root.RootLoader
import lol.simeon.husk.root.RootManager
import lol.simeon.husk.scheduler.SchedulerImpl
import java.nio.file.Path
import kotlin.system.exitProcess

private const val VERSION = "1.0-SNAPSHOT"
private const val MC_RANGE = "1.8 – 26.3"

fun main() {
    val startedAt = System.nanoTime()

    HuskLogging.install()
    Banner.print(VERSION, MC_RANGE)
    logSystemInfo()
    Log.debug { "Debug logging enabled (-Dhusk.debug / HUSK_DEBUG)" }

    val config = HuskConfig.load()
    Log.success("Config loaded (port ${config.port}, max ${config.maxPlayers})")

    val events = EventBusImpl()
    val scheduler = SchedulerImpl()
    val server = HuskServerImpl(VERSION)

    val net = NettyServer(config)
    try {
        net.start()
    } catch (e: Exception) {
        Log.error("Could not bind ${config.host}:${config.port} — ${e.message}. Is another server already running?")
        net.stop()
        scheduler.close()
        exitProcess(1)
    }

    val loader = RootLoader(Path.of(config.rootsDir), HuskServerImpl::class.java.classLoader)
    val roots = RootManager(server, events, scheduler)
    val loaded = loader.load()
    roots.enableAll(loaded)
    Log.success("Loaded ${loaded.size} root(s)")

    Runtime.getRuntime().addShutdownHook(Thread {
        Log.info("Shutting down")
        roots.disableAll()
        net.stop()
        scheduler.close()
    })

    val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
    Log.success(String.format(java.util.Locale.ROOT, "Done (%.3fs)! For help, type \"help\"", seconds))
}

private fun logSystemInfo() {
    val runtime = Runtime.getRuntime()
    val maxHeapMiB = runtime.maxMemory() / (1024 * 1024)
    Log.info("Java ${System.getProperty("java.version")} (${System.getProperty("java.vm.name")})")
    Log.info(
        "${System.getProperty("os.name")} ${System.getProperty("os.arch")} · " +
            "${runtime.availableProcessors()} cores · $maxHeapMiB MiB max heap",
    )
}
