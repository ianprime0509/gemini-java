package xyz.ianjohnson.gemini;

/** An exception thrown to indicate that the syntax of a {@link MimeType} is invalid. */
public class MimeTypeSyntaxException extends Exception {
  public MimeTypeSyntaxException() {}

  public MimeTypeSyntaxException(final String message) {
    super(message);
  }

  public MimeTypeSyntaxException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public MimeTypeSyntaxException(final Throwable cause) {
    super(cause);
  }
}
