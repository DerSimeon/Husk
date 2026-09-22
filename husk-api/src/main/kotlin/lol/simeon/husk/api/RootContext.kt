package lol.simeon.husk.api

import lol.simeon.husk.api.event.EventBus
import lol.simeon.husk.api.scheduler.Scheduler
import java.util.logging.Logger

/** Everything a [Root] needs to talk to the server. Handed in on [Root.onEnable]. */
interface RootContext {
    val server: HuskServer
    val logger: Logger
    val events: EventBus
    val scheduler: Scheduler
    val descriptor: RootDescriptor
}
