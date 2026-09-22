package lol.simeon.husk.api.scheduler

import java.time.Duration

/** Off-thread task scheduling for roots. */
interface Scheduler {
    fun run(task: () -> Unit)
    fun runLater(delay: Duration, task: () -> Unit)

    /** @return handle; [AutoCloseable.close] cancels the repeating task. */
    fun runRepeating(interval: Duration, task: () -> Unit): AutoCloseable
}
