package xyz.ianjohnson.gemini.server;

import com.google.auto.value.AutoValue;
import java.net.SocketAddress;
import java.net.URI;

@AutoValue
public abstract class GeminiRequest {
  GeminiRequest() {}

  public static Builder newBuilder() {
    return new AutoValue_GeminiRequest.Builder();
  }

  public abstract SocketAddress localAddress();

  public abstract SocketAddress remoteAddress();

  public abstract URI uri();

  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder localAddress(SocketAddress localAddress);

    public abstract Builder remoteAddress(SocketAddress remoteAddress);

    public abstract Builder uri(URI uri);

    public abstract GeminiRequest build();
  }
}
