package xyz.ianjohnson.gemini.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GeminiRequestHandler extends SimpleChannelInboundHandler<GeminiRequest> {
  private static final Logger log = LoggerFactory.getLogger(GeminiRequestHandler.class);

  private final Function<GeminiRequest, GeminiResponse> handler;

  GeminiRequestHandler(final Function<GeminiRequest, GeminiResponse> handler) {
    this.handler = handler;
  }

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, final GeminiRequest msg) {
    log.atInfo()
        .addKeyValue("remoteAddress", msg.remoteAddress())
        .addKeyValue("localAddress", msg.localAddress())
        .addKeyValue("requestUri", msg.uri())
        .log("Request received");

    final var resp = handler.apply(msg);
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
                    .addKeyValue("remoteAddress", msg.remoteAddress())
                    .addKeyValue("localAddress", msg.localAddress())
                    .addKeyValue("requestUri", msg.uri())
                    .log("Response completed");
              } else {
                log.atError()
                    .setCause(f.cause())
                    .addKeyValue("remoteAddress", msg.remoteAddress())
                    .addKeyValue("localAddress", msg.localAddress())
                    .addKeyValue("requestUri", msg.uri())
                    .log("Error sending response");
              }
              f.channel().close();
            });
  }
}
