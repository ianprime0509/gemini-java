package xyz.ianjohnson.gemini.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LineBasedFrameDecoder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import xyz.ianjohnson.gemini.StandardGeminiStatus;

final class GeminiRequestDecoder extends LineBasedFrameDecoder {
  private static final int MAX_URI_LENGTH = 1024;
  // Two bytes extra for CRLF
  private static final int MAX_REQUEST_LENGTH = MAX_URI_LENGTH + 2;

  GeminiRequestDecoder() {
    super(MAX_REQUEST_LENGTH);
  }

  @Override
  protected GeminiRequest decode(final ChannelHandlerContext ctx, final ByteBuf buffer)
      throws Exception {
    final var bytes = (ByteBuf) super.decode(ctx, buffer);
    if (bytes == null) {
      return null;
    }

    final URI uri;
    try {
      uri = new URI(bytes.toString(StandardCharsets.UTF_8));
    } catch (final URISyntaxException e) {
      ctx.writeAndFlush(GeminiResponse.of(StandardGeminiStatus.BAD_REQUEST, "Invalid request URI"));
      return null;
    }
    return GeminiRequest.newBuilder()
        .localAddress(ctx.channel().localAddress())
        .remoteAddress(ctx.channel().remoteAddress())
        .uri(uri)
        .build();
  }
}
