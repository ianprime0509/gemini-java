package xyz.ianjohnson.gemini.server;

import java.nio.charset.StandardCharsets;

final class TestUtils {
  private TestUtils() {}

  static byte[] utf8(final String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
