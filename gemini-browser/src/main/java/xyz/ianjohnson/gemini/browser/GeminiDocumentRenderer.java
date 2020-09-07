package xyz.ianjohnson.gemini.browser;

import java.nio.charset.Charset;
import java.util.Optional;
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
  public Optional<BodySubscriber<StyledDocument>> render(
      final MimeType mimeType, final BrowserTheme theme) {
    if (!mimeType.sameType(MimeType.TEXT_GEMINI)) {
      return Optional.empty();
    }
    return Optional.of(
        BodySubscribers.mapping(
            BodySubscribers.ofString(
                Charset.forName(mimeType.parameter("charset").orElse("utf-8"))),
            text -> render(text, theme)));
  }

  private StyledDocument render(final String text, final BrowserTheme theme) {
    final var gemini = GeminiDocument.parse(text);
    final var doc = new BrowserDocument(theme);

    for (final var content : gemini.content()) {
      if (content instanceof GeminiTextLine) {
        doc.appendText(((GeminiTextLine) content).text());
        doc.appendLineBreak();
      } else if (content instanceof GeminiHeadingLine) {
        final var head = (GeminiHeadingLine) content;
        doc.appendHeadingText(head.text(), head.level());
        doc.appendLineBreak();
      } else if (content instanceof GeminiLinkLine) {
        final var link = (GeminiLinkLine) content;
        doc.appendLink(link.title().orElse(link.uri().toString()), link.uri());
        doc.appendLineBreak();
      } else if (content instanceof GeminiPreformattedText) {
        final var contentText = ((GeminiPreformattedText) content).text();
        doc.appendPreformattedText(contentText);
        if (!contentText.endsWith("\n")) {
          doc.appendLineBreak();
        }
      } else if (content instanceof GeminiQuoteLine) {
        doc.appendQuoteText("> " + ((GeminiQuoteLine) content).text());
        doc.appendLineBreak();
      } else if (content instanceof GeminiUnorderedListItem) {
        doc.appendUnorderedListText("* " + ((GeminiUnorderedListItem) content).text());
        doc.appendLineBreak();
      }
    }

    return doc;
  }
}
