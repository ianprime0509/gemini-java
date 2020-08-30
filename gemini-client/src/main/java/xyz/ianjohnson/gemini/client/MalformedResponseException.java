package xyz.ianjohnson.gemini.client;

import xyz.ianjohnson.gemini.GeminiException;

public class MalformedResponseException extends GeminiException {
  public MalformedResponseException() {}

  public MalformedResponseException(final String message) {
    super(message);
  }

  public MalformedResponseException(final Throwable cause) {
    super(cause);
  }

  public MalformedResponseException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
