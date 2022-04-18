package net.md_5.bungee.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
public class MinecraftDecoder extends MessageToMessageDecoder<ByteBuf>
{

    @Setter
    private Protocol protocol;
    private final boolean server;
    @Setter
    private int protocolVersion;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
        // See Varint21FrameDecoder for the general reasoning. We add this here as ByteToMessageDecoder#handlerRemoved()
        // will fire any cumulated data through the pipeline, so we want to try and stop it here.
        if ( !ctx.channel().isActive() )
        {
            return;
        }

        Protocol.DirectionData prot = ( server ) ? protocol.TO_SERVER : protocol.TO_CLIENT;
        ByteBuf slice = in.copy(); // Can't slice this one due to EntityMap :(

        Object packetTypeInfo = null;
        try
        {
            if ( in.readableBytes() == 0 && !server ) return;

            int packetId = DefinedPacket.readVarInt( in );
            packetTypeInfo = packetId;

            DefinedPacket packet = prot.createPacket( packetId, protocolVersion );
            if ( packet != null )
            {
                packetTypeInfo = packet.getClass();
                packet.read( in, prot.getDirection(), protocolVersion );

                if ( in.isReadable() )
                {
                    throw new BadPacketException( "Packet " + protocol + ":" + prot.getDirection() + "/" + packetId + " (" + packet.getClass().getSimpleName() + ") larger than expected, extra bytes: " + in.readableBytes() );
                }
            } else
            {
                in.skipBytes( in.readableBytes() );
            }

            out.add( new PacketWrapper( packet, slice ) );
            slice = null;
        } catch ( BadPacketException | IndexOutOfBoundsException e )
        {
            final String packetTypeStr;
            if ( packetTypeInfo instanceof Integer )
            {
                packetTypeStr = "id " + Integer.toHexString( (Integer) packetTypeInfo );
            } else if ( packetTypeInfo instanceof Class )
            {
                packetTypeStr = "class " + ( (Class) packetTypeInfo ).getSimpleName();
            } else
            {
                packetTypeStr = "unknown";
            }
            throw new DecoderException( "Error decoding packet " + packetTypeStr + " with contents:\n" + ByteBufUtil.prettyHexDump( slice ), e );
        } finally
        {
            if ( slice != null )
            {
                slice.release();
            }
        }
    }
}
