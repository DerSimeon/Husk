package lol.simeon.husk.example

import lol.simeon.husk.api.Root
import lol.simeon.husk.api.RootContext

/** Proves the API + loader end-to-end: logs on enable/disable. */
class ExampleRoot : Root {
    override fun onEnable(context: RootContext) {
        context.logger.info("ExampleRoot enabled — hello from a root! server=${context.server.version}")
    }

    override fun onDisable() {
        // nothing to clean up
    }
}
