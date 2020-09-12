package xyz.ianjohnson.gemini.client;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xyz.ianjohnson.gemini.client.TestUtils.utf8;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GeminiRequestEncoderTest {
  private EmbeddedChannel channel;

  @BeforeEach
  public void setUp() {
    channel = new EmbeddedChannel(new GeminiRequestEncoder());
  }

  @Test
  public void testEncoder_withUri_writesEncodedUri() {
    channel.writeOutbound(URI.create("gemini://gemini.example/test"));
    channel.finish();
    channel.checkException();
    assertThat(channel.<Object>readOutbound())
        .isEqualTo(wrappedBuffer(utf8("gemini://gemini.example/test\r\n")));
  }

  @Test
  public void testEncoder_withByteBuf_throwsUnsupportedMessageTypeException() {
    assertThatThrownBy(
            () -> {
              channel.writeOutbound(
                  wrappedBuffer("gemini://gemini.example".getBytes(StandardCharsets.UTF_8)));
              channel.finish();
              channel.checkException();
            })
        .isInstanceOf(UnsupportedMessageTypeException.class);
  }
}
