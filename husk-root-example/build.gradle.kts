plugins {
    // no shadow/application — a root is a plain jar dropped into the server's roots/ dir
}

dependencies {
    compileOnly(project(":husk-api")) // provided by the server at runtime
}

tasks.jar {
    manifest {
        attributes(
            "Husk-Root-Main" to "lol.simeon.husk.example.ExampleRoot",
            "Husk-Root-Name" to "example",
            "Husk-Root-Version" to project.version.toString(),
            "Husk-Api-Version" to "1.0",
        )
    }
}
