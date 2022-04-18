package net.md_5.bungee.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;

public class Varint21FrameDecoder extends ByteToMessageDecoder
{

    private static boolean DIRECT_WARNING;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
        // If we decode an invalid packet and an exception is thrown (thus triggering a close of the connection),
        // the Netty ByteToMessageDecoder will continue to frame more packets and potentially call fireChannelRead()
        // on them, likely with more invalid packets. Therefore, check if the connection is no longer active and if so
        // sliently discard the packet.
        if ( !ctx.channel().isActive() )
        {
            in.skipBytes( in.readableBytes() );
            return;
        }

        in.markReaderIndex();

        for ( int i = 0; i < 3; i++ )
        {
            if ( !in.isReadable() )
            {
                in.resetReaderIndex();
                return;
            }

            byte read = in.readByte();
            if ( read >= 0 )
            {
                in.resetReaderIndex();
                int length = DefinedPacket.readVarInt( in );
                if ( false && length == 0 )
                {
                    throw new CorruptedFrameException( "Empty Packet!" );
                }

                if ( in.readableBytes() < length )
                {
                    in.resetReaderIndex();
                    return;
                } else
                {
                    out.add( in.readRetainedSlice( length ) );
                    return;
                }
            }
        }

        throw new CorruptedFrameException( "length wider than 21-bit" );
    }
}
