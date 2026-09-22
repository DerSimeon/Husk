plugins {
    `java-library`
}

dependencies {
    // Version, NamespacedKey, ByteMessage (protocol data types)
    api(libs.mycelium)
    // Component, Audience, BossBar, Title — roots use the standard Adventure API
    api(libs.adventure.api)
}
