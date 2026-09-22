package lol.simeon.husk.logging

/** Tiny ANSI helper. Honours the NO_COLOR convention (https://no-color.org). */
object Ansi {
    val enabled: Boolean = System.getenv("NO_COLOR").isNullOrEmpty()

    const val RESET = "[0m"
    const val BOLD = "[1m"
    const val DIM = "[2m"
    const val RED = "[31m"
    const val GREEN = "[32m"
    const val YELLOW = "[33m"
    const val BLUE = "[34m"
    const val MAGENTA = "[35m"
    const val CYAN = "[36m"
    const val GRAY = "[90m"

    fun paint(text: String, vararg codes: String): String =
        if (enabled && codes.isNotEmpty()) codes.joinToString("") + text + RESET else text
}
