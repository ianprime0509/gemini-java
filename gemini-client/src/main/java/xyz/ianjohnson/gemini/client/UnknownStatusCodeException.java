package xyz.ianjohnson.gemini.client;

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
