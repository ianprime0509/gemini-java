package xyz.ianjohnson.gemini.server;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;

final class BodyPublisherImpls {
  private BodyPublisherImpls() {}

  static class Empty implements Publisher<ByteBuffer> {
    static final Empty INSTANCE = new Empty();

    private Empty() {}

    @Override
    public void subscribe(final Subscriber<? super ByteBuffer> subscriber) {
      subscriber.onSubscribe(
          new Subscription() {
            @Override
            public void request(final long n) {}

            @Override
            public void cancel() {}
          });
      subscriber.onComplete();
    }

    @Override
    public String toString() {
      return "Empty{}";
    }
  }

  static class OfByteArray implements Publisher<ByteBuffer> {
    private final byte[] bytes;

    OfByteArray(final byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public void subscribe(final Subscriber<? super ByteBuffer> subscriber) {
      subscriber.onSubscribe(
          new Subscription() {
            private final AtomicBoolean done = new AtomicBoolean();

            @Override
            public void request(final long n) {
              if (n <= 0) {
                subscriber.onError(
                    new IllegalArgumentException("Requested items must be positive"));
              } else if (!done.getAndSet(true)) {
                subscriber.onNext(ByteBuffer.wrap(bytes).asReadOnlyBuffer());
                subscriber.onComplete();
              }
            }

            @Override
            public void cancel() {}
          });
    }

    @Override
    public String toString() {
      return "OfByteArray{" + "bytes=" + Arrays.toString(bytes) + '}';
    }
  }
}
