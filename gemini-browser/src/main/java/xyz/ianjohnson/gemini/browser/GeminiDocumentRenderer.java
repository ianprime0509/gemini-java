package xyz.ianjohnson.gemini.browser;

import java.awt.Color;
import java.awt.Font;
import java.nio.charset.Charset;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
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
  public BodySubscriber<StyledDocument> render(final MimeType mimeType) {
    return BodySubscribers.mapping(
        BodySubscribers.ofString(Charset.forName(mimeType.parameter("charset").orElse("utf-8"))),
        this::render);
  }

  private StyledDocument render(final String text) {
    final var gemini = GeminiDocument.parse(text);
    final var doc = new DefaultStyledDocument();

    final var textStyle = doc.addStyle("text", null);

    final var hStyle = doc.addStyle("h", textStyle);
    StyleConstants.setBold(hStyle, true);
    final var h1Style = doc.addStyle("h1", hStyle);
    StyleConstants.setFontSize(h1Style, 24);
    final var h2Style = doc.addStyle("h2", hStyle);
    StyleConstants.setFontSize(h2Style, 20);
    final var h3Style = doc.addStyle("h3", hStyle);
    StyleConstants.setFontSize(h3Style, 16);

    final var aStyle = doc.addStyle("a", textStyle);
    StyleConstants.setUnderline(aStyle, true);
    StyleConstants.setForeground(aStyle, Color.BLUE);

    final var preStyle = doc.addStyle("pre", textStyle);
    StyleConstants.setFontFamily(preStyle, Font.MONOSPACED);

    final var quoteStyle = doc.addStyle("quote", textStyle);
    StyleConstants.setItalic(quoteStyle, true);
    StyleConstants.setForeground(quoteStyle, new Color(0x70, 0xA0, 0x70));

    for (final var content : gemini.content()) {
      try {
        if (content instanceof GeminiTextLine) {
          doc.insertString(doc.getLength(), ((GeminiTextLine) content).text() + "\n", textStyle);
        } else if (content instanceof GeminiHeadingLine) {
          final var head = (GeminiHeadingLine) content;
          final Style style;
          if (head.level() == 3) {
            style = h3Style;
          } else if (head.level() == 2) {
            style = h2Style;
          } else {
            style = h1Style;
          }
          doc.insertString(doc.getLength(), head.text() + "\n", style);
        } else if (content instanceof GeminiLinkLine) {
          final var link = (GeminiLinkLine) content;
          final var style = doc.addStyle(null, aStyle);
          style.addAttribute(Link, link.uri());
          doc.insertString(
              doc.getLength(), link.title().orElse(link.uri().toString()) + "\n", style);
        } else if (content instanceof GeminiPreformattedText) {
          doc.insertString(doc.getLength(), ((GeminiPreformattedText) content).text(), preStyle);
        } else if (content instanceof GeminiQuoteLine) {
          doc.insertString(
              doc.getLength(), ">" + ((GeminiQuoteLine) content).text() + "\n", quoteStyle);
        } else if (content instanceof GeminiUnorderedListItem) {
          doc.insertString(
              doc.getLength(), "* " + ((GeminiUnorderedListItem) content).text() + "\n", textStyle);
        }
      } catch (final BadLocationException ignored) {
        // Impossible
      }
    }

    return doc;
  }
}
