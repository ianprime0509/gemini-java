package xyz.ianjohnson.gemini.browser;

import java.net.URI;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import xyz.ianjohnson.gemini.Nullable;

public final class BrowserStyleConstants {
  public static final Object Link = new CharacterAttribute("link");
  public static final Object Wrap = new ParagraphAttribute("wrap");

  private BrowserStyleConstants() {}

  @Nullable
  public static URI getLink(final AttributeSet as) {
    final var link = as.getAttribute(Link);
    return link instanceof URI ? (URI) link : null;
  }

  public static void setLink(final MutableAttributeSet as, final URI uri) {
    as.addAttribute(Link, uri);
  }

  public static WrapStyle getWrapStyle(final AttributeSet as) {
    final var wrap = as.getAttribute(Wrap);
    return wrap instanceof WrapStyle ? (WrapStyle) wrap : WrapStyle.STANDARD;
  }

  public static void setWrapStyle(final MutableAttributeSet as, final WrapStyle wrap) {
    as.addAttribute(Wrap, wrap);
  }

  public enum WrapStyle {
    STANDARD,
    NONE,
  }

  private static class Attribute {
    private final String name;

    private Attribute(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static class CharacterAttribute extends Attribute
      implements AttributeSet.CharacterAttribute {
    private CharacterAttribute(final String name) {
      super(name);
    }
  }

  private static class ParagraphAttribute extends Attribute
      implements AttributeSet.ParagraphAttribute {
    private ParagraphAttribute(final String name) {
      super(name);
    }
  }
}
