package xyz.ianjohnson.gemini.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import javax.swing.event.HyperlinkEvent;

final class Events {
  private Events() {}

  static Optional<URI> getUri(final HyperlinkEvent e) {
    if (e.getURL() != null) {
      try {
        return Optional.of(e.getURL().toURI());
      } catch (final URISyntaxException ignored) {
      }
    }
    if (e.getDescription() != null) {
      try {
        return Optional.of(new URI(e.getDescription()));
      } catch (final URISyntaxException ignored) {
      }
    }
    return Optional.empty();
  }
}
