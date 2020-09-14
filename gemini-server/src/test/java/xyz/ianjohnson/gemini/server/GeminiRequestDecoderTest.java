package xyz.ianjohnson.gemini.server;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static xyz.ianjohnson.gemini.server.TestUtils.utf8;

import io.netty.channel.embedded.EmbeddedChannel;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.StandardGeminiStatus;

public class GeminiRequestDecoderTest {
  private EmbeddedChannel channel;

  @BeforeEach
  public void setUp() {
    channel = new EmbeddedChannel(new GeminiRequestDecoder());
  }

  @Test
  public void testDecode_withValidRequest_decodesRequest() {
    channel.writeInbound(wrappedBuffer(utf8("gemini://gemini.example\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readOutbound()).isNull();
    assertThat(channel.<Object>readInbound())
        .isInstanceOfSatisfying(
            GeminiRequest.class,
            request -> assertThat(request.uri()).isEqualTo(URI.create("gemini://gemini.example")));
  }

  @Test
  public void testDecode_withValidRequestTerminatedWithOnlyLineFeed_decodesRequest() {
    channel.writeInbound(wrappedBuffer(utf8("gemini://gemini.example\n")));
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readOutbound()).isNull();
    assertThat(channel.<Object>readInbound())
        .isInstanceOfSatisfying(
            GeminiRequest.class,
            request -> assertThat(request.uri()).isEqualTo(URI.create("gemini://gemini.example")));
  }

  @Test
  public void testDecode_withValidRequestAtMaximumLength_decodesRequest() {
    channel.writeInbound(wrappedBuffer(utf8("//" + "A".repeat(1022) + "\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readOutbound()).isNull();
    assertThat(channel.<Object>readInbound())
        .isInstanceOfSatisfying(
            GeminiRequest.class,
            request -> assertThat(request.uri()).isEqualTo(URI.create("//" + "A".repeat(1022))));
  }

  @Test
  public void testDecode_withInvalidRequestUri_returnsBadRequestResponse() {
    channel.writeInbound(wrappedBuffer(utf8("://\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readInbound()).isNull();
    assertThat(channel.<Object>readOutbound())
        .isInstanceOfSatisfying(
            GeminiResponse.class,
            response -> {
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.BAD_REQUEST);
              assertThat(response.meta()).startsWith("Invalid request URI");
            });
  }

  @Test
  public void testDecode_withRequestUriMissingHost_returnsBadRequestResponse() {
    channel.writeInbound(wrappedBuffer(utf8("invalid\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readInbound()).isNull();
    assertThat(channel.<Object>readOutbound())
        .isInstanceOfSatisfying(
            GeminiResponse.class,
            response -> {
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.BAD_REQUEST);
              assertThat(response.meta()).isEqualTo("Invalid request URI: Host is required");
            });
  }

  @Test
  public void testDecode_withTooLongRequestUri_returnsBadRequestResponse() {
    channel.writeInbound(wrappedBuffer(utf8("A".repeat(5000) + "\r\n")));
    channel.finish();
    channel.checkException();

    assertThat(channel.<Object>readInbound()).isNull();
    assertThat(channel.<Object>readOutbound())
        .isInstanceOfSatisfying(
            GeminiResponse.class,
            response -> {
              assertThat(response.status()).isEqualTo(StandardGeminiStatus.BAD_REQUEST);
              assertThat(response.meta()).isEqualTo("Request URI too long");
            });
  }
}
