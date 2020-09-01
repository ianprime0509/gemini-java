package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;
import java.net.URI;
import java.util.Optional;

@AutoValue
public abstract class GeminiLinkLine extends GeminiContent {
  GeminiLinkLine() {}

  public static GeminiLinkLine of(final URI uri) {
    return of(uri, null);
  }

  public static GeminiLinkLine of(final URI uri, @Nullable final String title) {
    return new AutoValue_GeminiLinkLine(uri, Optional.ofNullable(title));
  }

  public abstract URI uri();

  public abstract Optional<String> title();
}
