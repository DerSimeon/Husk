plugins {
    alias(libs.plugins.shadow)
    application
}

dependencies {
    implementation(project(":husk-api")) // transitively exposes mycelium + adventure-api
    implementation(libs.netty.all)       // full netty (mycelium ships only netty-buffer)
    implementation(libs.typesafe.config) // HOCON config parsing
    implementation(libs.adventure.minimessage) // MiniMessage MOTD parsing
    implementation(libs.adventure.gson)         // Component -> status JSON
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("lol.simeon.husk.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
