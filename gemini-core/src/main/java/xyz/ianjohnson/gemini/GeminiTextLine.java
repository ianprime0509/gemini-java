package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class GeminiTextLine extends GeminiContent {
  GeminiTextLine() {}

  public static GeminiTextLine of(final String text) {
    return new AutoValue_GeminiTextLine(text);
  }

  /** The text of the line. */
  public abstract String text();
}
