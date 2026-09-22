package lol.simeon.husk.api.event

/** Marker for anything publishable on the [EventBus]. */
interface Event

/** Minimal synchronous, typed event bus. */
interface EventBus {
    fun <T : Event> subscribe(type: Class<T>, handler: (T) -> Unit)
    fun publish(event: Event)
}

/** Reified sugar: `events.subscribe<PlayerJoinEvent> { ... }`. */
inline fun <reified T : Event> EventBus.subscribe(noinline handler: (T) -> Unit) =
    subscribe(T::class.java, handler)
