package xyz.ianjohnson.gemini.server;

import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeminiServer implements Closeable {
  private static final int GEMINI_PORT = 1965;
  private static final Logger log = LoggerFactory.getLogger(GeminiServer.class);

  private final Function<GeminiRequest, GeminiResponse> handler;
  private final Executor executor;
  private final boolean userProvidedExecutor;
  private final EventLoopGroup bossEventLoopGroup;
  private final EventLoopGroup workerEventLoopGroup;
  private final SslContext sslContext;
  private final AtomicBoolean started = new AtomicBoolean();

  private Channel serverChannel;

  private GeminiServer(final Builder builder) {
    this.handler = builder.handler;
    if (builder.executor != null) {
      executor = builder.executor;
      userProvidedExecutor = true;
    } else {
      executor = Executors.newCachedThreadPool();
      userProvidedExecutor = false;
    }
    bossEventLoopGroup = new NioEventLoopGroup(0, executor);
    workerEventLoopGroup = new NioEventLoopGroup(0, executor);
    try {
      sslContext =
          SslContextBuilder.forServer(builder.keyManagerFactory)
              .protocols("TLSv1.2", "TLSv1.3")
              .trustManager(InsecureTrustManagerFactory.INSTANCE)
              .build();
    } catch (final SSLException e) {
      throw new IllegalStateException("Failed to construct SslContext", e);
    }
  }

  public static Builder newBuilder(
      final Function<GeminiRequest, GeminiResponse> handler,
      final KeyManagerFactory keyManagerFactory) {
    return new Builder(handler, keyManagerFactory);
  }

  public CompletableFuture<Void> start() {
    if (started.getAndSet(true)) {
      throw new IllegalStateException("Server already started");
    }

    final var future = new FutureImpl<Void>();
    new ServerBootstrap()
        .group(bossEventLoopGroup, workerEventLoopGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(final SocketChannel ch) {
                log.atInfo()
                    .addKeyValue("remoteAddress", ch.remoteAddress())
                    .addKeyValue("localAddress", ch.localAddress())
                    .log("Connection received");
                ch.pipeline()
                    .addLast("ssl", sslContext.newHandler(ch.alloc()))
                    .addLast(new GeminiRequestDecoder())
                    .addLast(new GeminiResponseHandler())
                    .addLast(new GeminiRequestHandler(handler));
                ch.closeFuture()
                    .addListener(
                        f -> {
                          if (f.isSuccess()) {
                            log.atInfo()
                                .addKeyValue("remoteAddress", ch.remoteAddress())
                                .addKeyValue("localAddress", ch.localAddress())
                                .log("Connection closed");
                          } else {
                            log.atError()
                                .setCause(f.cause())
                                .addKeyValue("remoteAddress", ch.remoteAddress())
                                .addKeyValue("localAddress", ch.localAddress())
                                .log("Connection error");
                          }
                        });
              }
            })
        .bind(GEMINI_PORT)
        .addListener(
            (final ChannelFuture f) -> {
              if (f.isSuccess()) {
                serverChannel = f.channel();
                future.complete(null);
              } else {
                future.completeExceptionally(f.cause());
              }
            });
    return future;
  }

  @Override
  public void close() {
    if (serverChannel == null) {
      throw new IllegalStateException("Server not running");
    }

    serverChannel
        .close()
        .addListener(
            f -> {
              workerEventLoopGroup.shutdownGracefully();
              bossEventLoopGroup.shutdownGracefully();
            });
  }

  public CompletableFuture<Void> closeFuture() {
    if (serverChannel == null) {
      throw new IllegalStateException("Server not running");
    }

    final var future = new FutureImpl<Void>();
    serverChannel
        .closeFuture()
        .addListener(
            f -> {
              if (f.isSuccess()) {
                future.complete(null);
              } else {
                future.completeExceptionally(f.cause());
              }
            });
    return future;
  }

  /**
   * Returns the {@link Executor} configured with this server, if one was provided by the user.
   *
   * @return the user-provided {@link Executor} for use with this server
   */
  public Optional<Executor> executor() {
    return userProvidedExecutor ? Optional.of(executor) : Optional.empty();
  }

  public static final class Builder {
    private final Function<GeminiRequest, GeminiResponse> handler;
    private final KeyManagerFactory keyManagerFactory;
    private Executor executor;

    private Builder(
        final Function<GeminiRequest, GeminiResponse> handler,
        final KeyManagerFactory keyManagerFactory) {
      this.handler = requireNonNull(handler, "handler");
      this.keyManagerFactory = requireNonNull(keyManagerFactory, "keyManagerFactory");
    }

    /**
     * Sets the {@link Executor} to use for handling asynchronous tasks.
     *
     * <p>If no executor is explicitly provided using this method, the server will use an internal
     * default executor.
     *
     * @param executor the {@link Executor} to use for handling asynchronous tasks
     * @return {@code this}
     */
    public Builder executor(final Executor executor) {
      this.executor = requireNonNull(executor, "executor");
      return this;
    }

    public GeminiServer build() {
      return new GeminiServer(this);
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
