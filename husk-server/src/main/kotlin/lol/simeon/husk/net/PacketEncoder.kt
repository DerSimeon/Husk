package lol.simeon.husk.net

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.EncoderException
import io.netty.handler.codec.MessageToByteEncoder
import lol.simeon.husk.logging.Log
import lol.simeon.husk.protocol.ClientboundPacket
import lol.simeon.husk.protocol.PacketRegistry
import lol.simeon.husk.session.Connection
import lol.simeon.mycelium.ByteMessage

/**
 * Serialises a [ClientboundPacket]: VarInt wire id (resolved for the connection's current state and
 * protocol) followed by the packet body. The frame length prefix is prepended downstream by
 * [VarIntFrameEncoder]. Serverbound packets never reach here — the type parameter filters them out.
 */
class PacketEncoder(private val connection: Connection) : MessageToByteEncoder<ClientboundPacket>() {

    override fun encode(ctx: ChannelHandlerContext, msg: ClientboundPacket, out: ByteBuf) {
        val version = connection.protocolVersion
        val protocol = connection.protocol
        val id = PacketRegistry[connection.state].idOf(msg, protocol)
            ?: throw EncoderException(
                "No wire id for ${msg.javaClass.simpleName} in ${connection.state} at $protocol ($version)",
            )

        Log.debug { "OUT ${connection.state}/0x%02x -> %s".format(id, msg.javaClass.simpleName) }

        val buf = ByteMessage(out)
        buf.writeVarInt(id)
        msg.encode(buf, version, protocol)
    }
}
