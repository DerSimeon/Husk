package lol.simeon.husk.logging

import java.util.logging.Level
import java.util.logging.Logger

/** Convenience over JUL with ✓ / ✗ / ⚠ status symbols. */
object Log {
    private val logger: Logger = Logger.getLogger("Husk")
    private val DEBUG_TRUE = setOf("1", "true", "yes", "on")

    /** Debug output is off unless `-Dhusk.debug=true` (or env `HUSK_DEBUG=true`) is set. */
    val debugEnabled: Boolean =
        (System.getProperty("husk.debug") ?: System.getenv("HUSK_DEBUG"))?.lowercase() in DEBUG_TRUE

    fun info(message: String) = logger.info(message)

    fun debug(message: String) {
        if (!debugEnabled) return
        logger.fine(Ansi.paint("»", Ansi.GRAY, Ansi.BOLD) + " " + message)
    }

    /** Lazy form: the message is built only when debug is enabled. */
    inline fun debug(lazyMessage: () -> String) {
        if (debugEnabled) debug(lazyMessage())
    }

    fun success(message: String) =
        logger.info(Ansi.paint("✓", Ansi.GREEN, Ansi.BOLD) + " " + message)

    fun warn(message: String) =
        logger.warning(Ansi.paint("⚠", Ansi.YELLOW, Ansi.BOLD) + " " + message)

    fun error(message: String, throwable: Throwable? = null) {
        val text = Ansi.paint("✗", Ansi.RED, Ansi.BOLD) + " " + message
        if (throwable == null) logger.severe(text) else logger.log(Level.SEVERE, text, throwable)
    }
}
