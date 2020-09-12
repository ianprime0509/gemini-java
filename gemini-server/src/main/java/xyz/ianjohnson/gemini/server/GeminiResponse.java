package xyz.ianjohnson.gemini.server;

import com.google.auto.value.AutoValue;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import xyz.ianjohnson.gemini.GeminiStatus;

@AutoValue
public abstract class GeminiResponse {
  GeminiResponse() {}

  public static GeminiResponse of(final GeminiStatus status, final String meta) {
    return of(status, meta, BodyPublishers.empty());
  }

  public static GeminiResponse of(
      final GeminiStatus status,
      final String meta,
      final Publisher<List<ByteBuffer>> bodyPublisher) {
    return new AutoValue_GeminiResponse(status, meta, bodyPublisher);
  }

  public abstract GeminiStatus status();

  public abstract String meta();

  public abstract Publisher<List<ByteBuffer>> bodyPublisher();

  public static final class BodyPublishers {
    private static final Publisher<List<ByteBuffer>> EMPTY =
        subscriber -> {
          subscriber.onSubscribe(
              new Subscription() {
                @Override
                public void request(final long n) {}

                @Override
                public void cancel() {}
              });
          subscriber.onComplete();
        };

    private BodyPublishers() {}

    public static Publisher<List<ByteBuffer>> empty() {
      return EMPTY;
    }

    public static Publisher<List<ByteBuffer>> ofByteArray(final byte[] bytes) {
      return subscriber ->
          subscriber.onSubscribe(
              new Subscription() {
                private final AtomicBoolean done = new AtomicBoolean();

                @Override
                public void request(final long n) {
                  if (n <= 0) {
                    subscriber.onError(
                        new IllegalArgumentException("Requested items must be positive"));
                  } else if (!done.getAndSet(true)) {
                    subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
                    subscriber.onComplete();
                  }
                }

                @Override
                public void cancel() {}
              });
    }

    public static Publisher<List<ByteBuffer>> ofString(final String s) {
      return ofString(s, StandardCharsets.UTF_8);
    }

    public static Publisher<List<ByteBuffer>> ofString(final String s, final Charset charset) {
      return ofByteArray(s.getBytes(charset));
    }
  }
}
