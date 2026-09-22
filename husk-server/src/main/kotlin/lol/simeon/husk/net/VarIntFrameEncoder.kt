package lol.simeon.husk.net

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import lol.simeon.mycelium.ByteMessage

/**
 * Outbound counterpart of [VarIntFrameDecoder]: prepends each encoded packet with its length as a
 * VarInt, producing a valid length-prefixed frame on the wire.
 */
class VarIntFrameEncoder : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        ByteMessage(out).writeVarInt(msg.readableBytes())
        out.writeBytes(msg)
    }
}
