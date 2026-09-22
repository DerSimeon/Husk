import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.shadow) apply false
    // detekt: kept in the catalog but not applied — 1.23.8 doesn't support Kotlin 2.4 / JDK 25.
    // Re-apply once a detekt release supports this toolchain.
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "lol.simeon"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }
}
