package lol.simeon.husk.event

import lol.simeon.husk.api.event.Event
import lol.simeon.husk.api.event.EventBus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Synchronous typed bus: handlers fire on the publishing thread, exact-type match. */
class EventBusImpl : EventBus {
    private val handlers = ConcurrentHashMap<Class<*>, MutableList<(Any) -> Unit>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event> subscribe(type: Class<T>, handler: (T) -> Unit) {
        handlers.computeIfAbsent(type) { CopyOnWriteArrayList() }.add(handler as (Any) -> Unit)
    }

    override fun publish(event: Event) {
        handlers[event.javaClass]?.forEach { it(event) }
    }
}
