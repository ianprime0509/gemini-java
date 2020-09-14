package xyz.ianjohnson.gemini.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.TooLongFrameException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.StandardGeminiStatus;

final class GeminiRequestDecoder extends DelimiterBasedFrameDecoder {
  private static final int MAX_URI_LENGTH = 1024;
  // Two bytes extra for CRLF
  private static final int MAX_REQUEST_LENGTH = MAX_URI_LENGTH + 2;
  private static final Logger log = LoggerFactory.getLogger(GeminiRequestDecoder.class);

  GeminiRequestDecoder() {
    super(MAX_REQUEST_LENGTH, true, true, Delimiters.lineDelimiter());
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    if (cause instanceof TooLongFrameException) {
      log.atWarn()
          .setCause(cause)
          .addKeyValue("remoteAddress", ctx.channel().remoteAddress())
          .addKeyValue("localAddress", ctx.channel().localAddress())
          .log("Bad request - request URI too long");
      ctx.writeAndFlush(GeminiResponse.of(StandardGeminiStatus.BAD_REQUEST, "Request URI too long"))
          .addListener(ChannelFutureListener.CLOSE);
    } else {
      log.atError()
          .setCause(cause)
          .addKeyValue("remoteAddress", ctx.channel().remoteAddress())
          .addKeyValue("localAddress", ctx.channel().localAddress())
          .log("Unknown error decoding request");
      ctx.close();
    }
  }

  @Override
  protected GeminiRequest decode(final ChannelHandlerContext ctx, final ByteBuf buffer)
      throws Exception {
    final var bytes = (ByteBuf) super.decode(ctx, buffer);
    if (bytes == null) {
      return null;
    }

    try {
      return GeminiRequest.newBuilder()
          .localAddress(ctx.channel().localAddress())
          .remoteAddress(ctx.channel().remoteAddress())
          .uri(new URI(bytes.toString(StandardCharsets.UTF_8)))
          .build();
    } catch (final URISyntaxException | IllegalArgumentException e) {
      log.atWarn()
          .setCause(e)
          .addKeyValue("remoteAddress", ctx.channel().remoteAddress())
          .addKeyValue("localAddress", ctx.channel().localAddress())
          .log("Bad request - invalid request URI");
      ctx.writeAndFlush(
              GeminiResponse.of(
                  StandardGeminiStatus.BAD_REQUEST, "Invalid request URI: " + e.getMessage()))
          .addListener(ChannelFutureListener.CLOSE);
      return null;
    }
  }
}
