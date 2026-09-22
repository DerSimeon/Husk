import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.detekt")

    group = "lol.simeon"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        // Pre-existing findings; CI fails only on new ones. Regenerate with ./gradlew detektBaseline.
        baseline = file("detekt-baseline.xml")
    }
}
