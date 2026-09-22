package lol.simeon.husk.net

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException

/**
 * Splits the TCP stream into single packets: each is a VarInt length prefix followed by that
 * many bytes. Emits one full-packet [ByteBuf] slice per frame; waits for more data if incomplete.
 */
class VarIntFrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (!input.isReadable) return
        input.markReaderIndex()

        var length = 0
        var bytesRead = 0
        while (true) {
            if (!input.isReadable) {
                input.resetReaderIndex() // length prefix not fully arrived yet
                return
            }
            val current = input.readByte().toInt()
            length = length or ((current and 0x7F) shl (7 * bytesRead))
            bytesRead++
            if (bytesRead > MAX_VARINT_BYTES) throw CorruptedFrameException("packet length VarInt too big")
            if (current and 0x80 == 0) break
        }

        if (length < 0) throw CorruptedFrameException("negative packet length: $length")
        if (input.readableBytes() < length) {
            input.resetReaderIndex() // body not fully arrived yet
            return
        }
        out.add(input.readRetainedSlice(length))
    }

    private companion object {
        const val MAX_VARINT_BYTES = 5
    }
}
