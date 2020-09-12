package xyz.ianjohnson.gemini.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class GeminiRequestEncoder extends ChannelOutboundHandlerAdapter {
  private static final byte[] crlf = new byte[] {'\r', '\n'};

  @Override
  public void write(
      final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
    if (!(msg instanceof URI)) {
      promise.setFailure(new UnsupportedMessageTypeException("Input must be a URI"));
      return;
    }

    final var bytes = msg.toString().getBytes(StandardCharsets.UTF_8);
    final var buf = ctx.alloc().buffer(bytes.length + 2);
    buf.writeBytes(bytes).writeBytes(crlf);
    ctx.write(buf, promise);
  }
}
