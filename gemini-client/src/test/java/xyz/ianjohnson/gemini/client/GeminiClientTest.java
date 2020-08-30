package xyz.ianjohnson.gemini.client;

import static java.util.Collections.enumeration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static xyz.ianjohnson.gemini.client.GeminiClient.GEMINI_PORT;
import static xyz.ianjohnson.gemini.client.TestUtils.mockSSLContext;
import static xyz.ianjohnson.gemini.client.TestUtils.utf8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.GeminiStatus;
import xyz.ianjohnson.gemini.MimeTypeSyntaxException;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandlers;

public class GeminiClientTest {
  private static final URI TEST_URI;

  static {
    try {
      TEST_URI = new URI("gemini://gemini.test:1965/path");
    } catch (final URISyntaxException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static ExecutorService executor;

  private SSLSocketFactory sslSocketFactory;
  private SSLSocket sslSocket;
  private ByteArrayOutputStream socketOutputStream;
  private GeminiClient client;

  @BeforeAll
  public static void setUpClass() {
    executor = Executors.newCachedThreadPool();
  }

  @AfterAll
  public static void tearDownClass() {
    executor.shutdownNow();
  }

  @BeforeEach
  public void setUp() throws IOException {
    sslSocketFactory = mock(SSLSocketFactory.class);
    sslSocket = mock(SSLSocket.class);
    socketOutputStream = new ByteArrayOutputStream();
    when(sslSocket.getOutputStream()).thenReturn(socketOutputStream);
    client =
        GeminiClient.newBuilder()
            .executor(executor)
            .sslContext(mockSSLContext(sslSocketFactory))
            .build();
  }

  @Test
  public void testSend_withUrlResultingInSuccess_returnsResponse() throws Exception {
    mockResponse(utf8("20 text/plain\r\nHello, world!\r\n"));

    assertThat(client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(GeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("text/plain");
              assertThat(response.body())
                  .hasValueSatisfying(
                      body -> assertThat(body).containsExactly(utf8("Hello, world!\r\n")));
            });
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withUrlResultingInError_returnsResponse() throws Exception {
    mockResponse(utf8("41 Server temporarily unavailable\r\n"));

    assertThat(client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(GeminiStatus.SERVER_UNAVAILABLE);
              assertThat(response.meta()).isEqualTo("Server temporarily unavailable");
              assertThat(response.body()).isEmpty();
            });
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withUrlMissingPort_usesDefaultPortAndReturnsResponse() throws Exception {
    final var uri = new URI("gemini://gemini.test");
    when(sslSocketFactory.createSocket(uri.getHost(), GEMINI_PORT)).thenReturn(sslSocket);
    when(sslSocket.getInputStream()).thenReturn(new ByteArrayInputStream(utf8("52 Gone\r\n")));

    assertThat(client.send(uri, BodyHandlers.discarding()))
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(uri);
              assertThat(response.status()).isEqualTo(GeminiStatus.GONE);
              assertThat(response.meta()).isEqualTo("Gone");
              assertThat(response.body()).isEmpty();
            });
    assertThat(socketOutputStream.toByteArray()).containsExactly(utf8(uri + "\r\n"));
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withHttpsScheme_throwsIllegalArgumentException() throws Exception {
    final var uri = new URI("https://gemini.test");
    assertThatThrownBy(() -> client.send(uri, BodyHandlers.discarding()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URI scheme must be gemini");
  }

  @Test
  public void testSend_withRelativeUri_throwsIllegalArgumentException() throws Exception {
    final var uri = new URI("relative/path");
    assertThatThrownBy(() -> client.send(uri, BodyHandlers.discarding()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URI missing host");
  }

  @Test
  public void testSend_withUnknownHost_throwsUnknownHostException() throws Exception {
    when(sslSocketFactory.createSocket(TEST_URI.getHost(), GEMINI_PORT))
        .thenThrow(new UnknownHostException("Host gemini.test not found"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOf(UnknownHostException.class)
        .hasMessage("Host gemini.test not found");
  }

  @Test
  public void testSend_withConnectionRefused_throwsConnectException() throws Exception {
    when(sslSocketFactory.createSocket(TEST_URI.getHost(), GEMINI_PORT))
        .thenThrow(new ConnectException("Connection refused"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOf(ConnectException.class)
        .hasMessage("Connection refused");
  }

  @Test
  public void testSend_withNoResponseContent_throwsMalformedResponseException() throws Exception {
    mockResponse(new byte[0]);

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Incomplete response header");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withTooLongResponseHeader_throwsMalformedResponseException()
      throws Exception {
    final var meta = new StringBuilder();
    for (var i = 0; i < 10000; i++) {
      meta.append((char) ((i % 10) + '0'));
    }
    mockResponse(utf8("40 " + meta + "\r\n"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Response header too long");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withMaximumLengthResponseHeader_returnsResponse() throws Exception {
    final var meta = new StringBuilder();
    for (var i = 0; i < 1024; i++) {
      meta.append((char) ((i % 10) + '0'));
    }
    mockResponse(utf8("40 " + meta + "\r\n"));

    assertThat(client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(GeminiStatus.TEMPORARY_FAILURE);
              assertThat(response.meta()).isEqualTo(meta.toString());
              assertThat(response.body()).isEmpty();
            });
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withMissingResponseStatus_throwsMalformedResponseException()
      throws Exception {
    mockResponse(utf8("Not found"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Invalid response status code");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withUnknownResponseStatus_throwsUnknownStatusCodeException()
      throws Exception {
    mockResponse(utf8("99 Invalid\r\n"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOfSatisfying(
            UnknownStatusCodeException.class, e -> assertThat(e.code()).isEqualTo(99))
        .hasMessageContaining("Unknown response status code: 99");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withResponseHeaderMissingTerminator_throwsMalformedResponseException()
      throws Exception {
    mockResponse(utf8("51 Not found"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Response header missing terminator");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withResponseHeaderUsingBareCarriageReturn_throwsMalformedResponseException()
      throws Exception {
    mockResponse(utf8("20 text/plain\rThis is invalid.\r"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Misplaced carriage return in response header");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withResponseHeaderUsingBareLineFeed_returnsResponse() throws Exception {
    mockResponse(utf8("20 text/plain;charset=utf-8\nThis is technically invalid."));

    assertThat(client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(GeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("text/plain;charset=utf-8");
              assertThat(response.body())
                  .hasValueSatisfying(
                      body ->
                          assertThat(body).containsExactly(utf8("This is technically invalid.")));
            });
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withSuccessResponseAndInvalidMimeType_throwsMalformedResponseException()
      throws Exception {
    mockResponse(utf8("200 text\r\n"));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.discarding()))
        .isInstanceOf(MalformedResponseException.class)
        .hasMessageContaining("Invalid MIME type")
        .hasCauseInstanceOf(MimeTypeSyntaxException.class);
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withSeveralReadChunksInHeaderAndBody_returnsResponse() throws Exception {
    final var bodyChunk1 = "chunk1".repeat(50);
    final var bodyChunk2 = "chunk2".repeat(75);
    when(sslSocketFactory.createSocket(TEST_URI.getHost(), TEST_URI.getPort()))
        .thenReturn(sslSocket);
    when(sslSocket.getInputStream())
        .thenReturn(
            new SequenceInputStream(
                enumeration(
                    List.of(
                        new ByteArrayInputStream(utf8("20 tex")),
                        new ByteArrayInputStream(utf8("t/plain\r")),
                        new ByteArrayInputStream(utf8("\n")),
                        new ByteArrayInputStream(utf8(bodyChunk1)),
                        new ByteArrayInputStream(utf8(bodyChunk2))))));

    assertThat(client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .satisfies(
            response -> {
              assertThat(response.uri()).isEqualTo(TEST_URI);
              assertThat(response.status()).isEqualTo(GeminiStatus.SUCCESS);
              assertThat(response.meta()).isEqualTo("text/plain");
              assertThat(response.body())
                  .hasValueSatisfying(
                      body -> assertThat(body).containsExactly(utf8(bodyChunk1 + bodyChunk2)));
            });
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withReadFailureInHeader_throwsIOException() throws Exception {
    final var initialInput = new ByteArrayInputStream(utf8("20 tex"));
    final var failInput =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Socket read failed");
          }
        };
    when(sslSocketFactory.createSocket(TEST_URI.getHost(), TEST_URI.getPort()))
        .thenReturn(sslSocket);
    when(sslSocket.getInputStream()).thenReturn(new SequenceInputStream(initialInput, failInput));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .isInstanceOf(IOException.class)
        .hasMessage("Socket read failed");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  @Test
  public void testSend_withReadFailureInBody_throwsIOException() throws Exception {
    final var initialInput = new ByteArrayInputStream(utf8("20 text/plain\r\nHello, wor"));
    final var failInput =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Socket read failed");
          }
        };
    when(sslSocketFactory.createSocket(TEST_URI.getHost(), TEST_URI.getPort()))
        .thenReturn(sslSocket);
    when(sslSocket.getInputStream()).thenReturn(new SequenceInputStream(initialInput, failInput));

    assertThatThrownBy(() -> client.send(TEST_URI, BodyHandlers.ofByteArray()))
        .isInstanceOf(IOException.class)
        .hasMessage("Socket read failed");
    assertCorrectRequest();
    verify(sslSocket).close();
  }

  private void mockResponse(final byte[] response) throws IOException {
    when(sslSocketFactory.createSocket(TEST_URI.getHost(), TEST_URI.getPort()))
        .thenReturn(sslSocket);
    when(sslSocket.getInputStream()).thenReturn(new ByteArrayInputStream(response));
  }

  private void assertCorrectRequest() {
    assertThat(socketOutputStream.toByteArray()).containsExactly(utf8(TEST_URI + "\r\n"));
  }
}
