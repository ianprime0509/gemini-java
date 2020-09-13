package xyz.ianjohnson.gemini.server;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xyz.ianjohnson.gemini.server.TestUtils.utf8;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.StandardGeminiStatus;

public class GeminiRequestHandlerTest {
  @Test
  public void testHandle_withHandlerFunctionReturningResponse_writesOutboundResponse() {
    final var channel =
        new EmbeddedChannel(
            new GeminiRequestHandler(
                r -> GeminiResponse.of(StandardGeminiStatus.PERMANENT_REDIRECT, r.uri() + "/")));
    channel.writeInbound(
        GeminiRequest.newBuilder()
            .localAddress(new InetSocketAddress(1965))
            .remoteAddress(new InetSocketAddress(50492))
            .uri(URI.create("gemini://gemini.test"))
            .build());
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readOutbound())
        .isInstanceOfSatisfying(
            GeminiResponse.class,
            response -> {
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.PERMANENT_REDIRECT);
              assertThat(response.meta()).isEqualTo("gemini://gemini.test/");
            });
  }

  @Test
  public void
      testHandle_withHandlerFunctionThrowingException_writesInternalServerErrorOutboundResponse() {
    final var channel =
        new EmbeddedChannel(
            new GeminiRequestHandler(
                r -> {
                  throw new RuntimeException("Oh no");
                }));
    channel.writeInbound(
        GeminiRequest.newBuilder()
            .localAddress(new InetSocketAddress(1965))
            .remoteAddress(new InetSocketAddress(50492))
            .uri(URI.create("gemini://gemini.test"))
            .build());
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readOutbound())
        .isInstanceOfSatisfying(
            GeminiResponse.class,
            response -> {
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.TEMPORARY_FAILURE);
              assertThat(response.meta()).isEqualTo("Internal server error");
            });
  }

  @Test
  public void testHandle_withByteBuf_throwsUnsupportedMessageTypeException() {
    final var channel =
        new EmbeddedChannel(
            new GeminiRequestHandler(
                r -> GeminiResponse.of(StandardGeminiStatus.TEMPORARY_FAILURE, "Oops")));

    assertThatThrownBy(
            () -> {
              channel.writeInbound(wrappedBuffer(utf8("Invalid")));
              channel.finish();
              channel.checkException();
            })
        .isInstanceOf(UnsupportedMessageTypeException.class);
  }
}
