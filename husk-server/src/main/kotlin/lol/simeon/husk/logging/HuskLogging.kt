package lol.simeon.husk.logging

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.StreamHandler

/** Installs the Husk console formatter on the JUL root logger. Call once, first thing in main. */
object HuskLogging {
    fun install(level: Level = Level.INFO) {
        // Force UTF-8 stdout so ✓ ✗ ⚠ and the banner render on Windows consoles.
        val out = PrintStream(FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8)
        System.setOut(out)

        val root = Logger.getLogger("")
        root.handlers.forEach(root::removeHandler)

        val handler = object : StreamHandler(out, HuskFormatter()) {
            override fun publish(record: LogRecord) {
                super.publish(record)
                flush() // StreamHandler buffers by default; flush every record
            }
        }
        handler.level = Level.ALL
        root.addHandler(handler)
        root.level = level
        // Enable FINE only on our own logger so Log.debug shows without netty's internal DEBUG flood.
        if (Log.debugEnabled) Logger.getLogger("Husk").level = Level.FINE
    }
}
