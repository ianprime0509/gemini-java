package xyz.ianjohnson.gemini.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodySubscriber;

final class BodySubscriberImpls {
  private BodySubscriberImpls() {}

  static final class Discarding implements BodySubscriber<Void> {
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

    @Override
    public String toString() {
      return "Discarding{}";
    }
  }

  static final class OfByteArray implements BodySubscriber<byte[]> {
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

    @Override
    public String toString() {
      return "OfByteArray{}";
    }
  }

  static final class Mapping<T, U> implements BodySubscriber<U> {
    private final BodySubscriber<T> upstream;
    private final Function<? super T, ? extends U> finisher;

    Mapping(final BodySubscriber<T> upstream, final Function<? super T, ? extends U> finisher) {
      this.upstream = upstream;
      this.finisher = finisher;
    }

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

    @Override
    public String toString() {
      return "Mapping{" + "upstream=" + upstream + ", finisher=" + finisher + '}';
    }
  }
}
