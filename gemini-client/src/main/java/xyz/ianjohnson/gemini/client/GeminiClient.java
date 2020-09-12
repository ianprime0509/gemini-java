package xyz.ianjohnson.gemini.client;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.GeminiStatus.Kind;
import xyz.ianjohnson.gemini.StandardGeminiStatus;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandler;
import xyz.ianjohnson.gemini.client.GeminiResponse.Redirect;

/**
 * A Gemini client.
 *
 * <p>The API of this client closely mirrors that of Java's native HttpClient.
 */
public final class GeminiClient implements Closeable {
  public static final int GEMINI_PORT = 1965;
  static final String GEMINI_SCHEME = "gemini";
  private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

  private final Executor executor;
  private final boolean userProvidedExecutor;
  private final EventLoopGroup eventLoopGroup;
  private final SslContext sslContext;
  private final int maxRedirects;

  private GeminiClient(final Builder builder) {
    if (builder.executor != null) {
      executor = builder.executor;
      userProvidedExecutor = true;
    } else {
      executor = Executors.newCachedThreadPool();
      userProvidedExecutor = false;
    }
    eventLoopGroup = new NioEventLoopGroup(0, executor);

    final var trustManager =
        builder.trustManager != null
            ? builder.trustManager
            : new TofuClientTrustManager(new KeyStoreManager(createDefaultKeyStore()));
    try {
      sslContext =
          SslContextBuilder.forClient()
              // Note: there appears to be a bug in Java 11 that prevents some connections from
              // working unless we use only TLSv1.2. Java 14 seems to work, but maybe this should
              // be checked somehow at runtime and configured appropriately.
              .protocols("TLSv1.2", "TLSv1.3")
              .trustManager(trustManager)
              .build();
    } catch (final SSLException e) {
      throw new IllegalStateException("Failed to construct SslContext", e);
    }
    maxRedirects = builder.maxRedirects;
  }

  /**
   * Returns a new {@link GeminiClient.Builder}.
   *
   * @return a new {@link GeminiClient.Builder}
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Returns a new {@link GeminiClient} using default settings. This is equivalent to calling {@code
   * GeminiClient.newBuilder().build()}; see the documentation of {@link GeminiClient.Builder} for
   * details about the default settings.
   *
   * @return a new {@link GeminiClient} using default settings
   */
  public static GeminiClient newGeminiClient() {
    return newBuilder().build();
  }

  private static KeyStore createDefaultKeyStore() {
    try {
      final var keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null);
      return keyStore;
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to construct default KeyStore", e);
    }
  }

  @Override
  public void close() {
    eventLoopGroup.shutdownGracefully();
  }

  /**
   * Sends a request for the given URI, blocking if necessary until the response is ready or the
   * request is interrupted.
   *
   * <p>This is a shortcut for {@link #send(String, int, URI, BodyHandler)} in the most common case,
   * where the URI being requested is hosted on the server to which the request is sent (that is, no
   * proxying is requested).
   *
   * @param uri the URI to request. The request is sent to the host and port specified in the URI.
   * @param responseBodyHandler the response body handler
   * @param <T> the type of the decoded response body
   * @return a {@link GeminiResponse} containing the response details
   * @throws IOException if an I/O-related exception occurs while executing the request
   * @throws InterruptedException if the request is interrupted before it completes
   */
  public <T> GeminiResponse<T> send(final URI uri, final BodyHandler<T> responseBodyHandler)
      throws IOException, InterruptedException {
    return getResponse(sendAsync(uri, responseBodyHandler));
  }

  /**
   * Sends a request for the given URI, blocking if necessary until the response is ready or the
   * request is interrupted.
   *
   * @param host the host of the server to which to send the request
   * @param port the the port of the server to which to send the request
   * @param uri the URI to request
   * @param responseBodyHandler the response body handler
   * @param <T> the type of the decoded response body
   * @return a {@link GeminiResponse} containing the response details
   * @throws IOException if an I/O-related exception occurs while executing the request
   * @throws InterruptedException if the request is interrupted before it completes
   */
  public <T> GeminiResponse<T> send(
      final String host, final int port, final URI uri, final BodyHandler<T> responseBodyHandler)
      throws IOException, InterruptedException {
    return getResponse(sendAsync(host, port, uri, responseBodyHandler));
  }

  private <T> GeminiResponse<T> getResponse(final Future<GeminiResponse<T>> future)
      throws IOException, InterruptedException {
    try {
      return future.get();
    } catch (final InterruptedException e) {
      future.cancel(true);
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

  /**
   * Sends an asynchronous request for the given URI.
   *
   * <p>This is a shortcut for {@link #sendAsync(String, int, URI, BodyHandler)} in the most common
   * case, where the URI being requested is hosted on the server to which the request is sent (that
   * is, no proxying is requested).
   *
   * @param uri the URI to request. The request is sent to the host and port specified in the URI.
   * @param responseBodyHandler the response body handler
   * @param <T> the type of the decoded response body
   * @return a {@link CompletableFuture} that will eventually complete with the received response
   *     details or an error encountered while handling the request
   */
  public <T> CompletableFuture<GeminiResponse<T>> sendAsync(
      final URI uri, final BodyHandler<T> responseBodyHandler) {
    if (uri.getScheme() != null && !GEMINI_SCHEME.equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("URI scheme must be gemini");
    }

    return sendAsync(uri.getHost(), uri.getPort(), uri, responseBodyHandler);
  }

  /**
   * Sends an asynchronous request for the given URI.
   *
   * @param host the host of the server to which to send the request
   * @param port the the port of the server to which to send the request
   * @param uri the URI to request
   * @param responseBodyHandler the response body handler
   * @param <T> the type of the decoded response body
   * @return a {@link CompletableFuture} that will eventually complete with the received response
   *     details or an error encountered while handling the request
   */
  public <T> CompletableFuture<GeminiResponse<T>> sendAsync(
      final String host, final int port, final URI uri, final BodyHandler<T> responseBodyHandler) {
    final var future = new FutureImpl<GeminiResponse<T>>();
    sendAsync(host, port, uri, responseBodyHandler, new ArrayList<>())
        .whenComplete(
            (response, t) -> {
              if (response != null) {
                future.complete(response);
              } else if (t instanceof CompletionException) {
                future.completeExceptionally(t.getCause());
              } else {
                future.completeExceptionally(t);
              }
            });
    return future;
  }

  private <T> CompletableFuture<GeminiResponse<T>> sendAsync(
      final String host,
      final int providedPort,
      final URI uri,
      final BodyHandler<T> responseBodyHandler,
      final List<Redirect> redirects) {
    if (redirects.size() > maxRedirects) {
      return CompletableFuture.failedFuture(new TooManyRedirectsException(redirects));
    } else if (host == null || host.isEmpty()) {
      throw new IllegalArgumentException("Invalid host");
    }
    final var port = providedPort != -1 ? providedPort : GEMINI_PORT;

    log.debug("Connecting to host {} on port {}", host, port);

    final CompletableFuture<GeminiResponse<T>> future = new FutureImpl<>();
    new Bootstrap()
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(final SocketChannel ch) {
                ch.pipeline()
                    .addLast("ssl", sslContext.newHandler(ch.alloc(), uri.getHost(), port))
                    .addLast(new GeminiResponseHandler<>(uri, responseBodyHandler, future));
              }
            })
        .connect(uri.getHost(), port)
        .addListener(
            (final ChannelFuture channelFuture) -> {
              if (!channelFuture.isSuccess()) {
                future.completeExceptionally(channelFuture.cause());
                return;
              }

              final var channel = channelFuture.channel();
              future.whenComplete((response, e) -> channel.close());

              channel
                  .writeAndFlush(wrappedBuffer((uri + "\r\n").getBytes(StandardCharsets.UTF_8)))
                  .addListener(
                      writeFuture -> {
                        if (!writeFuture.isSuccess()) {
                          future.completeExceptionally(writeFuture.cause());
                        }
                      });
            });

    return future.thenCompose(
        response -> {
          final var redirectFuture = new FutureImpl<GeminiResponse<T>>();
          if (response.status().kind() == Kind.REDIRECT) {
            final URI redirectUri;
            try {
              redirectUri = uri.resolve(new URI(response.meta().strip()));
            } catch (final URISyntaxException e) {
              redirectFuture.completeExceptionally(
                  new MalformedResponseException("Invalid URI for redirect", e));
              return redirectFuture;
            }

            if (redirectUri.getHost() == null || redirectUri.getHost().isEmpty()) {
              redirectFuture.completeExceptionally(
                  new MalformedResponseException("Redirect URI missing host: " + redirectUri));
              return redirectFuture;
            }
            if (redirectUri.getScheme() != null
                && !redirectUri.getScheme().equalsIgnoreCase(GEMINI_SCHEME)) {
              // Cannot handle non-Gemini redirect
              redirectFuture.complete(response);
              return redirectFuture;
            }
            redirects.add(
                Redirect.of(
                    redirectUri, response.status() == StandardGeminiStatus.PERMANENT_REDIRECT));
            return sendAsync(
                redirectUri.getHost(),
                redirectUri.getPort(),
                redirectUri,
                responseBodyHandler,
                redirects);
          }
          redirectFuture.complete(response);
          return redirectFuture;
        });
  }

  /**
   * Returns the {@link Executor} configured with this client, if one was provided by the user.
   *
   * @return the user-provided {@link Executor} for use with this client
   */
  public Optional<Executor> executor() {
    return userProvidedExecutor ? Optional.of(executor) : Optional.empty();
  }

  /** A builder for {@link GeminiClient GeminiClients}. */
  public static final class Builder {
    private Executor executor;
    private TrustManager trustManager;
    private int maxRedirects = 5;

    private Builder() {}

    /**
     * Sets the {@link Executor} to use for handling asynchronous tasks.
     *
     * <p>If no executor is explicitly provided using this method, the client will use an internal
     * default executor.
     *
     * @param executor the {@link Executor} to use for handling asynchronous tasks
     * @return {@code this}
     */
    public Builder executor(final Executor executor) {
      this.executor = requireNonNull(executor, "executor");
      return this;
    }

    /**
     * Sets the {@link TrustManager} to use for evaluating trust decisions.
     *
     * <p>If no trust manager is explicitly provided using this method, the client will use a {@link
     * TofuClientTrustManager} with an internal key store that is not persisted beyond the life of
     * the client. Hence, unless the same client instance is meant to be used for an extended period
     * of time, it is highly recommended to configure a {@link TofuClientTrustManager} backed by a
     * key store that is already loaded with previously trusted certificates and can be saved after
     * the client shuts down to persist any new certificates.
     *
     * @param trustManager the {@link TrustManager} to use for evaluating trust decisions
     * @return {@code this}
     */
    public Builder trustManager(final TrustManager trustManager) {
      this.trustManager = requireNonNull(trustManager, "trustManager");
      return this;
    }

    /**
     * Sets the maximum number of redirects to follow when handling a request.
     *
     * <p>If no maximum is explicitly provided using this method, the default number is 5. Note that
     * since {@link GeminiClient} can only make Gemini requests, any redirects to services using
     * other protocols (as indicated by the URI scheme) will result in the client returning a {@link
     * GeminiResponse} indicating a redirect.
     *
     * @param maxRedirects the maximum number of redirects to follow when handling a request
     * @return {@code this}
     */
    public Builder maxRedirects(final int maxRedirects) {
      if (maxRedirects < 0) {
        throw new IllegalArgumentException("maxRedirects must not be negative");
      }
      this.maxRedirects = maxRedirects;
      return this;
    }

    /**
     * Builds a new {@link GeminiClient} using the configuration specified using this {@link
     * Builder}.
     *
     * @return a new {@link GeminiClient} using the configuration specified using this {@link
     *     Builder}.
     */
    public GeminiClient build() {
      return new GeminiClient(this);
    }
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
