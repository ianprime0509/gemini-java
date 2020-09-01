package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class GeminiUnorderedListItem extends GeminiContent {
  GeminiUnorderedListItem() {}

  public static GeminiUnorderedListItem of(final String text) {
    return new AutoValue_GeminiUnorderedListItem(text);
  }

  public abstract String text();
}
