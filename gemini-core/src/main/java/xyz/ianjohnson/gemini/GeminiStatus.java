package xyz.ianjohnson.gemini;

/**
 * A Gemini response status code. This interface is meant to be sealed, but this is not possible as
 * of Java 11. Client code <em>should not</em> implement this interface, but should rather use the
 * {@link #valueOf(int)} method to return a status corresponding to a status code.
 */
public interface GeminiStatus {
  /**
   * If the given code is a standard Gemini status code, returns the corresponding {@link
   * StandardGeminiStatus}. Otherwise, if the given code is of a valid {@link Kind}, returns a
   * non-standard implementation describing the code and its associated kind.
   *
   * @param code the code to convert to a {@link GeminiStatus}
   * @return the associated {@link GeminiStatus}
   * @throws IllegalArgumentException if the given code is not a two-digit number or does not fall
   *     into one of the recognized {@link Kind Kinds}
   */
  static GeminiStatus valueOf(final int code) {
    if (code < 10 || code > 99) {
      throw new IllegalArgumentException("Invalid Gemini status code: " + code);
    }

    try {
      return StandardGeminiStatus.valueOf(code);
    } catch (final IllegalArgumentException ignored) {
    }
    final var kind = Kind.valueOf(code / 10);
    return NonStandardGeminiStatus.of(kind, code);
  }

  /** The general kind or category of the response status, indicating how it should be handled. */
  Kind kind();

  /**
   * The more detailed response status code, giving more specific details on the nature of the
   * response (for example, permanent redirect versus temporary redirect).
   */
  int code();

  /** The general kind or category of the response status. */
  enum Kind {
    INPUT(1),
    SUCCESS(2),
    REDIRECT(3),
    TEMPORARY_FAILURE(4),
    PERMANENT_FAILURE(5),
    CLIENT_CERTIFICATE_REQUIRED(6),
    ;

    private final int firstDigit;

    Kind(final int firstDigit) {
      this.firstDigit = firstDigit;
    }

    public static Kind valueOf(final int firstDigit) {
      for (final var kind : values()) {
        if (kind.firstDigit() == firstDigit) {
          return kind;
        }
      }
      throw new IllegalArgumentException("Unrecognized response status kind: " + firstDigit);
    }

    /**
     * The distinguishing first digit of the response code that identifies responses of this kind.
     */
    public int firstDigit() {
      return firstDigit;
    }
  }
}
