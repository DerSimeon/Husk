package lol.simeon.husk

import lol.simeon.husk.logging.Ansi

private val ART = """
 ██╗  ██╗██╗   ██╗███████╗██╗  ██╗
 ██║  ██║██║   ██║██╔════╝██║ ██╔╝
 ███████║██║   ██║███████╗█████╔╝
 ██╔══██║██║   ██║╚════██║██╔═██╗
 ██║  ██║╚██████╔╝███████║██║  ██╗
 ╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝
""".trimIndent()

/** Prints the startup banner: ASCII art + version + supported range. */
object Banner {
    fun print(version: String, mcRange: String) {
        println()
        ART.lines().forEach { println(Ansi.paint(it, Ansi.MAGENTA, Ansi.BOLD)) }
        println(
            "  " + Ansi.paint("Husk Limbo", Ansi.CYAN, Ansi.BOLD) +
                Ansi.paint("  v$version", Ansi.GRAY),
        )
        println("  " + Ansi.paint("Minecraft $mcRange", Ansi.DIM))
        println()
    }
}
