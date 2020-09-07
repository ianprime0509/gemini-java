package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class NonStandardGeminiStatus implements GeminiStatus {
  NonStandardGeminiStatus() {}

  static NonStandardGeminiStatus of(final Kind kind, final int code) {
    return new AutoValue_NonStandardGeminiStatus(kind, code);
  }

  @Override
  public abstract Kind kind();

  @Override
  public abstract int code();

  @Override
  public final String toString() {
    return "unknown " + kind() + " (" + code() + ")";
  }
}
