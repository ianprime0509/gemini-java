package xyz.ianjohnson.gemini.client;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xyz.ianjohnson.gemini.client.TestUtils.utf8;

import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.GeminiStatus.Kind;
import xyz.ianjohnson.gemini.MimeTypeSyntaxException;
import xyz.ianjohnson.gemini.StandardGeminiStatus;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandlers;

public class GeminiResponseDecoderTest {
  private static final int TIMEOUT_MILLISECONDS = 1000;
  private static final URI TEST_URI;

  static {
    try {
      TEST_URI = new URI("gemini://gemini.test");
    } catch (final URISyntaxException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private CompletableFuture<GeminiResponse<byte[]>> future;
  private EmbeddedChannel channel;

  @BeforeEach
  public void setUp() {
    future = new CompletableFuture<>();
    channel =
        new EmbeddedChannel(
            new GeminiResponseDecoder<>(TEST_URI, BodyHandlers.ofByteArray(), future));
  }

  @Test
  public void testHandler_withSuccessfulResponse_returnsResponse() throws Throwable {
    channel.writeInbound(wrappedBuffer(utf8("20 text/plain\r\nHello, world!\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("text/plain");
              assertThat(response.body())
                  .hasValueSatisfying(
                      body -> assertThat(body).containsExactly(utf8("Hello, world!\r\n")));
            });
  }

  @Test
  public void testHandler_withSuccessfulResponseAndLongBodyInManyChunks_returnsResponse()
      throws Throwable {
    channel.writeInbound(wrappedBuffer(utf8("20 text/plain;charset=utf-8\r\n")));
    for (var i = 0; i < 10000; i++) {
      channel.writeInbound(wrappedBuffer(utf8("Line of text\n")));
    }
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("text/plain;charset=utf-8");
              final var expectedBody = utf8("Line of text\n".repeat(10000));
              assertThat(response.body())
                  .hasValueSatisfying(
                      body ->
                          assertThat(body)
                              .withFailMessage(
                                  "Expected body to contain %d bytes, but contained %d",
                                  expectedBody.length, body.length)
                              .containsExactly(expectedBody));
            });
  }

  @Test
  public void testHandler_withSuccessfulResponseAndBinaryBody_returnsResponse() throws Throwable {
    final var responseBody = new byte[1024];
    for (var i = 0; i < responseBody.length; i++) {
      responseBody[i] = (byte) i;
    }

    channel.writeInbound(wrappedBuffer(utf8("20 application/octet-stream\r\n")));
    channel.writeInbound(wrappedBuffer(responseBody));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("application/octet-stream");
              assertThat(response.body())
                  .hasValueSatisfying(body -> assertThat(body).containsExactly(responseBody));
            });
  }

  @Test
  public void testHandler_withBodyHandlerReturningNull_returnsResponseWithNoBody()
      throws Throwable {
    final var future = new CompletableFuture<GeminiResponse<Void>>();
    final var channel =
        new EmbeddedChannel(
            new GeminiResponseDecoder<>(TEST_URI, BodyHandlers.discarding(), future));
    channel.writeInbound(wrappedBuffer(utf8("20 text/plain\r\nThis body will be ignored.\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse(future))
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("text/plain");
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withNonSuccessfulResponse_returnsResponse() throws Throwable {
    channel.writeInbound(wrappedBuffer(utf8("51 Not found\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.NOT_FOUND);
              assertThat(response.meta()).isEqualTo("Not found");
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withNoResponse_throwsMalformedResponseException() {
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Response header missing terminator");
  }

  @Test
  public void
      testHandler_withImmediatelyTerminatedResponseHeader_throwsMalformedResponseException() {
    channel.writeInbound(wrappedBuffer(utf8("\r\n")));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Incomplete response header");
  }

  @Test
  public void testHandler_withOnlyStatusCodeAndNoMetaInHeader_returnsResponse() throws Throwable {
    // This is technically invalid but permitted anyways
    channel.writeInbound(wrappedBuffer(utf8("50\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.PERMANENT_FAILURE);
              assertThat(response.meta()).isEqualTo("");
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withEmptyMetaInHeader_returnsResponse() throws Throwable {
    // I believe this complies with the standard, although it's very unhelpful to the user
    channel.writeInbound(wrappedBuffer(utf8("50 \r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.PERMANENT_FAILURE);
              assertThat(response.meta()).isEqualTo("");
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withMetaContainingSurroundingWhitespace_returnsResponse()
      throws Throwable {
    channel.writeInbound(wrappedBuffer(utf8("60    ===NO===   \r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status())
                  .isEqualTo(StandardGeminiStatus.CLIENT_CERTIFICATE_REQUIRED);
              assertThat(response.meta()).isEqualTo("   ===NO===   ");
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withMetaContainingNonAsciiCharacters_returnsResponse() throws Throwable {
    channel.writeInbound(wrappedBuffer(utf8("51 エラー Document non trouvé\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.NOT_FOUND);
              assertThat(response.meta()).isEqualTo("エラー Document non trouvé");
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withTooLongResponseHeader_throwsMalformedResponseException() {
    channel.writeInbound(wrappedBuffer(utf8("41 " + "A".repeat(10000))));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Response header too long");
  }

  @Test
  public void testHandler_withMaximumLengthResponseHeader_returnsResponse() throws Throwable {
    channel.writeInbound(wrappedBuffer(utf8("40 " + "A".repeat(1024) + "\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.TEMPORARY_FAILURE);
              assertThat(response.meta()).isEqualTo("A".repeat(1024));
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withMissingResponseStatus_throwsMalformedResponseException() {
    channel.writeInbound(wrappedBuffer(utf8("Not found\r\n")));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Invalid response status code");
  }

  @Test
  public void testHandler_withUnknownResponseStatusKind_throwsUnknownStatusCodeException() {
    channel.writeInbound(wrappedBuffer(utf8("99 Invalid\r\n")));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOfSatisfying(
            UnknownStatusCodeException.class, e -> assertThat(e.code()).isEqualTo(99))
        .hasMessageContaining("Unknown response status code: 99");
  }

  @Test
  public void testHandler_withNonStandardStatusCode_returnsResponse() throws Throwable {
    channel.writeInbound(wrappedBuffer(utf8("49 Temporary failure\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status().kind()).isEqualTo(Kind.TEMPORARY_FAILURE);
              assertThat(response.status().code()).isEqualTo(49);
              assertThat(response.meta()).isEqualTo("Temporary failure");
              assertThat(response.body()).isEmpty();
            });
  }

  @Test
  public void testHandler_withResponseHeaderMissingTerminator_throwsMalformedResponseException() {
    channel.writeInbound(wrappedBuffer(utf8("51 Not found")));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Response header missing terminator");
  }

  @Test
  public void
      testHandler_withResponseHeaderUsingBareCarriageReturn_throwsMalformedResponseException() {
    channel.writeInbound(wrappedBuffer(utf8("20 text/plain\rThis is invalid.\r")));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Response header missing terminator");
  }

  @Test
  public void
      testHandler_withResponseHeaderUsingBareCarriageReturnOutsideOfTerminator_throwsMalformedResponseException() {
    channel.writeInbound(wrappedBuffer(utf8("40 Bad\ridea\n")));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Misplaced carriage return in response header");
  }

  @Test
  public void testHandler_withResponseHeaderUsingBareLineFeed_returnsResponse() throws Throwable {
    channel.writeInbound(
        wrappedBuffer(utf8("20 text/plain;charset=utf-8\nThis is technically invalid.")));
    channel.finish();
    channel.checkException();

    assertThat(getResponse())
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("text/plain;charset=utf-8");
              assertThat(response.body())
                  .hasValueSatisfying(
                      body ->
                          assertThat(body).containsExactly(utf8("This is technically invalid.")));
            });
  }

  @Test
  public void testHandler_withSuccessResponseAndInvalidMimeType_throwsMalformedResponseException() {
    channel.writeInbound(wrappedBuffer(utf8("200 text\r\n")));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Invalid MIME type")
        .hasCauseInstanceOf(MimeTypeSyntaxException.class);
  }

  @Test
  public void testHandler_withIOExceptionInHeader_rethrowsIOException() {
    channel.writeInbound(wrappedBuffer(utf8("41 Server is dea")));
    channel.pipeline().fireExceptionCaught(new IOException("Socket read error"));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Socket read error");
  }

  @Test
  public void testHandler_withIOExceptionInBody_rethrowsIOException() {
    channel.writeInbound(wrappedBuffer(utf8("20 text/gemini\r\n")));
    for (var i = 0; i < 1000; i++) {
      channel.writeInbound(wrappedBuffer(utf8("Hello, world!\n")));
    }
    channel.pipeline().fireExceptionCaught(new IOException("Socket read error"));
    channel.finish();
    channel.checkException();

    assertThatThrownBy(this::getResponse)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Socket read error");
  }

  private GeminiResponse<byte[]> getResponse() throws Throwable {
    return getResponse(future);
  }

  private <T> GeminiResponse<T> getResponse(final CompletableFuture<GeminiResponse<T>> future)
      throws Throwable {
    try {
      return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
    } catch (final ExecutionException e) {
      throw e.getCause();
    }
  }
}
