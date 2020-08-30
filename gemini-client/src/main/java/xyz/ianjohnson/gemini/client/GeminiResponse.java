package xyz.ianjohnson.gemini.client;

import com.google.auto.value.AutoValue;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;
import xyz.ianjohnson.gemini.GeminiStatus;
import xyz.ianjohnson.gemini.MimeType;

@AutoValue
public abstract class GeminiResponse<T> {
  GeminiResponse() {}

  static <T> Builder<T> newBuilder() {
    return new AutoValue_GeminiResponse.Builder<>();
  }

  public abstract URI uri();

  public abstract GeminiStatus status();

  public abstract String meta();

  public abstract Optional<T> body();

  @FunctionalInterface
  public interface BodyHandler<T> {
    BodySubscriber<T> apply(MimeType mimeType);
  }

  public static final class BodyHandlers {
    private BodyHandlers() {}

    public static BodyHandler<Void> discarding() {
      return mimeType -> BodySubscribers.discarding();
    }

    public static BodyHandler<byte[]> ofByteArray() {
      return mimeType -> BodySubscribers.ofByteArray();
    }

    public static BodyHandler<String> ofString() {
      return mimeType ->
          BodySubscribers.ofString(Charset.forName(mimeType.parameter("charset").orElse("UTF-8")));
    }
  }

  public interface BodySubscriber<T> extends Subscriber<List<ByteBuffer>> {
    CompletionStage<T> getBody();
  }

  public static final class BodySubscribers {
    private BodySubscribers() {}

    public static BodySubscriber<Void> discarding() {
      return new BodySubscriber<>() {
        private final CompletableFuture<Void> future = new CompletableFuture<>();

        @Override
        public CompletionStage<Void> getBody() {
          return future;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
          subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(final List<ByteBuffer> item) {}

        @Override
        public void onError(final Throwable throwable) {
          future.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
          future.complete(null);
        }
      };
    }

    public static BodySubscriber<byte[]> ofByteArray() {
      return new BodySubscriber<>() {
        private final List<ByteBuffer> buffers = new ArrayList<>();
        private final CompletableFuture<byte[]> future = new CompletableFuture<>();
        private Subscription subscription;

        @Override
        public void onSubscribe(final Subscription subscription) {
          this.subscription = subscription;
          this.subscription.request(1);
        }

        @Override
        public void onNext(final List<ByteBuffer> item) {
          buffers.addAll(item);
          this.subscription.request(1);
        }

        @Override
        public void onError(final Throwable throwable) {
          future.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
          final var size = buffers.stream().mapToLong(buf -> buf.limit() - buf.position()).sum();
          final var bytes = new byte[(int) Math.min(size, Integer.MAX_VALUE)];
          var offset = 0;
          for (final var buf : buffers) {
            final var len = Math.min(buf.limit() - buf.position(), bytes.length - offset);
            buf.get(bytes, offset, len);
            offset += len;
          }
          future.complete(bytes);
        }

        @Override
        public CompletionStage<byte[]> getBody() {
          return future.minimalCompletionStage();
        }
      };
    }

    public static BodySubscriber<String> ofString(final Charset charset) {
      return mapping(ofByteArray(), bytes -> new String(bytes, charset));
    }

    public static <T, U> BodySubscriber<U> mapping(
        final BodySubscriber<T> upstream, final Function<? super T, ? extends U> finisher) {
      return new BodySubscriber<>() {
        @Override
        public void onSubscribe(final Subscription subscription) {
          upstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(final List<ByteBuffer> item) {
          upstream.onNext(item);
        }

        @Override
        public void onError(final Throwable throwable) {
          upstream.onError(throwable);
        }

        @Override
        public void onComplete() {
          upstream.onComplete();
        }

        @Override
        public CompletionStage<U> getBody() {
          return upstream.getBody().thenApply(finisher);
        }
      };
    }
  }

  @AutoValue.Builder
  abstract static class Builder<T> {
    Builder() {}

    abstract Builder<T> uri(URI uri);

    abstract Builder<T> status(GeminiStatus status);

    abstract Builder<T> meta(String meta);

    abstract Builder<T> body(T body);

    abstract GeminiResponse<T> build();
  }
}
