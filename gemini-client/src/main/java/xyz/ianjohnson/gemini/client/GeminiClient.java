package xyz.ianjohnson.gemini.client;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import xyz.ianjohnson.gemini.GeminiStatus;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.MimeTypeSyntaxException;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandler;

/**
 * A Gemini client.
 *
 * <p>The API of this client closely mirrors that of Java's native HttpClient.
 */
public final class GeminiClient {
  static final String GEMINI_SCHEME = "gemini";
  static final int GEMINI_PORT = 1965;
  private static final int BODY_BUFFER_SIZE = 8192;

  private final Executor executor;
  private final boolean userProvidedExecutor;
  private final SSLContext sslContext;

  private GeminiClient(final Builder builder) {
    if (builder.executor != null) {
      executor = builder.executor;
      userProvidedExecutor = true;
    } else {
      executor = Executors.newCachedThreadPool();
      userProvidedExecutor = false;
    }
    if (builder.sslContext != null) {
      sslContext = builder.sslContext;
    } else {
      try {
        sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, new TrustManager[] {new GeminiTrustManager()}, null);
      } catch (final GeneralSecurityException e) {
        // TODO: handle if necessary?
        throw new RuntimeException(e);
      }
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static GeminiClient newGeminiClient() {
    return newBuilder().build();
  }

  public Optional<Executor> executor() {
    return userProvidedExecutor ? Optional.of(executor) : Optional.empty();
  }

  public SSLContext sslContext() {
    return sslContext;
  }

  public <T> GeminiResponse<T> send(final URI uri, final BodyHandler<T> responseBodyHandler)
      throws IOException, InterruptedException {
    final var respFuture = sendAsync(uri, responseBodyHandler);
    try {
      return respFuture.get();
    } catch (final InterruptedException e) {
      respFuture.cancel(true);
      throw e;
    } catch (final ExecutionException e) {
      final var t = e.getCause();
      if (t instanceof RuntimeException) {
        throw (RuntimeException) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      } else if (t instanceof IOException) {
        throw (IOException) t;
      }
      throw new IOException(t.getMessage(), t);
    }
  }

  public <T> CompletableFuture<GeminiResponse<T>> sendAsync(
      final URI uri, final BodyHandler<T> responseBodyHandler) {
    if (uri.getScheme() != null && !GEMINI_SCHEME.equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("URI scheme must be gemini");
    }
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("URI missing host");
    }
    final var port = uri.getPort() != -1 ? uri.getPort() : GEMINI_PORT;

    final CompletableFuture<GeminiResponse<T>> future = new FutureImpl<>();
    executor.execute(
        () -> {
          final Socket socket;
          try {
            socket = sslContext.getSocketFactory().createSocket(uri.getHost(), port);
          } catch (final IOException e) {
            future.completeExceptionally(e);
            return;
          }

          try {
            socket.getOutputStream().write((uri + "\r\n").getBytes(StandardCharsets.UTF_8));
            final var input = socket.getInputStream();
            final var header = ResponseHeader.read(input);
            final var responseBuilder =
                GeminiResponse.<T>newBuilder().uri(uri).status(header.status()).meta(header.meta());
            if (header.status() == GeminiStatus.SUCCESS) {
              final MimeType mimeType;
              try {
                mimeType = MimeType.parse(header.meta());
              } catch (final MimeTypeSyntaxException e) {
                throw new MalformedResponseException("Invalid MIME type", e);
              }
              final var subscriber = responseBodyHandler.apply(mimeType);
              readBody(input, subscriber, future, header.remaining());
              subscriber
                  .getBody()
                  .whenComplete(
                      (body, e) -> {
                        try {
                          socket.close();
                        } catch (final IOException ex) {
                          if (e != null) {
                            e.addSuppressed(ex);
                            future.completeExceptionally(e);
                          } else {
                            future.completeExceptionally(ex);
                          }
                          return;
                        }

                        if (e == null) {
                          future.complete(responseBuilder.body(body).build());
                        } else {
                          future.completeExceptionally(e);
                        }
                      });
            } else {
              socket.close();
              future.complete(responseBuilder.build());
            }
          } catch (final Throwable e) {
            try {
              socket.close();
            } catch (final Throwable ex) {
              e.addSuppressed(ex);
            }
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  private void readBody(
      final InputStream input,
      final Subscriber<List<ByteBuffer>> subscriber,
      final Future<?> responseFuture,
      final ByteBuffer initial)
      throws IOException {
    try (input;
        final var publisher =
            new SubmissionPublisher<List<ByteBuffer>>(executor, Flow.defaultBufferSize())) {
      publisher.subscribe(subscriber);
      publisher.submit(List.of(initial));
      while (true) {
        if (responseFuture.isDone()) {
          return;
        }

        final var buf = new byte[BODY_BUFFER_SIZE];
        final var read = input.read(buf);
        if (read == -1) {
          return;
        }
        publisher.submit(List.of(ByteBuffer.wrap(buf, 0, read).asReadOnlyBuffer()));
      }
    }
  }

  public static final class Builder {
    private Executor executor;
    private SSLContext sslContext;

    private Builder() {}

    public Builder executor(final Executor executor) {
      this.executor = requireNonNull(executor, "executor");
      return this;
    }

    public Builder sslContext(final SSLContext sslContext) {
      this.sslContext = requireNonNull(sslContext, "sslContext");
      return this;
    }

    public GeminiClient build() {
      return new GeminiClient(this);
    }
  }

  @AutoValue
  abstract static class ResponseHeader {
    private static final int MAX_META_LENGTH = 1024;
    // Two bytes for status, one byte for space, two bytes for CRLF
    private static final int MAX_HEADER_LENGTH = MAX_META_LENGTH + 5;

    ResponseHeader() {}

    static ResponseHeader of(
        final GeminiStatus status, final String meta, final ByteBuffer remaining) {
      return new AutoValue_GeminiClient_ResponseHeader(status, meta, remaining);
    }

    static ResponseHeader read(final InputStream input) throws IOException {
      final var buf = new byte[MAX_HEADER_LENGTH + 1];
      // Attempt to read as much as possible up to the buffer size. Depending on how the data is
      // sent, the first read call may only return part of the header, so we need to continue
      // reading to ensure we get the entire header.
      var read = 0;
      int segRead;
      while (read < buf.length && (segRead = input.read(buf, read, buf.length - read)) != -1) {
        read += segRead;
      }
      if (read < 3) {
        throw new MalformedResponseException("Incomplete response header");
      }
      if (read > MAX_HEADER_LENGTH) {
        throw new MalformedResponseException("Response header too long");
      }

      if (buf[0] < '0' || buf[0] > '9' || buf[1] < '0' || buf[1] > '9') {
        throw new MalformedResponseException("Invalid response status code");
      }
      final var code = 10 * (buf[0] - '0') + (buf[1] - '0');
      final GeminiStatus status;
      try {
        status = GeminiStatus.valueOf(code);
      } catch (final IllegalArgumentException e) {
        throw new UnknownStatusCodeException(code);
      }

      var metaStart = 2;
      for (var metaEnd = metaStart; metaEnd < read; metaEnd++) {
        if (buf[metaEnd] == '\r') {
          metaEnd++;
          if (metaEnd == read || buf[metaEnd] != '\n') {
            throw new MalformedResponseException("Misplaced carriage return in response header");
          }
        }
        // We intentionally allow the header line to be terminated with only a line feed
        if (buf[metaEnd] == '\n') {
          final var remaining =
              ByteBuffer.wrap(buf, metaEnd + 1, read - metaEnd - 1).asReadOnlyBuffer();
          if (buf[metaStart] == ' ') {
            metaStart++;
          }
          if (buf[metaEnd - 1] == '\r') {
            metaEnd--;
          }
          final var meta = new String(buf, metaStart, metaEnd - metaStart, StandardCharsets.UTF_8);
          return of(status, meta, remaining);
        }
      }
      throw new MalformedResponseException("Response header missing terminator");
    }

    abstract GeminiStatus status();

    abstract String meta();

    abstract ByteBuffer remaining();
  }

  final class FutureImpl<T> extends CompletableFuture<T> {
    private FutureImpl() {}

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
      return new FutureImpl<>();
    }

    @Override
    public Executor defaultExecutor() {
      return executor;
    }
  }
}
