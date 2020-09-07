package xyz.ianjohnson.gemini;

/** A standard Gemini response status code. */
public enum StandardGeminiStatus implements GeminiStatus {
  INPUT(10),
  SENSITIVE_INPUT(11),
  SUCCESS(20),
  TEMPORARY_REDIRECT(30),
  PERMANENT_REDIRECT(31),
  TEMPORARY_FAILURE(40),
  SERVER_UNAVAILABLE(41),
  CGI_ERROR(42),
  PROXY_ERROR(43),
  SLOW_DOWN(44),
  PERMANENT_FAILURE(50),
  NOT_FOUND(51),
  GONE(52),
  PROXY_REQUEST_REFUSED(53),
  BAD_REQUEST(59),
  CLIENT_CERTIFICATE_REQUIRED(60),
  CERTIFICATE_NOT_AUTHORIZED(61),
  CERTIFICATE_NOT_VALID(62),
  ;

  private final Kind kind;
  private final int code;

  StandardGeminiStatus(final int code) {
    this.kind = Kind.valueOf(code / 10);
    this.code = code;
  }

  public static StandardGeminiStatus valueOf(final int code) {
    for (final var status : values()) {
      if (code == status.code()) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown status code: " + code);
  }

  @Override
  public Kind kind() {
    return kind;
  }

  @Override
  public int code() {
    return code;
  }
}
