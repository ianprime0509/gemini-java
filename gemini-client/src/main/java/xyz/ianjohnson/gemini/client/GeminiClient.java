package xyz.ianjohnson.gemini.client;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static java.util.Objects.requireNonNull;

import io.netty.bootstrap.Bootstrap;
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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandler;

/**
 * A Gemini client.
 *
 * <p>The API of this client closely mirrors that of Java's native HttpClient.
 */
public final class GeminiClient implements Closeable {
  static final String GEMINI_SCHEME = "gemini";
  static final int GEMINI_PORT = 1965;

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

    if (builder.sslContext != null) {
      sslContext = builder.sslContext;
    } else {
      try {
        sslContext =
            SslContextBuilder.forClient()
                .protocols("TLSv1.2", "TLSv1.3")
                .trustManager(new GeminiTrustManager())
                .build();
      } catch (final SSLException e) {
        throw new IllegalStateException("Failed to construct SslContext", e);
      }
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static GeminiClient newGeminiClient() {
    return newBuilder().build();
  }

  @Override
  public void close() {
    eventLoopGroup.shutdownGracefully();
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
    final var bootstrap =
        new Bootstrap()
            .group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(
                new ChannelInitializer<SocketChannel>() {
                  @Override
                  protected void initChannel(final SocketChannel ch) {
                    ch.pipeline()
                        .addLast(sslContext.newHandler(ch.alloc(), uri.getHost(), port))
                        .addLast(new GeminiResponseHandler<>(uri, responseBodyHandler, future));
                  }
                });

    final var channelFuture = bootstrap.connect(uri.getHost(), port);
    channelFuture.addListener(
        channelReady -> {
          if (!channelFuture.isSuccess()) {
            future.completeExceptionally(channelFuture.cause());
            return;
          }

          final var channel = channelFuture.channel();
          future.whenComplete((response, e) -> channel.close());
          final var writeFuture =
              channel.writeAndFlush(wrappedBuffer((uri + "\r\n").getBytes(StandardCharsets.UTF_8)));
          writeFuture.addListener(
              writeDone -> {
                if (!writeFuture.isSuccess()) {
                  future.completeExceptionally(writeFuture.cause());
                }
              });
        });

    return future;
  }

  public Optional<Executor> executor() {
    return userProvidedExecutor ? Optional.of(executor) : Optional.empty();
  }

  public SslContext sslContext() {
    return sslContext;
  }

  public static final class Builder {
    private Executor executor;
    private SslContext sslContext;

    private Builder() {}

    public Builder executor(final Executor executor) {
      this.executor = requireNonNull(executor, "executor");
      return this;
    }

    public Builder sslContext(final SslContext sslContext) {
      this.sslContext = requireNonNull(sslContext, "sslContext");
      return this;
    }

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
