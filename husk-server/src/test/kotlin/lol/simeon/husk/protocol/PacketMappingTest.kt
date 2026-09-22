package lol.simeon.husk.protocol

import io.netty.buffer.Unpooled
import lol.simeon.husk.protocol.packet.BossBarPacket
import lol.simeon.husk.protocol.packet.HandshakePacket
import lol.simeon.husk.session.ConnectionState
import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PacketMappingTest {
    @Test
    fun `idFor resolves to the highest listed version at or below the target`() {
        val mapping = PacketMapping.of(
            Version.MINECRAFT_1_8 to 0x21,
            Version.MINECRAFT_1_9 to 0x20,
            Version.MINECRAFT_1_20 to 0x25,
        )
        assertEquals(0x21, mapping.idFor(Version.MINECRAFT_1_8))
        assertEquals(0x20, mapping.idFor(Version.MINECRAFT_1_9))
        assertEquals(0x20, mapping.idFor(Version.MINECRAFT_1_16)) // between 1.9 and 1.20
        assertEquals(0x25, mapping.idFor(Version.MINECRAFT_26_3))
    }

    @Test
    fun `idFor is null before a packet's first version`() {
        val mapping = PacketMapping.of(Version.MINECRAFT_1_20_2 to 0x03)
        assertNull(mapping.idFor(Version.MINECRAFT_1_8))
        assertEquals(0x03, mapping.idFor(Version.MINECRAFT_1_20_2))
    }

    @Test
    fun `todo mapping never resolves and is not filled`() {
        val todo = PacketMapping.todo()
        assertNull(todo.idFor(Version.MINECRAFT_26_3))
        assert(!todo.isFilled)
    }

    @Test
    fun `idFor works from a raw protocol number`() {
        val mapping = PacketMapping.ofProtocol(47 to 0x00, 340 to 0x0b)
        assertNull(mapping.idFor(46))
        assertEquals(0x00, mapping.idFor(338)) // 1.12.1 -> still the 47 entry
        assertEquals(0x0b, mapping.idFor(340)) // 1.12.2
    }

    @Test
    fun `construction rejects duplicate protocols`() {
        assertFailsWith<IllegalArgumentException> {
            PacketMapping.ofProtocol(47 to 0x00, 47 to 0x01)
        }
    }

    @Test
    fun `registry decodes a serverbound handshake by id`() {
        val buf = ByteMessage(Unpooled.buffer())
        buf.writeVarInt(770)
        buf.writeString("localhost")
        buf.buf.writeShort(25565)
        buf.writeVarInt(2)

        val packet = PacketRegistry[ConnectionState.HANDSHAKE]
            .decode(0x00, Version.MINECRAFT_1_21_5, buf) as HandshakePacket

        assertEquals(770, packet.protocolVersion)
        assertEquals("localhost", packet.serverAddress)
        assertEquals(25565, packet.serverPort)
        assertEquals(2, packet.nextState)
    }

    @Test
    fun `wired clientbound ids resolve per version`() {
        val play = PacketRegistry[ConnectionState.PLAY]
        val bossBar = BossBarPacket(UUID.randomUUID(), BossBarPacket.Remove)
        assertEquals(0x0c, play.idOf(bossBar, Version.MINECRAFT_1_9))   // proto 107
        assertEquals(0x0a, play.idOf(bossBar, Version.MINECRAFT_1_20_2)) // proto 764
        assertNull(play.idOf(bossBar, Version.MINECRAFT_1_8))            // boss_bar didn't exist in 1.8
    }
}
