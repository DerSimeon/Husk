package lol.simeon.husk.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord

/** `[HH:mm:ss] LEVEL logger  message` with per-level colour. */
class HuskFormatter : Formatter() {
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")

    override fun format(record: LogRecord): String {
        val ts = Ansi.paint(LocalTime.now().format(time), Ansi.GRAY)
        val (label, color) = when (record.level) {
            Level.SEVERE -> "ERROR" to Ansi.RED
            Level.WARNING -> "WARN " to Ansi.YELLOW
            Level.INFO -> "INFO " to Ansi.CYAN
            else -> "DEBUG" to Ansi.GRAY
        }
        val level = Ansi.paint(label, color)
        val source = Ansi.paint(shortName(record.loggerName), Ansi.DIM)

        val builder = StringBuilder()
        builder.append("$ts $level $source  ${formatMessage(record)}\n")
        record.thrown?.let { thrown ->
            val writer = StringWriter()
            thrown.printStackTrace(PrintWriter(writer))
            builder.append(Ansi.paint(writer.toString(), Ansi.RED))
        }
        return builder.toString()
    }

    /** Last dotted segment, e.g. `lol.simeon.husk.net.NettyServer` -> `NettyServer`. */
    private fun shortName(name: String?): String =
        name?.substringAfterLast('.')?.ifEmpty { "Husk" } ?: "Husk"
}
