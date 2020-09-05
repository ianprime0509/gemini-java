package xyz.ianjohnson.gemini.client;

import xyz.ianjohnson.gemini.GeminiException;

/** An exception thrown to indicate that a response received from a Gemini server is malformed. */
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
