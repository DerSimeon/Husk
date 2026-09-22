package lol.simeon.husk.root

import lol.simeon.husk.api.Root
import lol.simeon.husk.api.RootDescriptor
import lol.simeon.husk.logging.Log
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.name

/** A root instantiated from a jar, with its descriptor and owning classloader. */
data class LoadedRoot(
    val root: Root,
    val descriptor: RootDescriptor,
    val classLoader: URLClassLoader,
)

/**
 * Scans [rootsDir] for `*.jar`, reads each jar's `Husk-Root-*` manifest attributes,
 * and instantiates the `Husk-Root-Main` class.
 *
 * ponytail: each root gets its own URLClassLoader, but with [parent] = the server
 * loader, so roots see server internals too. Tighten to an api-only parent if
 * isolation actually matters.
 */
class RootLoader(private val rootsDir: Path, private val parent: ClassLoader) {

    fun load(): List<LoadedRoot> {
        if (!Files.isDirectory(rootsDir)) {
            Files.createDirectories(rootsDir)
            return emptyList()
        }
        Files.list(rootsDir).use { stream ->
            return stream
                .filter { it.name.endsWith(".jar") }
                .map(::loadJar)
                .filter { it != null }
                .map { it!! }
                .toList()
        }
    }

    private fun loadJar(jar: Path): LoadedRoot? = try {
        val descriptor = readDescriptor(jar)
        if (descriptor == null) {
            Log.warn("Skipping $jar: no Husk-Root-Main manifest attribute")
            null
        } else {
            val loader = URLClassLoader(arrayOf(jar.toUri().toURL()), parent)
            val instance = loader.loadClass(descriptor.mainClass)
                .getDeclaredConstructor()
                .newInstance() as Root
            LoadedRoot(instance, descriptor, loader)
        }
    } catch (e: Exception) {
        Log.error("Failed to load root $jar: ${e.message}")
        null
    }

    private fun readDescriptor(jar: Path): RootDescriptor? = JarFile(jar.toFile()).use { jarFile ->
        val attrs = jarFile.manifest?.mainAttributes ?: return null
        val main = attrs.getValue("Husk-Root-Main") ?: return null
        RootDescriptor(
            name = attrs.getValue("Husk-Root-Name") ?: jar.name,
            mainClass = main,
            version = attrs.getValue("Husk-Root-Version") ?: "unknown",
            apiVersion = attrs.getValue("Husk-Api-Version"),
        )
    }
}
