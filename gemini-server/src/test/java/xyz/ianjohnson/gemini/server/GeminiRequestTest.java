package xyz.ianjohnson.gemini.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xyz.ianjohnson.gemini.server.GeminiRequest.normalizePath;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class GeminiRequestTest {
  @Test
  public void testNormalizePath_withNull_returnsSingleSlash() {
    assertThat(normalizePath(null)).isEqualTo("/");
  }

  @Test
  public void testNormalizePath_withEmptyString_returnsSingleSlash() {
    assertThat(normalizePath("")).isEqualTo("/");
  }

  @Test
  public void testNormalizePath_withRoot_returnsRoot() {
    assertThat(normalizePath("/")).isEqualTo("/");
  }

  @Test
  public void testNormalizePath_withNonDirectory_returnsNonDirectoryPath() {
    assertThat(normalizePath("/this/is/a/file")).isEqualTo("/this/is/a/file");
  }

  @Test
  public void testNormalizePath_withDirectory_returnsDirectoryPath() {
    assertThat(normalizePath("/this/is/a/directory/")).isEqualTo("/this/is/a/directory/");
  }

  @Test
  public void testNormalizePath_withoutLeadingSlash_addsLeadingSlash() {
    assertThat(normalizePath("absolute/path")).isEqualTo("/absolute/path");
  }

  @Test
  public void testNormalizePath_withEmptyComponents_removesEmptyComponents() {
    assertThat(normalizePath("//path/////%20/here/%20/")).isEqualTo("/path/%20/here/%20/");
  }

  @Test
  public void testNormalizePath_withSelfRelativeReferences_resolvesReferences() {
    assertThat(normalizePath("/relative/././reference/./here/./././."))
        .isEqualTo("/relative/reference/here");
  }

  @Test
  public void testNormalizePath_withParentRelativeReferences_resolvesReferences() {
    assertThat(normalizePath("../../directory/../../traversal/attempt/./.."))
        .isEqualTo("/traversal");
  }

  @Test
  public void testNormalizePath_withSeveralDotsInPath_doesNotTreatThreeOrMoreDotsAsReference() {
    assertThat(normalizePath("..././..../.../.././")).isEqualTo("/.../..../");
  }

  @Test
  public void testNormalizePath_withUnnecessarilyEncodedCharacters_decodesCharacters() {
    assertThat(normalizePath("/pa%74%68/")).isEqualTo("/path/");
  }

  @Test
  public void testNormalizePath_withCompletelyEncodedPath_decodesEntirePathAsAppropriate() {
    assertThat(normalizePath("%48%65%6c%6c%6f%2c%20%77%6f%72%6c%64%21"))
        .isEqualTo("/Hello,%20world!");
    assertThat(normalizePath("%48%65%6C%6C%6F%2C%20%77%6F%72%6C%64%21"))
        .isEqualTo("/Hello,%20world!");
  }

  @Test
  public void testNormalizePath_withUtf8EncodedCharacters_decodesAppropriateCharacters() {
    assertThat(normalizePath("%c3%a9%6c%c3%a8%76%65%c2%a0%21/")).isEqualTo("/élève%C2%A0!/");
    assertThat(normalizePath("%C3%A9%6C%C3%A8%76%65%C2%A0%21/")).isEqualTo("/élève%C2%A0!/");
  }

  @Test
  public void testNormalizePath_withEncodedCharacterOutsideBmp_decodesCharacterCorrectly() {
    assertThat(normalizePath("%f0%9F%91%8d")).isEqualTo("/👍");
  }

  @Test
  public void testNormalizePath_withFirst256Characters_matchesJavaUriEncodingRequirements()
      throws URISyntaxException {
    final var unencoded = new StringBuilder();
    for (var i = 0; i < 256; i++) {
      unencoded.appendCodePoint(i);
    }
    final var uri = new URI("gemini", "localhost", "/" + unencoded, null);
    final var javaEncoded = uri.getRawPath();

    final var fullyEncoded = new StringBuilder();
    for (var i = 0; i < 256; i++) {
      if (i == '/') {
        fullyEncoded.append('/');
      } else {
        for (final var b : new String(new int[] {i}, 0, 1).getBytes(StandardCharsets.UTF_8)) {
          fullyEncoded.append(String.format("%%%02X", b));
        }
      }
    }

    assertThat(normalizePath(fullyEncoded.toString())).isEqualTo(javaEncoded);
  }

  @Test
  public void testNormalizePath_withPercentEncodedSlashes_doesNotTreatEncodedSlashesAsSeparators() {
    assertThat(normalizePath("/path%2fpath%2Fpath/dir/%2e././2f/../%2fpath/"))
        .isEqualTo("/path%2Fpath%2Fpath/%2Fpath/");
  }

  @Test
  public void testNormalizePath_withInvalidUtf8Sequences_leavesInvalidSequencesEncoded() {
    assertThat(normalizePath("%FF%Fe/path/%48%65%6c%6c%6f%f7%F7%48%65%6c%6c%6f%f0%9F"))
        .isEqualTo("/%FF%FE/path/Hello%F7%F7Hello%F0%9F");
  }

  @Test
  public void testNormalizePath_withInvalidPercentEncodedSequence_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> normalizePath("%%")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> normalizePath("%GG")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> normalizePath("%4G")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> normalizePath("Hello,%2World!"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> normalizePath("%")).isInstanceOf(IllegalArgumentException.class);
  }
}
