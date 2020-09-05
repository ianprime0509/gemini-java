package xyz.ianjohnson.gemini;

import java.util.HashMap;

/** A single-use parser for {@link MimeType}s. */
final class MimeTypeParser {
  private final String input;
  private int pos;

  MimeTypeParser(final String input) {
    this.input = input;
  }

  MimeType parse() throws MimeTypeSyntaxException {
    final var type = token();
    character('/');
    final var subtype = token();

    final var parameters = new HashMap<String, String>();
    while (pos < input.length()) {
      character(';');
      // Not strictly legal, but allowed due to use by others
      whitespace();
      // We also allow a trailing semicolon
      if (pos == input.length()) {
        break;
      }

      final var attribute = token();
      character('=');
      parameters.put(attribute, value());
    }
    if (pos != input.length()) {
      syntaxError("unexpected character");
    }

    return MimeType.of(type, subtype, parameters);
  }

  private String value() throws MimeTypeSyntaxException {
    if (pos >= input.length()) {
      syntaxError("expected value");
    }
    if (input.charAt(pos) == '"') {
      return quotedString();
    }
    return token();
  }

  private String quotedString() throws MimeTypeSyntaxException {
    character('"');
    final var sb = new StringBuilder();
    while (pos < input.length()) {
      var c = input.charAt(pos++);
      if (c == '"') {
        return sb.toString();
      } else if (c == '\\') {
        if (pos >= input.length()) {
          syntaxError("expected escaped character");
        }
        c = input.charAt(pos++);
      }
      sb.append(c);
    }
    throw syntaxError("expected end of quoted string");
  }

  private String token() throws MimeTypeSyntaxException {
    final var sb = new StringBuilder();
    sb.append(tokenChar());
    char c;
    while (pos < input.length() && isTokenChar(c = input.charAt(pos))) {
      sb.append(c);
      pos++;
    }
    return sb.toString();
  }

  private char tokenChar() throws MimeTypeSyntaxException {
    if (pos >= input.length()) {
      syntaxError("expected token character");
    }
    final var c = input.charAt(pos);
    if (!isTokenChar(c)) {
      syntaxError("illegal token character");
    }
    pos++;
    return c;
  }

  private boolean isTokenChar(final char c) {
    return c < 0x80 && c > 0x20 && c != '(' && c != ')' && c != '<' && c != '>' && c != '@'
        && c != ',' && c != ';' && c != ':' && c != '\\' && c != '"' && c != '/' && c != '['
        && c != ']' && c != '?' && c != '=';
  }

  private void character(final char c) throws MimeTypeSyntaxException {
    if (pos >= input.length() || input.charAt(pos) != c) {
      syntaxError("expected '" + c + "'");
    }
    pos++;
  }

  private void whitespace() {
    while (pos < input.length() && input.charAt(pos) == ' ') {
      pos++;
    }
  }

  private MimeTypeSyntaxException syntaxError(final String message) throws MimeTypeSyntaxException {
    throw new MimeTypeSyntaxException("At position " + (pos + 1) + ": " + message);
  }
}
