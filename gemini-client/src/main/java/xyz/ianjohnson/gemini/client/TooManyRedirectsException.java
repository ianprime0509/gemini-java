package xyz.ianjohnson.gemini.client;

import java.util.List;
import xyz.ianjohnson.gemini.GeminiException;
import xyz.ianjohnson.gemini.client.GeminiResponse.Redirect;

public class TooManyRedirectsException extends GeminiException {
  private final List<Redirect> redirects;

  /**
   * Constructs a new {@link TooManyRedirectsException} resulting from the given chain of redirects.
   *
   * @param redirects a list of all the redirects followed or attempted, leading up to this
   *     exception, including the requested redirect that caused this exception
   */
  public TooManyRedirectsException(final List<Redirect> redirects) {
    super("Too many redirects for the client to handle");
    this.redirects = List.copyOf(redirects);
  }

  /**
   * Returns a list of all the redirects followed plus the one that caused this exception to be
   * thrown.
   *
   * @return a list of all the redirects followed or attempted leading to this exception
   */
  public List<Redirect> redirects() {
    return redirects;
  }
}
