package xyz.ianjohnson.gemini.server;

import static io.netty.buffer.Unpooled.buffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xyz.ianjohnson.gemini.server.TestUtils.utf8;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.StandardGeminiStatus;
import xyz.ianjohnson.gemini.server.GeminiResponse.BodyPublishers;

public class GeminiResponseEncoderTest {
  private EmbeddedChannel channel;

  @BeforeEach
  public void setUp() {
    channel = new EmbeddedChannel(new GeminiResponseEncoder());
  }

  @Test
  public void testEncode_withResponseWithNoBody_encodesJustHeader() {
    channel.writeOutbound(
        GeminiResponse.of(StandardGeminiStatus.TEMPORARY_FAILURE, "Temporary failure"));
    channel.finish();
    channel.checkException();

    assertThat(collectResponse()).isEqualTo(wrappedBuffer(utf8("40 Temporary failure\r\n")));
  }

  @Test
  public void testEncode_withResponseWithBody_encodesResponseAndBody() {
    channel.writeOutbound(
        GeminiResponse.of(
            StandardGeminiStatus.SUCCESS,
            "text/plain",
            BodyPublishers.ofString("Hello, world!\n")));
    channel.finish();
    channel.checkException();

    assertThat(collectResponse())
        .isEqualTo(wrappedBuffer(utf8("20 text/plain\r\nHello, world!\n")));
  }

  @Test
  public void testEncode_withByteBuf_throwsUnsupportedMessageTypeException() {
    assertThatThrownBy(
            () -> {
              channel.writeOutbound(wrappedBuffer(utf8("Bad input")));
              channel.finish();
              channel.checkException();
            })
        .isInstanceOf(UnsupportedMessageTypeException.class);
  }

  @Test
  public void testEncode_withErrorInBodyPublisher_rethrowsError() {
    final Publisher<List<ByteBuffer>> publisher =
        subscriber -> {
          subscriber.onSubscribe(
              new Subscription() {
                @Override
                public void request(final long n) {}

                @Override
                public void cancel() {}
              });
          subscriber.onError(new RuntimeException("Publisher error!"));
        };

    assertThatThrownBy(
            () -> {
              channel.writeOutbound(
                  GeminiResponse.of(StandardGeminiStatus.SUCCESS, "text/plain", publisher));
              channel.finish();
              channel.checkException();
            })
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Publisher error!");
  }

  private ByteBuf collectResponse() {
    final var buf = buffer();
    ByteBuf read;
    while ((read = channel.readOutbound()) != null) {
      buf.writeBytes(read);
    }
    return buf;
  }
}
