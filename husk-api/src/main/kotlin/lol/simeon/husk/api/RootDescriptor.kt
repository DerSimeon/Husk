package lol.simeon.husk.api

/** Metadata read from a root jar's manifest (`Husk-Root-*` attributes). */
data class RootDescriptor(
    val name: String,
    val mainClass: String,
    val version: String,
    val apiVersion: String?,
)
