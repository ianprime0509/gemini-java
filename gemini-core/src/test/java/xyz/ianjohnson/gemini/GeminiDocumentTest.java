package xyz.ianjohnson.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GeminiDocumentTest {
  @Test
  public void testParse_withSimpleDocument_parsesDocument() {
    final var document =
        "# Test document\n"
            + "\n"
            + "Hello, world!\n"
            + "This is a simple Gemini document.\n"
            + "\n"
            + "=> gemini://gemini.example An example server\n"
            + "=> gemini://alt.gemini.example Another server\n"
            + "\n"
            + "```Preformatted text\n"
            + "Hello, world!\n"
            + "This is preformatted text.\n"
            + "```\n"
            + "\n"
            + "## Sub-heading\n"
            + "\n"
            + "* List item 1\n"
            + "* List item 2\n"
            + "\n"
            + "### Sub-sub-heading\n"
            + "\n"
            + "A great man once said,\n"
            + "> Hello, world!\n"
            + "What a profound statement.\n";

    final var parsed =
        GeminiDocument.of(
            List.of(
                GeminiHeadingLine.of(1, "Test document"),
                GeminiTextLine.of(""),
                GeminiTextLine.of("Hello, world!"),
                GeminiTextLine.of("This is a simple Gemini document."),
                GeminiTextLine.of(""),
                GeminiLinkLine.of(URI.create("gemini://gemini.example"), "An example server"),
                GeminiLinkLine.of(URI.create("gemini://alt.gemini.example"), "Another server"),
                GeminiTextLine.of(""),
                GeminiPreformattedText.of(
                    "Hello, world!\nThis is preformatted text.\n", "Preformatted text"),
                GeminiTextLine.of(""),
                GeminiHeadingLine.of(2, "Sub-heading"),
                GeminiTextLine.of(""),
                GeminiUnorderedListItem.of("List item 1"),
                GeminiUnorderedListItem.of("List item 2"),
                GeminiTextLine.of(""),
                GeminiHeadingLine.of(3, "Sub-sub-heading"),
                GeminiTextLine.of(""),
                GeminiTextLine.of("A great man once said,"),
                GeminiQuoteLine.of(" Hello, world!"),
                GeminiTextLine.of("What a profound statement.")));

    assertThat(GeminiDocument.parse(document)).isEqualTo(parsed);
  }

  @Test
  public void testParse_withDocumentUsingCrLfLineEndings_parsesDocument() {
    final var document = "This document uses CRLF line endings.\r\n" + "What a waste of bytes!\r\n";

    final var parsed =
        GeminiDocument.of(
            List.of(
                GeminiTextLine.of("This document uses CRLF line endings."),
                GeminiTextLine.of("What a waste of bytes!")));

    assertThat(GeminiDocument.parse(document)).isEqualTo(parsed);
  }

  @Test
  public void testParse_withValidAndInvalidLinks_parsesLinksIfValid() {
    final var document =
        "=> gemini://gemini.example.com Example\n"
            + "=>\tgemini://gemini.example.com\tExample 2\n"
            + "=>    https://ianjohnson.xyz      This is valid too\n"
            + "=>gemini://nospace.example.com\n"
            + "=> gemini://example.com    \n"
            + "=> relative/link/test.txt Relative link\n"
            + "=> gopher://gopher.example.com:70/1  Gopher link\n"
            + "=> !@(\\// Invalid link\n";

    final var parsed =
        GeminiDocument.of(
            List.of(
                GeminiLinkLine.of(URI.create("gemini://gemini.example.com"), "Example"),
                GeminiLinkLine.of(URI.create("gemini://gemini.example.com"), "Example 2"),
                GeminiLinkLine.of(URI.create("https://ianjohnson.xyz"), "This is valid too"),
                GeminiLinkLine.of(URI.create("gemini://nospace.example.com")),
                GeminiLinkLine.of(URI.create("gemini://example.com")),
                GeminiLinkLine.of(URI.create("relative/link/test.txt"), "Relative link"),
                GeminiLinkLine.of(URI.create("gopher://gopher.example.com:70/1"), "Gopher link"),
                GeminiTextLine.of("=> !@(\\// Invalid link")));

    assertThat(GeminiDocument.parse(document)).isEqualTo(parsed);
  }

  @Test
  public void testParse_withPreformattedText_parsesPreformattedText() {
    final var document =
        "```\n"
            + "This is preformatted text.\n"
            + "\tAll characters are preserved as-is.\n"
            + " ```\n"
            + "```ignored\n"
            + " ```\n"
            + "``` Alt text\n"
            + "```\n"
            + "```\n"
            + "```\n"
            + "```Not Gemini content\n"
            + "=> gemini://example.com Not a link\n"
            + "# Not a heading\n"
            + "* Not a list item\n"
            + "> Not a quote\n"
            + "```\n"
            + "```\n"
            + "Since ``` is a toggle line, the document may end in preformatted mode.\n";

    final var parsed =
        GeminiDocument.of(
            List.of(
                GeminiPreformattedText.of(
                    "This is preformatted text.\n"
                        + "\tAll characters are preserved as-is.\n"
                        + " ```\n"),
                GeminiTextLine.of(" ```"),
                GeminiPreformattedText.of("", " Alt text"),
                GeminiPreformattedText.of(""),
                GeminiPreformattedText.of(
                    "=> gemini://example.com Not a link\n"
                        + "# Not a heading\n"
                        + "* Not a list item\n"
                        + "> Not a quote\n",
                    "Not Gemini content"),
                GeminiPreformattedText.of(
                    "Since ``` is a toggle line, the document may end in preformatted mode.\n")));

    assertThat(GeminiDocument.parse(document)).isEqualTo(parsed);
  }

  @Test
  public void testParse_withHeadingLines_parsesHeadingLines() {
    final var document =
        "# Heading 1\n"
            + "#\tHeading 2\n"
            + "#Heading 3 \n"
            + "## Heading 4 \n"
            + "##    Heading 5\n"
            + "##Heading 6\n"
            + "### Heading 7\n"
            + "###\t\t  Heading 8\n"
            + "###Heading 9\n"
            + "#### Deeper levels are not recognized\n";

    final var parsed =
        GeminiDocument.of(
            List.of(
                GeminiHeadingLine.of(1, "Heading 1"),
                GeminiHeadingLine.of(1, "Heading 2"),
                GeminiHeadingLine.of(1, "Heading 3 "),
                GeminiHeadingLine.of(2, "Heading 4 "),
                GeminiHeadingLine.of(2, "Heading 5"),
                GeminiHeadingLine.of(2, "Heading 6"),
                GeminiHeadingLine.of(3, "Heading 7"),
                GeminiHeadingLine.of(3, "Heading 8"),
                GeminiHeadingLine.of(3, "Heading 9"),
                GeminiHeadingLine.of(3, "# Deeper levels are not recognized")));

    assertThat(GeminiDocument.parse(document)).isEqualTo(parsed);
  }

  @Test
  public void testParse_withUnorderedListItems_parsesUnorderedListItems() {
    final var document =
        "* One unordered list item\n"
            + "*  Whitespace is preserved \n"
            + "*This doesn't count\n"
            + " * Neither does this\n"
            + "\t* Or this\n";

    final var parsed =
        GeminiDocument.of(
            List.of(
                GeminiUnorderedListItem.of("One unordered list item"),
                GeminiUnorderedListItem.of(" Whitespace is preserved "),
                GeminiTextLine.of("*This doesn't count"),
                GeminiTextLine.of(" * Neither does this"),
                GeminiTextLine.of("\t* Or this")));

    assertThat(GeminiDocument.parse(document)).isEqualTo(parsed);
  }

  @Test
  public void testParse_withQuoteLines_parsesQuoteLines() {
    final var document =
        "> A quote line\n"
            + ">Another quote line\n"
            + " >Not a quote line\n"
            + " > No leading spaces allowed\n";

    final var parsed =
        GeminiDocument.of(
            List.of(
                GeminiQuoteLine.of(" A quote line"),
                GeminiQuoteLine.of("Another quote line"),
                GeminiTextLine.of(" >Not a quote line"),
                GeminiTextLine.of(" > No leading spaces allowed")));

    assertThat(GeminiDocument.parse(document)).isEqualTo(parsed);
  }
}
