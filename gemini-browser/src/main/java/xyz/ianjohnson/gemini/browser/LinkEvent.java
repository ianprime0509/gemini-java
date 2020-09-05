package xyz.ianjohnson.gemini.browser;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.EventObject;

public class LinkEvent extends EventObject {
  private final URI uri;

  public LinkEvent(final Object source, final URI uri) {
    super(source);
    this.uri = requireNonNull(uri, "uri");
  }

  public URI getUri() {
    return uri;
  }

  @Override
  public String toString() {
    return "LinkEvent{" + "uri=" + uri + ", source=" + source + '}';
  }
}
