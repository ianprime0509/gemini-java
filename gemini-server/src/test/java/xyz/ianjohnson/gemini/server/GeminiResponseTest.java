package xyz.ianjohnson.gemini.server;

import static org.assertj.core.api.Assertions.assertThat;
import static xyz.ianjohnson.gemini.server.TestUtils.utf8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.server.GeminiResponse.BodyPublishers;

public class GeminiResponseTest {
  public static class BodyPublishersTest {
    @Test
    public void testEmpty_publishesNoBody() throws Throwable {
      assertThat(collectBytes(BodyPublishers.empty())).isEmpty();
    }

    @Test
    public void testOfBytes_withNonEmptyByteArray_publishesInputByteArray() throws Throwable {
      final var bytes = utf8("Hello, world!");
      assertThat(collectBytes(BodyPublishers.ofByteArray(bytes))).containsExactly(bytes);
    }

    @Test
    public void testOfBytes_withEmptyByteArray_publishesInputByteArray() throws Throwable {
      final var bytes = new byte[0];
      assertThat(collectBytes(BodyPublishers.ofByteArray(bytes))).isEmpty();
    }

    @Test
    public void testOfString_withNoCharsetSpecified_usesUtf8() throws Throwable {
      assertThat(collectBytes(BodyPublishers.ofString("Hello, world!")))
          .containsExactly(utf8("Hello, world!"));
    }

    @Test
    public void testOfString_withCharsetSpecified_decodesValueWithGivenCharset() throws Throwable {
      assertThat(collectBytes(BodyPublishers.ofString("Hello, world!", StandardCharsets.UTF_16BE)))
          .containsExactly("Hello, world!".getBytes(StandardCharsets.UTF_16BE));
    }

    private byte[] collectBytes(final Publisher<List<ByteBuffer>> publisher) throws Throwable {
      final var output = new ByteArrayOutputStream();
      final var future = new CompletableFuture<Void>();
      publisher.subscribe(
          new Subscriber<>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription subscription) {
              this.subscription = subscription;
              this.subscription.request(1);
            }

            @Override
            public void onNext(final List<ByteBuffer> item) {
              for (final var buf : item) {
                final var bytes = new byte[buf.remaining()];
                buf.get(bytes);
                synchronized (output) {
                  try {
                    output.write(bytes);
                  } catch (final IOException ignored) {
                    // Impossible
                  }
                }
              }
              subscription.request(1);
            }

            @Override
            public void onError(final Throwable throwable) {
              future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
              future.complete(null);
            }
          });
      future.get(500, TimeUnit.MILLISECONDS);
      return output.toByteArray();
    }
  }
}
