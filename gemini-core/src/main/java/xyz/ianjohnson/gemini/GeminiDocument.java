package xyz.ianjohnson.gemini;

import com.google.auto.value.AutoValue;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@AutoValue
public abstract class GeminiDocument {
  GeminiDocument() {}

  public static GeminiDocument of(final List<GeminiContent> content) {
    return new AutoValue_GeminiDocument(List.copyOf(content));
  }

  public static GeminiDocument parse(final String document) {
    final List<GeminiContent> content = new ArrayList<>();
    String altText = null;
    StringBuilder preformatted = null;
    for (final var line : document.split("\r?\n")) {
      if (line.startsWith("```")) {
        if (preformatted == null) {
          // Start of preformatted text
          altText = line.substring(3).strip();
          if (altText.isEmpty()) {
            altText = null;
          }
          preformatted = new StringBuilder();
        } else {
          content.add(GeminiPreformattedText.of(preformatted.toString(), altText));
          altText = null;
          preformatted = null;
        }
      } else if (preformatted != null) {
        preformatted.append(line);
        preformatted.append("\n");
      } else if (line.startsWith("=>")) {
        final var parts = line.substring(2).stripLeading().split("\\s+", 2);
        final URI uri;
        try {
          uri = new URI(parts[0]);
        } catch (final URISyntaxException e) {
          // Invalid link; treat as text
          content.add(GeminiTextLine.of(line));
          continue;
        }
        final var title = parts.length > 1 && !parts[1].isBlank() ? parts[1].strip() : null;
        content.add(GeminiLinkLine.of(uri, title));
      } else if (line.startsWith("#")) {
        final int level;
        final String text;
        if (line.startsWith("###")) {
          level = 3;
          text = line.substring(3);
        } else if (line.startsWith("##")) {
          level = 2;
          text = line.substring(2);
        } else {
          level = 1;
          text = line.substring(1);
        }
        content.add(GeminiHeadingLine.of(level, text.strip()));
      } else if (line.startsWith("* ")) {
        content.add(GeminiUnorderedListItem.of(line.substring(2).strip()));
      } else if (line.startsWith(">")) {
        content.add(GeminiQuoteLine.of(line.substring(1).strip()));
      } else {
        content.add(GeminiTextLine.of(line.strip()));
      }
    }
    // Handle unterminated preformatted content
    if (preformatted != null) {
      content.add(GeminiPreformattedText.of(preformatted.toString(), altText));
    }
    return of(content);
  }

  /** The content of the document. */
  public abstract List<GeminiContent> content();
}
