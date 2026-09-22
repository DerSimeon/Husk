package lol.simeon.husk.api

/**
 * A "root" — a third-party extension loaded from a jar in the server's roots dir.
 *
 * Implementations need a public no-arg constructor and a manifest entry
 * `Husk-Root-Main` naming the class.
 */
interface Root {
    /** Called once after load. Register listeners/tasks via [context] here. */
    fun onEnable(context: RootContext) {}

    /** Called on server shutdown or root unload. Release resources here. */
    fun onDisable() {}
}
