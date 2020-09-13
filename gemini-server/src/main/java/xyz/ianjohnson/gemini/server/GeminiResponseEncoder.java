package xyz.ianjohnson.gemini.server;

import static io.netty.buffer.Unpooled.wrappedBuffer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import xyz.ianjohnson.gemini.GeminiStatus.Kind;

final class GeminiResponseEncoder extends ChannelOutboundHandlerAdapter {
  @Override
  public void write(
      final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
    if (!(msg instanceof GeminiResponse)) {
      promise.setFailure(new UnsupportedMessageTypeException("Input must be a GeminiResponse"));
      return;
    }

    final var resp = (GeminiResponse) msg;
    final var header = resp.status().code() + " " + resp.meta() + "\r\n";
    ctx.writeAndFlush(wrappedBuffer(header.getBytes(StandardCharsets.UTF_8)))
        .addListener(
            f -> {
              if (!f.isSuccess()) {
                promise.setFailure(f.cause());
                return;
              }

              if (resp.status().kind() == Kind.SUCCESS) {
                sendBody(resp.bodyPublisher(), ctx, promise);
              } else {
                promise.setSuccess();
              }
            });
  }

  private void sendBody(
      final Publisher<List<ByteBuffer>> bodyPublisher,
      final ChannelHandlerContext ctx,
      final ChannelPromise promise) {
    bodyPublisher.subscribe(
        new Subscriber<>() {
          private Subscription subscription;

          @Override
          public void onSubscribe(final Subscription subscription) {
            promise.addListener(f -> subscription.cancel());
            this.subscription = subscription;
            this.subscription.request(1);
          }

          @Override
          public void onNext(final List<ByteBuffer> item) {
            ctx.write(wrappedBuffer(item.toArray(new ByteBuffer[0])))
                .addListener(
                    f -> {
                      if (!f.isSuccess()) {
                        promise.tryFailure(f.cause());
                        return;
                      }
                      subscription.request(1);
                    });
          }

          @Override
          public void onError(final Throwable throwable) {
            promise.tryFailure(throwable);
          }

          @Override
          public void onComplete() {
            promise.trySuccess();
          }
        });
  }
}
