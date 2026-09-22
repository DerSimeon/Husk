package lol.simeon.husk.net

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.MessageToMessageDecoder
import lol.simeon.husk.logging.Log
import lol.simeon.husk.protocol.PacketRegistry
import lol.simeon.husk.session.Connection
import lol.simeon.mycelium.ByteMessage


class PacketDecoder(private val connection: Connection) : MessageToMessageDecoder<ByteBuf>() {

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (!ctx.channel().isActive) return


        val buf = ByteMessage(msg)
        val id = buf.readVarInt()
        val packet = PacketRegistry[connection.state].decode(id, connection.protocol, connection.protocolVersion, buf)

        if (packet == null) {
            // Limbo: unknown/unhandled ids are expected and safely ignored.
            Log.debug { "IN  ${connection.state}/0x%02x -> (unknown, dropped)".format(id) }
            return
        }

        if (msg.isReadable) {
            throw DecoderException("IN  ${connection.state}/0x%02x -> %s: %d bytes remaining".format(id, packet.javaClass.simpleName, msg.readableBytes()))
        }

        ctx.fireChannelRead(packet)
    }
}
