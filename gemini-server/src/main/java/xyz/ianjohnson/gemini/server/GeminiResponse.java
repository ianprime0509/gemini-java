package xyz.ianjohnson.gemini.server;

import com.google.auto.value.AutoValue;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow.Publisher;
import xyz.ianjohnson.gemini.GeminiStatus;
import xyz.ianjohnson.gemini.server.BodyPublisherImpls.Empty;
import xyz.ianjohnson.gemini.server.BodyPublisherImpls.OfByteArray;

@AutoValue
public abstract class GeminiResponse {
  GeminiResponse() {}

  public static GeminiResponse of(final GeminiStatus status, final String meta) {
    return of(status, meta, BodyPublishers.empty());
  }

  public static GeminiResponse of(
      final GeminiStatus status, final String meta, final Publisher<ByteBuffer> bodyPublisher) {
    return new AutoValue_GeminiResponse(status, meta, bodyPublisher);
  }

  public abstract GeminiStatus status();

  public abstract String meta();

  public abstract Publisher<ByteBuffer> bodyPublisher();

  public static final class BodyPublishers {
    private BodyPublishers() {}

    public static Publisher<ByteBuffer> empty() {
      return Empty.INSTANCE;
    }

    public static Publisher<ByteBuffer> ofByteArray(final byte[] bytes) {
      return new OfByteArray(bytes);
    }

    public static Publisher<ByteBuffer> ofString(final String s) {
      return ofString(s, StandardCharsets.UTF_8);
    }

    public static Publisher<ByteBuffer> ofString(final String s, final Charset charset) {
      return ofByteArray(s.getBytes(charset));
    }
  }
}
