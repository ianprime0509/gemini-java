package xyz.ianjohnson.gemini;

import java.io.IOException;

public class GeminiException extends IOException {
  public GeminiException() {}

  public GeminiException(final String message) {
    super(message);
  }

  public GeminiException(final Throwable cause) {
    super(cause);
  }

  public GeminiException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
