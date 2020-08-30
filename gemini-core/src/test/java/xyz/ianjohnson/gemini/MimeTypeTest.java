package xyz.ianjohnson.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class MimeTypeTest {
  @Test
  public void testParse_withSimpleValidMimeType_returnsParsedMimeType()
      throws MimeTypeSyntaxException {
    assertThat(MimeType.parse("text/gemini")).isEqualTo(MimeType.of("text", "gemini"));
    assertThat(MimeType.parse("image/png")).isEqualTo(MimeType.of("image", "png"));
    assertThat(MimeType.parse("x-type/x-subtype")).isEqualTo(MimeType.of("x-type", "x-subtype"));
    assertThat(MimeType.parse("Application/JSON")).isEqualTo(MimeType.of("application", "json"));
  }

  @Test
  public void testParse_withMimeTypeContainingParameters_returnsParsedMimeType()
      throws MimeTypeSyntaxException {
    assertThat(MimeType.parse("text/plain; charset=utf-8"))
        .isEqualTo(MimeType.of("text", "plain", Map.of("charset", "utf-8")));
    assertThat(MimeType.parse("text/plain; charset=\"UTF-8\""))
        .isEqualTo(MimeType.of("text", "plain", Map.of("charset", "UTF-8")));
    assertThat(
            MimeType.parse(
                "application/x-myextension;PARAM1=Value;param2=\"quoted \\\"value\\\"\""))
        .isEqualTo(
            MimeType.of(
                "application",
                "x-myextension",
                Map.of("param1", "Value", "param2", "quoted \"value\"")));
  }

  @Test
  public void testParse_withInvalidMimeType_throwsMimeTypeSyntaxException() {
    assertThatThrownBy(() -> MimeType.parse("")).isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("   ")).isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("text")).isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("text / plain"))
        .isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("text/plain;"))
        .isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("\"text/plain\""))
        .isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("text/plain;charset"))
        .isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("text/plain;charset="))
        .isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("text/plain;charset=\"UTF-8"))
        .isInstanceOf(MimeTypeSyntaxException.class);
    assertThatThrownBy(() -> MimeType.parse("text/plain utf-8"))
        .isInstanceOf(MimeTypeSyntaxException.class);
  }
}
