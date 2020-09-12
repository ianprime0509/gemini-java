package xyz.ianjohnson.gemini.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.SubmissionPublisher;

final class GeminiBodyDecoder extends ChannelInboundHandlerAdapter {
  private final SubmissionPublisher<List<ByteBuffer>> publisher = new SubmissionPublisher<>();

  GeminiBodyDecoder(final Subscriber<List<ByteBuffer>> subscriber) {
    publisher.subscribe(subscriber);
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
    publisher.close();
    super.channelInactive(ctx);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    publisher.closeExceptionally(cause);
    ctx.close();
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    if (!(msg instanceof ByteBuf)) {
      ReferenceCountUtil.release(msg);
      throw new UnsupportedMessageTypeException("Only ByteBuf messages are supported");
    }

    final var buf = (ByteBuf) msg;
    final var chunk = ByteBuffer.allocate(buf.readableBytes());
    buf.readBytes(chunk);
    buf.release();
    publisher.submit(List.of(chunk.flip().asReadOnlyBuffer()));
  }
}
