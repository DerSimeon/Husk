package lol.simeon.husk.root

import lol.simeon.husk.api.HuskServer
import lol.simeon.husk.api.RootContext
import lol.simeon.husk.api.RootDescriptor
import lol.simeon.husk.api.event.EventBus
import lol.simeon.husk.api.scheduler.Scheduler
import lol.simeon.husk.logging.Log
import java.util.logging.Logger

private class RootContextImpl(
    override val server: HuskServer,
    override val logger: Logger,
    override val events: EventBus,
    override val scheduler: Scheduler,
    override val descriptor: RootDescriptor,
) : RootContext

/** Drives root lifecycle: enable on boot, disable in reverse order on shutdown. */
class RootManager(
    private val server: HuskServer,
    private val events: EventBus,
    private val scheduler: Scheduler,
) {
    private val enabled = mutableListOf<LoadedRoot>()

    fun enableAll(roots: List<LoadedRoot>) {
        for (loaded in roots) {
            try {
                val context = RootContextImpl(
                    server,
                    Logger.getLogger(loaded.descriptor.name),
                    events,
                    scheduler,
                    loaded.descriptor,
                )
                loaded.root.onEnable(context)
                enabled += loaded
                Log.success("Enabled root ${loaded.descriptor.name} v${loaded.descriptor.version}")
            } catch (e: Exception) {
                Log.error("Failed to enable root ${loaded.descriptor.name}: ${e.message}")
            }
        }
    }

    fun disableAll() {
        for (loaded in enabled.asReversed()) {
            try {
                loaded.root.onDisable()
                loaded.classLoader.close()
            } catch (e: Exception) {
                Log.error("Error disabling root ${loaded.descriptor.name}: ${e.message}")
            }
        }
        enabled.clear()
    }
}
