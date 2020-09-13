package xyz.ianjohnson.gemini.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.StandardGeminiStatus;

final class GeminiRequestHandler extends ChannelInboundHandlerAdapter {
  private static final Logger log = LoggerFactory.getLogger(GeminiRequestHandler.class);

  private final Function<GeminiRequest, GeminiResponse> handler;

  GeminiRequestHandler(final Function<GeminiRequest, GeminiResponse> handler) {
    this.handler = requireNonNull(handler, "handler");
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    if (!(msg instanceof GeminiRequest)) {
      throw new UnsupportedMessageTypeException("Input must be a GeminiRequest");
    }

    final var request = (GeminiRequest) msg;
    log.atInfo()
        .addKeyValue("remoteAddress", request.remoteAddress())
        .addKeyValue("localAddress", request.localAddress())
        .addKeyValue("requestUri", request.uri())
        .log("Request received");

    GeminiResponse resp;
    try {
      resp = handler.apply(request);
    } catch (final Exception e) {
      log.atError()
          .setCause(e)
          .addKeyValue("remoteAddress", request.remoteAddress())
          .addKeyValue("localAddress", request.localAddress())
          .log("Internal error when handling request");
      resp = GeminiResponse.of(StandardGeminiStatus.TEMPORARY_FAILURE, "Internal server error");
    }

    log.atInfo()
        .addKeyValue("remoteAddress", ctx.channel().remoteAddress())
        .addKeyValue("localAddress", ctx.channel().localAddress())
        .addKeyValue("status", resp.status().code())
        .addKeyValue("meta", resp.meta())
        .log("Sending response");
    ctx.writeAndFlush(resp)
        .addListener(
            (final ChannelFuture f) -> {
              if (f.isSuccess()) {
                log.atInfo()
                    .addKeyValue("remoteAddress", request.remoteAddress())
                    .addKeyValue("localAddress", request.localAddress())
                    .addKeyValue("requestUri", request.uri())
                    .log("Response completed");
              } else {
                log.atError()
                    .setCause(f.cause())
                    .addKeyValue("remoteAddress", request.remoteAddress())
                    .addKeyValue("localAddress", request.localAddress())
                    .addKeyValue("requestUri", request.uri())
                    .log("Error sending response");
              }
              f.channel().close();
            });
  }
}
