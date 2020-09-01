package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;
import java.util.Optional;

@AutoValue
public abstract class GeminiPreformattedText extends GeminiContent {
  GeminiPreformattedText() {}

  public static GeminiPreformattedText of(final String text) {
    return of(text, null);
  }

  public static GeminiPreformattedText of(final String text, @Nullable final String altText) {
    return new AutoValue_GeminiPreformattedText(text, Optional.ofNullable(altText));
  }

  public abstract String text();

  public abstract Optional<String> altText();
}
