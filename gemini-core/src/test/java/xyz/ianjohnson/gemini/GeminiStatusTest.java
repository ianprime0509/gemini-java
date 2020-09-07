package xyz.ianjohnson.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.GeminiStatus.Kind;

public class GeminiStatusTest {
  @Test
  public void testValueOf_withStandardGeminiStatusCode_returnsCorrespondingStandardGeminiStatus() {
    for (final var standardStatus : StandardGeminiStatus.values()) {
      assertThat(GeminiStatus.valueOf(standardStatus.code())).isSameAs(standardStatus);
    }
  }

  @Test
  public void
      testValueOf_withNonStandardGeminiStatusCodeOfValidKind_returnsNonStandardGeminiStatus() {
    for (final var kind : Kind.values()) {
      final var code = 10 * kind.firstDigit() + 8;
      assertThat(GeminiStatus.valueOf(code))
          .satisfies(
              status -> {
                assertThat(status).isNotInstanceOf(StandardGeminiStatus.class);
                assertThat(status.kind()).isEqualTo(kind);
                assertThat(status.code()).isEqualTo(code);
                assertThat(status.toString()).isEqualTo("unknown " + kind + " (" + code + ")");
              });
    }
  }

  @Test
  public void testValueOf_withInvalidGeminiStatusCode_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> GeminiStatus.valueOf(1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Gemini status code: 1");
    assertThatThrownBy(() -> GeminiStatus.valueOf(-10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Gemini status code: -10");
    assertThatThrownBy(() -> GeminiStatus.valueOf(100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Gemini status code: 100");
    assertThatThrownBy(() -> GeminiStatus.valueOf(99))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unrecognized response status kind: 9");
  }
}
