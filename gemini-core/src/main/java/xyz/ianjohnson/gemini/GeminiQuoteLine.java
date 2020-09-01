package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class GeminiQuoteLine extends GeminiContent {
  GeminiQuoteLine() {}

  public static GeminiQuoteLine of(final String text) {
    return new AutoValue_GeminiQuoteLine(text);
  }

  public abstract String text();
}
