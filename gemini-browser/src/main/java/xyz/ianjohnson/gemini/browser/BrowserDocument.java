package xyz.ianjohnson.gemini.browser;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.Nullable;
import xyz.ianjohnson.gemini.browser.BrowserStyleConstants.WrapStyle;

/**
 * An extension of {@link DefaultStyledDocument} that supports additional methods facilitating
 * browser document creation.
 */
public class BrowserDocument extends DefaultStyledDocument {
  private static final Logger log = LoggerFactory.getLogger(BrowserDocument.class);

  protected final BrowserTheme theme;

  /**
   * Constructs a new {@link BrowserDocument} with the given theme.
   *
   * @param theme the theme to use when inserting styled content (for example, with {@link
   *     #appendText(String)} and related methods)
   */
  public BrowserDocument(final BrowserTheme theme) {
    this.theme = requireNonNull(theme, "theme");
  }

  /**
   * Appends the given text to the end of the document, with the given attributes.
   *
   * @param text the text to append
   * @param attributes the attributes to apply to the text
   */
  public void append(final String text, @Nullable final AttributeSet attributes) {
    try {
      insertString(getLength(), requireNonNull(text, "text"), attributes);
    } catch (final BadLocationException e) {
      log.error("Impossible BadLocationException in append", e);
    }
  }

  /**
   * Appends plain text to the document, using the associated theme's plain text style.
   *
   * @param text the plain text to append
   * @see BrowserTheme#textStyle()
   */
  public void appendText(final String text) {
    append(text, theme.textStyle());
  }

  /** Appends a line break to the document, using the associated theme's plain text style. */
  public void appendLineBreak() {
    append("\n", theme.textStyle());
  }

  /**
   * Appends a link to the document, using the associated theme's link style along with the {@link
   * BrowserStyleConstants#Link} attribute set to the given URI.
   *
   * @param text the text to append
   * @param uri the URI to link to
   * @see BrowserTheme#linkStyle()
   */
  public void appendLink(final String text, final URI uri) {
    final var style = new SimpleAttributeSet(theme.linkStyle());
    BrowserStyleConstants.setLink(style, requireNonNull(uri, "uri"));
    append(text, style);
  }

  /**
   * Appends preformatted content to the document, using the associated theme's preformatted text
   * style.
   *
   * @param text the preformatted text to append
   * @see BrowserTheme#preformattedTextStyle()
   */
  public void appendPreformattedText(final String text) {
    final var startOffset = getLength();
    append(text, theme.preformattedTextStyle());
    final var attrs = new SimpleAttributeSet();
    BrowserStyleConstants.setWrapStyle(attrs, WrapStyle.NONE);
    setParagraphAttributes(startOffset, text.length(), attrs, false);
  }

  /**
   * Appends heading text to the document, using the associated theme's style for headings of the
   * given level (which must be between 1 and 3, inclusive).
   *
   * @param text the heading text to append
   * @param level the heading level to use (between 1 and 3, inclusive)
   * @throws IllegalArgumentException if {@code level} is not between 1 and 3, inclusive
   * @see BrowserTheme#h1Style()
   * @see BrowserTheme#h2Style()
   * @see BrowserTheme#h3Style()
   */
  public void appendHeadingText(final String text, final int level) {
    if (level < 1 || level > 3) {
      throw new IllegalArgumentException("Heading level must be between 1 and 3, inclusive");
    } else if (level == 1) {
      append(text, theme.h1Style());
    } else if (level == 2) {
      append(text, theme.h2Style());
    } else {
      append(text, theme.h3Style());
    }
  }

  /**
   * Appends quote text to the document, using the associated theme's quote text style.
   *
   * @param text the quote text to append
   * @see BrowserTheme#quoteStyle()
   */
  public void appendQuoteText(final String text) {
    append(text, theme.quoteStyle());
  }

  /**
   * Appends unordered list text, using the associated theme's unordered list text style.
   *
   * @param text the unordered list text to append
   * @see BrowserTheme#unorderedListStyle()
   */
  public void appendUnorderedListText(final String text) {
    append(text, theme.unorderedListStyle());
  }
}
