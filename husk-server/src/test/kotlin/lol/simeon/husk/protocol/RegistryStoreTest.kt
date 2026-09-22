package lol.simeon.husk.protocol

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegistryStoreTest {
    private val store = RegistryStore()

    @Test
    fun `proto 776 provides damage_type registry and its is_fire tag`() {
        val prepared = assertNotNull(store.forProtocol(776), "776 should resolve to a capture")

        val damageType = prepared.registryData.first { it.registryId == "minecraft:damage_type" }
        assertTrue(damageType.entries.isNotEmpty(), "damage_type must have entries")

        val damageTags = prepared.tags.registries.first { it.registryId == "minecraft:damage_type" }
        val isFire = damageTags.tags.first { it.name == "minecraft:is_fire" }
        assertTrue(isFire.entryIds.isNotEmpty(), "is_fire tag must reference damage types")
    }

    @Test
    fun `in-between protocol resolves to nearest lower capture`() {
        // No capture for a made-up higher protocol -> falls back to the newest (776).
        assertNotNull(store.forProtocol(99999))
    }
}
