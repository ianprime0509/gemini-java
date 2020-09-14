package xyz.ianjohnson.gemini.server;

import static java.util.Collections.unmodifiableNavigableMap;
import static xyz.ianjohnson.gemini.server.GeminiRequest.normalizePath;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import xyz.ianjohnson.gemini.StandardGeminiStatus;

public final class Router implements Function<GeminiRequest, GeminiResponse> {
  private final Map<String, Function<GeminiRequest, GeminiResponse>> exactRoutes;
  private final NavigableMap<String, Function<GeminiRequest, GeminiResponse>> prefixRoutes;

  private Router(final Builder builder) {
    exactRoutes = Map.copyOf(builder.exactRoutes);
    prefixRoutes = unmodifiableNavigableMap(new TreeMap<>(builder.prefixRoutes));
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public GeminiResponse apply(final GeminiRequest request) {
    final var path = normalizePath(request.uri().getPath());

    final var exactMatch = exactRoutes.get(path);
    if (exactMatch != null) {
      return exactMatch.apply(request);
    }

    // Fast path: exact match on prefix
    final var exactPrefixMatch = prefixRoutes.get(path);
    if (exactPrefixMatch != null) {
      return exactPrefixMatch.apply(request);
    }

    // Special case: if the path does not end with a slash but there is a prefix that consists of
    // the path with a slash appended, perform a redirect
    if (!path.endsWith("/") && prefixRoutes.containsKey(path + "/")) {
      return GeminiResponse.of(StandardGeminiStatus.PERMANENT_REDIRECT, path + "/");
    }

    for (var entry = prefixRoutes.floorEntry(path);
        entry != null;
        entry = prefixRoutes.lowerEntry(entry.getKey())) {
      if (!path.startsWith(entry.getKey())) {
        continue;
      }

      if (!path.equals(request.uri().getPath())) {
        return GeminiResponse.of(StandardGeminiStatus.PERMANENT_REDIRECT, path);
      }
      return entry.getValue().apply(request);
    }
    return GeminiResponse.of(StandardGeminiStatus.NOT_FOUND, "Not found");
  }

  public static final class Builder {
    private final Map<String, Function<GeminiRequest, GeminiResponse>> exactRoutes =
        new HashMap<>();
    private final NavigableMap<String, Function<GeminiRequest, GeminiResponse>> prefixRoutes =
        new TreeMap<>();

    public Builder addExactRoute(
        String route, final Function<GeminiRequest, GeminiResponse> handler) {
      route = normalizePath(route);
      if (exactRoutes.containsKey(route)) {
        throw new IllegalStateException("Exact route already defined: " + route);
      }
      exactRoutes.put(route, handler);
      return this;
    }

    public Builder addPrefixRoute(
        String prefix, final Function<GeminiRequest, GeminiResponse> handler) {
      prefix = normalizePath(prefix);
      if (prefixRoutes.containsKey(prefix)) {
        throw new IllegalStateException("Prefix route already defined: " + prefix);
      }
      prefixRoutes.put(prefix, handler);
      return this;
    }

    public Router build() {
      return new Router(this);
    }
  }
}
