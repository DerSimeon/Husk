plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Husk"

include(":husk-api", ":husk-server", ":husk-root-example")
