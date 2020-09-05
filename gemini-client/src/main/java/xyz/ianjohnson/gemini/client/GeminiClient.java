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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandler;

/**
 * A Gemini client.
 *
 * <p>The API of this client closely mirrors that of Java's native HttpClient.
 *
 * <h1>Server certificate validation</h1>
 *
 * <p>As recommended by section 4.2 of the <a
 * href="https://gemini.circumlunar.space/docs/specification.html">Gemini specification</a>, this
 * client implements a simple "TOFU" (trust on first use) model, under which a server's certificate
 * (if valid) is trusted when the host is first visited and stored, and for any further connections
 * the server's certificate is validated against this original certificate (for as long as the
 * original certificate is valid).
 *
 * <p>To store the certificates associated with each host, this class uses a {@link KeyStore}, where
 * the alias of the certificate is the name of the host.
 */
public final class GeminiClient implements Closeable {
  static final String GEMINI_SCHEME = "gemini";
  static final int GEMINI_PORT = 1965;
  private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

  private final Executor executor;
  private final boolean userProvidedExecutor;
  private final EventLoopGroup eventLoopGroup;
  private final SslContext sslContext;
  private final KeyStore keyStore;

  private GeminiClient(final Builder builder) {
    if (builder.executor != null) {
      executor = builder.executor;
      userProvidedExecutor = true;
    } else {
      executor = Executors.newCachedThreadPool();
      userProvidedExecutor = false;
    }
    eventLoopGroup = new NioEventLoopGroup(0, executor);

    if (builder.sslContext != null) {
      sslContext = builder.sslContext;
    } else {
      try {
        sslContext =
            SslContextBuilder.forClient()
                // Note: there appears to be a bug in Java 11 that prevents some connections from
                // working unless we use only TLSv1.2. Java 14 seems to work, but maybe this should
                // be checked somehow at runtime and configured appropriately.
                .protocols("TLSv1.2", "TLSv1.3")
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
      } catch (final SSLException e) {
        throw new IllegalStateException("Failed to construct SslContext", e);
      }
    }

    keyStore = builder.keyStore != null ? builder.keyStore : createDefaultKeyStore();
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
   * @param uri the URI to request. The request is sent to the host and port specified in the URI.
   * @param responseBodyHandler the response body handler
   * @param <T> the type of the decoded response body
   * @return a {@link GeminiResponse} containing the response details
   * @throws IOException if an I/O-related exception occurs while executing the request
   * @throws InterruptedException if the request is interrupted before it completes
   */
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

  /**
   * Sends an asynchronous request for the given URI.
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
    log.debug("Connecting to host {} on port {}", uri.getHost(), port);

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

                        try {
                          validatePeerCertificate(sslHandler.engine().getSession());
                        } catch (final CertificateException e) {
                          future.completeExceptionally(e);
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

  /**
   * Returns the {@link SslContext} used by this client.
   *
   * @return the {@link SslContext} used by this client
   */
  public SslContext sslContext() {
    return sslContext;
  }

  /**
   * Returns the {@link KeyStore} used by this client.
   *
   * @return the {@link KeyStore} used by this client
   */
  public KeyStore keyStore() {
    return keyStore;
  }

  private void validatePeerCertificate(final SSLSession session) throws CertificateException {
    final var host = session.getPeerHost();
    final Certificate[] peerCerts;
    try {
      peerCerts = session.getPeerCertificates();
    } catch (final SSLPeerUnverifiedException e) {
      throw new CertificateException("Could not verify peer", e);
    }
    if (peerCerts.length == 0 || !(peerCerts[0] instanceof X509Certificate)) {
      throw new CertificateException("No valid certificate supplied by peer");
    }
    final var peerCert = (X509Certificate) peerCerts[0];
    peerCert.checkValidity();

    final X509Certificate knownCert;
    try {
      final var tmp = keyStore.getCertificate(host);
      if (tmp != null && !(tmp instanceof X509Certificate)) {
        throw new CertificateException("Known certificate is not an X509Certificate");
      }
      knownCert = (X509Certificate) tmp;
    } catch (final KeyStoreException e) {
      throw new CertificateException(
          "Could not look for existing known certificate in key store", e);
    }
    var knownCertValid = knownCert != null;
    try {
      if (knownCertValid) {
        knownCert.checkValidity();
      }
    } catch (final CertificateNotYetValidException e) {
      log.warn(
          "Known certificate for host {} ({}) is somehow not yet valid",
          host,
          Certificates.getFingerprint(knownCert));
      knownCertValid = false;
    } catch (final CertificateExpiredException e) {
      log.info(
          "Known certificate for host {} ({}) is expired",
          host,
          Certificates.getFingerprint(knownCert));
      knownCertValid = false;
    }

    if (!knownCertValid) {
      log.info(
          "Trusting new certificate for host {} with fingerprint {}",
          host,
          Certificates.getFingerprint(peerCert));
      try {
        keyStore.setCertificateEntry(host, peerCert);
      } catch (final KeyStoreException e) {
        throw new CertificateException("Could not store certificate for new host in key store", e);
      }
    } else if (!knownCert.equals(peerCert)) {
      throw new CertificateChangedException(host, peerCert, knownCert);
    }
  }

  /** A builder for {@link GeminiClient GeminiClients}. */
  public static final class Builder {
    private Executor executor;
    private SslContext sslContext;
    private KeyStore keyStore;

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
     * Sets the {@link SslContext} to use for the client.
     *
     * <p>If no {@link SslContext} is explicitly provided using this method, the client will create
     * a default context supporting TLS 1.2 and 1.3.
     *
     * @param sslContext the {@link SslContext} to use for the client
     * @return {@code this}
     */
    public Builder sslContext(final SslContext sslContext) {
      this.sslContext = requireNonNull(sslContext, "sslContext");
      return this;
    }

    /**
     * Sets the {@link KeyStore} in which to store trusted server certificates.
     *
     * <p>If no key store is explicitly provided using this method, the client will create a new key
     * store.
     *
     * @param keyStore the {@link KeyStore} in which to store trusted server certificates
     * @return {@code this}
     */
    public Builder keyStore(final KeyStore keyStore) {
      this.keyStore = requireNonNull(keyStore, "keyStore");
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
