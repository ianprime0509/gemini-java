package xyz.ianjohnson.gemini.browser;

import java.net.URI;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import xyz.ianjohnson.gemini.Nullable;

public final class BrowserStyleConstants {
  public static final Object Link = new Object();

  private BrowserStyleConstants() {}

  @Nullable
  public static URI getLink(final AttributeSet as) {
    final Object link = as.getAttribute(Link);
    return link instanceof URI ? (URI) link : null;
  }

  public static void setLink(final MutableAttributeSet as, final URI uri) {
    as.addAttribute(Link, uri);
  }
}
