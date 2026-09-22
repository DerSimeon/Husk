package lol.simeon.husk.scheduler

import lol.simeon.husk.api.scheduler.Scheduler
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Backed by a [java.util.concurrent.ScheduledExecutorService]. */
class SchedulerImpl(threads: Int = 1) : Scheduler, AutoCloseable {
    private val executor = Executors.newScheduledThreadPool(threads)

    override fun run(task: () -> Unit) {
        executor.execute(task)
    }

    override fun runLater(delay: Duration, task: () -> Unit) {
        executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS)
    }

    override fun runRepeating(interval: Duration, task: () -> Unit): AutoCloseable {
        val future = executor.scheduleAtFixedRate(
            task, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS,
        )
        return AutoCloseable { future.cancel(false) }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
