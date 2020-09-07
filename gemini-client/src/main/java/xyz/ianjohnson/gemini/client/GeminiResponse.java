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

/**
 * A decoded response from a Gemini server.
 *
 * @param <T> the type of the decoded response body
 */
@AutoValue
public abstract class GeminiResponse<T> {
  GeminiResponse() {}

  /**
   * Returns a new {@link GeminiResponse.Builder}.
   *
   * @param <T> the type of the decoded response body
   * @return a new {@link GeminiResponse.Builder}
   */
  public static <T> Builder<T> newBuilder() {
    return new AutoValue_GeminiResponse.Builder<>();
  }

  /** The URI sent with the corresponding request. */
  public abstract URI uri();

  /** The response status. */
  public abstract GeminiStatus status();

  /**
   * The meta string returned with the response. This usually gives more details on the type of the
   * response or the reason for receiving a particular status.
   */
  public abstract String meta();

  /** The decoded response body, if any. */
  public abstract Optional<T> body();

  /** Details about a redirect requested by the server. */
  @AutoValue
  public abstract static class Redirect {
    Redirect() {}

    public static Redirect of(final URI uri, final boolean permanent) {
      return new AutoValue_GeminiResponse_Redirect(uri, permanent);
    }

    /** The {@link URI} that the server requested the client to redirect to. */
    public abstract URI uri();

    /**
     * Whether the server indicated that the redirect was permanent (using the response status
     * {@link xyz.ianjohnson.gemini.StandardGeminiStatus#PERMANENT_REDIRECT}).
     */
    public abstract boolean permanent();
  }

  /**
   * A response body handler, returning an object that can decode a response body based on its MIME
   * type.
   *
   * @param <T> the type of the decoded response body
   */
  @FunctionalInterface
  public interface BodyHandler<T> {
    /**
     * Given the MIME type of the response body, returns a {@link BodySubscriber} to decode it.
     *
     * @param mimeType the MIME type of the response body that will be sent by the server and
     *     decoded by the returned {@link BodySubscriber}
     * @return a {@link BodySubscriber} to decode the response body
     */
    BodySubscriber<T> apply(MimeType mimeType);
  }

  /** A collection of useful pre-defined {@link BodyHandler BodyHandlers}. */
  public static final class BodyHandlers {
    private BodyHandlers() {}

    /**
     * Returns a {@link BodyHandler} that discards the entire response body.
     *
     * @return a {@link BodyHandler} that discards the entire response body
     */
    public static BodyHandler<Void> discarding() {
      return mimeType -> BodySubscribers.discarding();
    }

    /**
     * Returns a {@link BodyHandler} that collects the response body into a byte array.
     *
     * @return a {@link BodyHandler} that collects the response body into a byte array
     */
    public static BodyHandler<byte[]> ofByteArray() {
      return mimeType -> BodySubscribers.ofByteArray();
    }

    /**
     * Returns a {@link BodyHandler} that collects the response body into a string. The {@code
     * charset} parameter of the MIME type passed to the body handler will be used to determine the
     * charset to use to decode the response body; a default of UTF-8 is assumed.
     *
     * @return a {@link BodyHandler} that collects the response body into a string
     */
    public static BodyHandler<String> ofString() {
      return mimeType ->
          BodySubscribers.ofString(Charset.forName(mimeType.parameter("charset").orElse("UTF-8")));
    }
  }

  /**
   * A {@link Subscriber} that receives sequential chunks of the response body and eventually
   * returns the decoded body.
   *
   * @param <T> the type of the decoded response body
   */
  public interface BodySubscriber<T> extends Subscriber<List<ByteBuffer>> {
    /**
     * Returns a future that eventually completes with the decoded response body (or an error).
     *
     * @return a future that eventually completes with the decoded response body (or an error)
     */
    CompletionStage<T> getBody();
  }

  /** A collection of useful pre-defined {@link BodySubscriber BodySubscribers}. */
  public static final class BodySubscribers {
    private BodySubscribers() {}

    /**
     * Returns a {@link BodySubscriber} that discards the entire response body.
     *
     * @return a {@link BodySubscriber} that discards the entire response body
     */
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

    /**
     * Returns a {@link BodySubscriber} that collects the response body into a byte array.
     *
     * @return a {@link BodySubscriber} that collects the response body into a byte array
     */
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

    /**
     * Returns a {@link BodySubscriber} that collects the response body into a string, decoding it
     * with the given {@link Charset}.
     *
     * @param charset the charset to use to decode the response body
     * @return a {@link BodySubscriber} that collects the response body into a string
     */
    public static BodySubscriber<String> ofString(final Charset charset) {
      return mapping(ofByteArray(), bytes -> new String(bytes, charset));
    }

    /**
     * Returns a {@link BodySubscriber} that decodes the body using another {@link BodySubscriber}
     * and then applies a function to convert it into its final form.
     *
     * @param upstream a {@link BodySubscriber} to decode the body into its initial form
     * @param finisher the function to convert the initial decoded body into its final form
     * @param <T> the type of the initial decoded body
     * @param <U> the type of the final decoded body
     * @return a {@link BodySubscriber} that maps the final output of another {@link BodySubscriber}
     */
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
  public abstract static class Builder<T> {
    Builder() {}

    public abstract Builder<T> uri(URI uri);

    public abstract Builder<T> status(GeminiStatus status);

    public abstract Builder<T> meta(String meta);

    public abstract Builder<T> body(T body);

    public abstract GeminiResponse<T> build();
  }
}
