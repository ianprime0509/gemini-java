package xyz.ianjohnson.gemini.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.TooLongFrameException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import xyz.ianjohnson.gemini.GeminiStatus;
import xyz.ianjohnson.gemini.GeminiStatus.Kind;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.MimeTypeSyntaxException;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandler;

final class GeminiResponseDecoder<T> extends DelimiterBasedFrameDecoder {
  private static final int MAX_META_LENGTH = 1024;
  // Two bytes for status, one byte for space, two bytes for CRLF
  private static final int MAX_HEADER_LENGTH = MAX_META_LENGTH + 5;

  private final URI uri;
  private final BodyHandler<T> bodyHandler;
  private final CompletableFuture<GeminiResponse<T>> future;

  public GeminiResponseDecoder(
      final URI uri,
      final BodyHandler<T> bodyHandler,
      final CompletableFuture<GeminiResponse<T>> future) {
    super(MAX_HEADER_LENGTH, true, true, Delimiters.lineDelimiter());
    this.uri = uri;
    this.bodyHandler = bodyHandler;
    this.future = future;
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) throws MalformedResponseException {
    throw new MalformedResponseException("Response header missing terminator");
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    if (cause instanceof TooLongFrameException) {
      future.completeExceptionally(new MalformedResponseException("Response header too long"));
    } else if (cause instanceof DecoderException && cause.getCause() != null) {
      future.completeExceptionally(cause.getCause());
    } else {
      future.completeExceptionally(cause);
    }
    ctx.close();
  }

  @Override
  protected Object decode(final ChannelHandlerContext ctx, final ByteBuf buffer) throws Exception {
    final var header = (ByteBuf) super.decode(ctx, buffer);
    if (header == null) {
      return null;
    }

    if (header.readableBytes() < 2) {
      throw new MalformedResponseException("Incomplete response header");
    }

    final var codeB1 = header.readByte();
    final var codeB2 = header.readByte();
    if (codeB1 < '0' || codeB1 > '9' || codeB2 < '0' || codeB2 > '9') {
      throw new MalformedResponseException("Invalid response status code");
    }
    final var code = 10 * (codeB1 - '0') + (codeB2 - '0');
    final GeminiStatus status;
    try {
      status = GeminiStatus.valueOf(code);
    } catch (final IllegalArgumentException e) {
      throw new UnknownStatusCodeException(code);
    }

    if (header.readableBytes() > 0 && header.getByte(header.readerIndex()) == ' ') {
      header.skipBytes(1);
    }
    if (header.bytesBefore((byte) '\r') != -1) {
      throw new MalformedResponseException("Misplaced carriage return in response header");
    }
    final var metaBytes = new byte[header.readableBytes()];
    header.readBytes(metaBytes);
    final var meta = new String(metaBytes, StandardCharsets.UTF_8);

    final var responseBuilder = GeminiResponse.<T>newBuilder().uri(uri).status(status).meta(meta);
    if (status.kind() == Kind.SUCCESS) {
      final MimeType mimeType;
      try {
        mimeType = MimeType.parse(meta);
      } catch (final MimeTypeSyntaxException e) {
        throw new MalformedResponseException("Invalid MIME type", e);
      }
      final var subscriber = bodyHandler.apply(mimeType);
      subscriber
          .getBody()
          .whenComplete(
              (body, e) -> {
                if (e == null) {
                  future.complete(
                      body != null ? responseBuilder.body(body).build() : responseBuilder.build());
                } else {
                  future.completeExceptionally(e);
                }
                ctx.close();
              });
      ctx.pipeline()
          .replace(this, ctx.name(), new GeminiBodyDecoder(subscriber, future.defaultExecutor()));
    } else {
      future.complete(responseBuilder.build());
      ctx.close();
    }

    return null;
  }
}
