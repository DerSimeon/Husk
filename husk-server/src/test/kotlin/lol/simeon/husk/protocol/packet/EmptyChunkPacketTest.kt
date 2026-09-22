package lol.simeon.husk.protocol.packet

import io.netty.buffer.Unpooled
import lol.simeon.mycelium.ByteMessage
import lol.simeon.mycelium.Version
import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyChunkPacketTest {
    @Test
    fun `each section is 8 bytes (block+fluid counts + single-valued palettes)`() {
        val buf = ByteMessage(Unpooled.buffer())
        EmptyChunkPacket(chunkX = 0, chunkZ = 0, sectionCount = 16, biomeId = 0).encode(buf, Version.MINECRAFT_26_2)

        buf.buf.readInt(); buf.buf.readInt() // x, z
        assertEquals(0, buf.readVarInt(), "no heightmaps")
        // section = blockCount(2) + fluidCount(2) + blockBits(1)+airValue(1) + biomeBits(1)+biomeValue(1) = 8
        assertEquals(16 * 8, buf.readVarInt(), "chunk data byte length")
    }
}
