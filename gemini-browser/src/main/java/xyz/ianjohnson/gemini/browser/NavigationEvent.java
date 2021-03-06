package xyz.ianjohnson.gemini.browser;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.EventObject;

public class NavigationEvent extends EventObject {
  private final URI uri;

  public NavigationEvent(final Object source, final URI uri) {
    super(source);
    this.uri = requireNonNull(uri);
  }

  public URI getUri() {
    return uri;
  }

  @Override
  public String toString() {
    return "NavigationEvent{" + "uri=" + uri + ", source=" + source + '}';
  }
}
