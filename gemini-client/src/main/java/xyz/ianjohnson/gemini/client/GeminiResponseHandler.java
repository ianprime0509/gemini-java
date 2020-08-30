package xyz.ianjohnson.gemini.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import xyz.ianjohnson.gemini.GeminiStatus;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.MimeTypeSyntaxException;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandler;

final class GeminiResponseHandler<T> extends ChannelInboundHandlerAdapter {
  private static final int MAX_META_LENGTH = 1024;
  // Two bytes for status, one byte for space, two bytes for CRLF
  private static final int MAX_HEADER_LENGTH = MAX_META_LENGTH + 5;
  private static final int BUFFER_SIZE = 8192;

  private final URI uri;
  private final BodyHandler<T> bodyHandler;
  private final CompletableFuture<GeminiResponse<T>> future;
  private SubmissionPublisher<List<ByteBuffer>> bodyPublisher;
  private ByteBuf buf;

  GeminiResponseHandler(
      final URI uri,
      final BodyHandler<T> bodyHandler,
      final CompletableFuture<GeminiResponse<T>> future) {
    this.uri = uri;
    this.bodyHandler = bodyHandler;
    this.future = future;
  }

  @Override
  public void handlerAdded(final ChannelHandlerContext ctx) {
    buf = ctx.alloc().buffer(BUFFER_SIZE);
  }

  @Override
  public void handlerRemoved(final ChannelHandlerContext ctx) {
    buf.release();
    buf = null;
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
    final var msgBuf = (ByteBuf) msg;
    buf.writeBytes(msgBuf);
    msgBuf.release();
    if (buf.readableBytes() >= BUFFER_SIZE) {
      handleBufferContents(ctx);
    }
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
    handleBufferContents(ctx);
    if (bodyPublisher != null) {
      bodyPublisher.close();
    }
    super.channelInactive(ctx);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    if (bodyPublisher != null) {
      bodyPublisher.closeExceptionally(cause);
    } else {
      future.completeExceptionally(cause);
    }
    ctx.close();
  }

  private void handleBufferContents(final ChannelHandlerContext ctx)
      throws MalformedResponseException {
    if (bodyPublisher == null) {
      readHeader(ctx);
    } else {
      readBodyChunk();
    }
  }

  private void readHeader(final ChannelHandlerContext ctx) throws MalformedResponseException {
    // The header will include everything up to and including the line feed
    final var headerLen =
        buf.bytesBefore(
                Math.min(MAX_HEADER_LENGTH, buf.writerIndex() - buf.readerIndex()), (byte) '\n')
            + 1;
    if (headerLen == 0) {
      if (buf.readableBytes() < MAX_HEADER_LENGTH) {
        throw new MalformedResponseException("Response header missing terminator");
      }
      throw new MalformedResponseException("Response header too long");
    }

    final var headerBuf = buf.slice(buf.readerIndex(), headerLen);
    buf.skipBytes(headerLen);
    if (headerBuf.readableBytes() < 3) {
      throw new MalformedResponseException("Incomplete response header");
    }

    final var codeB1 = headerBuf.readByte();
    final var codeB2 = headerBuf.readByte();
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

    if (headerBuf.getByte(headerBuf.readerIndex()) == ' ') {
      headerBuf.skipBytes(1);
    }
    final var untilCr = headerBuf.bytesBefore((byte) '\r');
    final byte[] metaBytes;
    // We intentionally allow the header line to be terminated with only a line feed
    if (untilCr != -1) {
      if (headerBuf.getByte(headerBuf.readerIndex() + untilCr + 1) != '\n') {
        throw new MalformedResponseException("Misplaced carriage return in response header");
      }
      metaBytes = new byte[untilCr];
    } else {
      metaBytes = new byte[headerBuf.readableBytes() - 1];
    }
    headerBuf.readBytes(metaBytes);
    final var meta = new String(metaBytes, StandardCharsets.UTF_8);

    startBody(ctx, status, meta);
  }

  private void startBody(
      final ChannelHandlerContext ctx, final GeminiStatus status, final String meta)
      throws MalformedResponseException {
    final var responseBuilder = GeminiResponse.<T>newBuilder().uri(uri).status(status).meta(meta);
    if (status != GeminiStatus.SUCCESS) {
      future.complete(responseBuilder.build());
      ctx.close();
      return;
    }

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
                future.complete(responseBuilder.body(body).build());
              } else {
                future.completeExceptionally(e);
              }
              ctx.close();
            });
    bodyPublisher = new SubmissionPublisher<>(future.defaultExecutor(), Flow.defaultBufferSize());
    bodyPublisher.subscribe(subscriber);
    readBodyChunk();
  }

  private void readBodyChunk() {
    final var chunk = ByteBuffer.allocate(buf.readableBytes());
    buf.readBytes(chunk);
    buf.discardReadBytes();
    bodyPublisher.submit(List.of(chunk.flip().asReadOnlyBuffer()));
  }
}
