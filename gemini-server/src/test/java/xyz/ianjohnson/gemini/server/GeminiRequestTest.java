package xyz.ianjohnson.gemini.server;

import static org.assertj.core.api.Assertions.assertThat;
import static xyz.ianjohnson.gemini.server.GeminiRequest.normalizePath;

import org.junit.jupiter.api.Test;

public class GeminiRequestTest {
  @Test
  public void testNormalizePath_withNull_returnsEmptyString() {
    assertThat(normalizePath(null)).isEqualTo("");
  }

  @Test
  public void testNormalizePath_withEmptyString_returnsEmptyString() {
    assertThat(normalizePath("")).isEqualTo("");
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
    assertThat(normalizePath("//path///// /here/%20/")).isEqualTo("/path/ /here/%20/");
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
}
