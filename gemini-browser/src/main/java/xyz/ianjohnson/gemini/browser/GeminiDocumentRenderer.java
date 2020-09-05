package xyz.ianjohnson.gemini.browser;

import java.nio.charset.Charset;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import xyz.ianjohnson.gemini.GeminiDocument;
import xyz.ianjohnson.gemini.GeminiHeadingLine;
import xyz.ianjohnson.gemini.GeminiLinkLine;
import xyz.ianjohnson.gemini.GeminiPreformattedText;
import xyz.ianjohnson.gemini.GeminiQuoteLine;
import xyz.ianjohnson.gemini.GeminiTextLine;
import xyz.ianjohnson.gemini.GeminiUnorderedListItem;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodySubscriber;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodySubscribers;

public final class GeminiDocumentRenderer implements DocumentRenderer {
  @Override
  public boolean canApply(final MimeType mimeType) {
    return mimeType.sameType(MimeType.TEXT_GEMINI);
  }

  @Override
  public BodySubscriber<StyledDocument> render(final MimeType mimeType, final BrowserTheme theme) {
    return BodySubscribers.mapping(
        BodySubscribers.ofString(Charset.forName(mimeType.parameter("charset").orElse("utf-8"))),
        text -> render(text, theme));
  }

  private StyledDocument render(final String text, final BrowserTheme theme) {
    final var gemini = GeminiDocument.parse(text);
    final var doc = new DefaultStyledDocument();

    for (final var content : gemini.content()) {
      try {
        if (content instanceof GeminiTextLine) {
          doc.insertString(
              doc.getLength(), ((GeminiTextLine) content).text() + "\n", theme.textStyle());
        } else if (content instanceof GeminiHeadingLine) {
          final var head = (GeminiHeadingLine) content;
          final AttributeSet style;
          if (head.level() == 3) {
            style = theme.h3Style();
          } else if (head.level() == 2) {
            style = theme.h2Style();
          } else {
            style = theme.h1Style();
          }
          doc.insertString(doc.getLength(), head.text(), style);
          doc.insertString(doc.getLength(), "\n", theme.textStyle());
        } else if (content instanceof GeminiLinkLine) {
          final var link = (GeminiLinkLine) content;
          final var style = new SimpleAttributeSet(theme.linkStyle());
          BrowserStyleConstants.setLink(style, link.uri());
          doc.insertString(doc.getLength(), link.title().orElse(link.uri().toString()), style);
          doc.insertString(doc.getLength(), "\n", theme.textStyle());
        } else if (content instanceof GeminiPreformattedText) {
          doc.insertString(
              doc.getLength(),
              ((GeminiPreformattedText) content).text(),
              theme.preformattedTextStyle());
        } else if (content instanceof GeminiQuoteLine) {
          doc.insertString(
              doc.getLength(), ">" + ((GeminiQuoteLine) content).text(), theme.quoteStyle());
          doc.insertString(doc.getLength(), "\n", theme.textStyle());
        } else if (content instanceof GeminiUnorderedListItem) {
          doc.insertString(
              doc.getLength(),
              "* " + ((GeminiUnorderedListItem) content).text(),
              theme.unorderedListStyle());
          doc.insertString(doc.getLength(), "\n", theme.textStyle());
        }
      } catch (final BadLocationException ignored) {
        // Impossible
      }
    }

    return doc;
  }
}
