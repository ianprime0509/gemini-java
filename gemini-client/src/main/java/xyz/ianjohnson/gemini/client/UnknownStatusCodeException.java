package xyz.ianjohnson.gemini.client;

/**
 * An exception thrown to indicate that a response status code returned by a Gemini server is not
 * one of the recognized status codes enumerated in {@link xyz.ianjohnson.gemini.GeminiStatus
 * GeminiStatus}.
 */
public class UnknownStatusCodeException extends MalformedResponseException {
  private final int code;

  public UnknownStatusCodeException(final int code) {
    super("Unknown response status code: " + code);
    this.code = code;
  }

  public int code() {
    return code;
  }
}
