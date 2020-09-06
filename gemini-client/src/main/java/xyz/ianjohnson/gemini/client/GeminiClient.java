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
import io.netty.handler.ssl.SslHandler;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandler;

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
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("URI missing host");
    }
    final var port = uri.getPort() != -1 ? uri.getPort() : GEMINI_PORT;

    return sendAsync(uri.getHost(), port, uri, responseBodyHandler);
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
    log.debug("Connecting to host {} on port {}", host, port);

    final CompletableFuture<GeminiResponse<T>> future = new FutureImpl<>();
    final var bootstrap =
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
                });

    bootstrap
        .connect(uri.getHost(), port)
        .addListener(
            (final ChannelFuture channelFuture) -> {
              if (!channelFuture.isSuccess()) {
                future.completeExceptionally(channelFuture.cause());
                return;
              }

              final var channel = channelFuture.channel();
              future.whenComplete((response, e) -> channel.close());

              final var sslHandler = (SslHandler) channel.pipeline().get("ssl");
              sslHandler
                  .handshakeFuture()
                  .addListener(
                      handshakeFuture -> {
                        if (!handshakeFuture.isSuccess()) {
                          future.completeExceptionally(handshakeFuture.cause());
                          return;
                        }

                        channel
                            .writeAndFlush(
                                wrappedBuffer((uri + "\r\n").getBytes(StandardCharsets.UTF_8)))
                            .addListener(
                                writeFuture -> {
                                  if (!writeFuture.isSuccess()) {
                                    future.completeExceptionally(writeFuture.cause());
                                  }
                                });
                      });
            });

    return future;
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
