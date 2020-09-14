package xyz.ianjohnson.gemini.server;

import com.google.auto.value.AutoValue;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import xyz.ianjohnson.gemini.Nullable;

@AutoValue
public abstract class GeminiRequest {
  GeminiRequest() {}

  public static Builder newBuilder() {
    return new AutoValue_GeminiRequest.Builder();
  }

  /**
   * Normalizes the given URI path by removing empty segments and resolving {@code .} and {@code ..}
   * relative references.
   *
   * @param path the path to normalize. The path is assumed to be absolute: if no leading {@code /}
   *     is present, one will be prepended to the resulting path (unless the path is empty). {@code
   *     null} is treated as an empty path.
   * @return the normalized path. The normalized path ends in {@code /} if and only if the original
   *     path does.
   */
  public static String normalizePath(@Nullable final String path) {
    if (path == null || path.isEmpty()) {
      return "";
    }

    final var comps = new ArrayList<String>(8);
    for (final var comp : path.split("/")) {
      if ("..".equals(comp)) {
        if (!comps.isEmpty()) {
          comps.remove(comps.size() - 1);
        }
      } else if (!".".equals(comp) && !comp.isEmpty()) {
        comps.add(comp);
      }
    }
    if (path.endsWith("/")) {
      comps.add("");
    }
    return "/" + String.join("/", comps);
  }

  /** The local (server) address that received the request. */
  public abstract SocketAddress localAddress();

  /** The remote (client) address that sent the request. */
  public abstract SocketAddress remoteAddress();

  /** The requested URI. */
  public abstract URI uri();

  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder localAddress(SocketAddress localAddress);

    public abstract Builder remoteAddress(SocketAddress remoteAddress);

    public abstract Builder uri(URI uri);

    public final GeminiRequest build() {
      if (uri().getHost() == null) {
        throw new IllegalArgumentException("Host is required");
      } else if (uri().getUserInfo() != null) {
        throw new IllegalArgumentException("Userinfo is not allowed");
      }
      return autoBuild();
    }

    abstract URI uri();

    abstract GeminiRequest autoBuild();
  }
}
