package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class GeminiHeadingLine extends GeminiContent {
  GeminiHeadingLine() {}

  public static GeminiHeadingLine of(final int level, final String text) {
    if (level < 1 || level > 3) {
      throw new IllegalArgumentException("Level must be 1, 2 or 3");
    }
    return new AutoValue_GeminiHeadingLine(level, text);
  }

  public abstract int level();

  public abstract String text();
}
