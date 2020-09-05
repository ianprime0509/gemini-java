package xyz.ianjohnson.gemini.browser;

import com.google.auto.value.AutoValue;
import java.awt.Color;
import java.awt.Font;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

@AutoValue
public abstract class BrowserTheme {
  BrowserTheme() {}

  public static Builder newBuilder() {
    return new AutoValue_BrowserTheme.Builder();
  }

  public static BrowserTheme defaultTheme() {
    final var textStyle = new SimpleAttributeSet();
    StyleConstants.setFontFamily(textStyle, Font.SANS_SERIF);
    StyleConstants.setFontSize(textStyle, 16);

    final var linkStyle = new SimpleAttributeSet(textStyle);
    StyleConstants.setUnderline(linkStyle, true);
    StyleConstants.setForeground(linkStyle, Color.BLUE);

    final var preformattedTextStyle = new SimpleAttributeSet(textStyle);
    StyleConstants.setFontFamily(preformattedTextStyle, Font.MONOSPACED);

    final var hStyle = new SimpleAttributeSet(textStyle);
    StyleConstants.setBold(hStyle, true);
    final var h1Style = new SimpleAttributeSet(hStyle);
    StyleConstants.setFontSize(h1Style, 28);
    final var h2Style = new SimpleAttributeSet(hStyle);
    StyleConstants.setFontSize(h2Style, 24);
    final var h3Style = new SimpleAttributeSet(hStyle);
    StyleConstants.setFontSize(h3Style, 20);

    final var quoteStyle = new SimpleAttributeSet(textStyle);
    StyleConstants.setForeground(quoteStyle, new Color(0x78, 0x99, 0x22));

    return newBuilder()
        .textStyle(textStyle)
        .linkStyle(linkStyle)
        .preformattedTextStyle(preformattedTextStyle)
        .h1Style(h1Style)
        .h2Style(h2Style)
        .h3Style(h3Style)
        .quoteStyle(quoteStyle)
        .unorderedListStyle(textStyle)
        .build();
  }

  public abstract AttributeSet textStyle();

  public abstract AttributeSet linkStyle();

  public abstract AttributeSet preformattedTextStyle();

  public abstract AttributeSet h1Style();

  public abstract AttributeSet h2Style();

  public abstract AttributeSet h3Style();

  public abstract AttributeSet quoteStyle();

  public abstract AttributeSet unorderedListStyle();

  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder textStyle(AttributeSet textStyle);

    public abstract Builder linkStyle(AttributeSet linkStyle);

    public abstract Builder preformattedTextStyle(AttributeSet preformattedTextStyle);

    public abstract Builder h1Style(AttributeSet h1Style);

    public abstract Builder h2Style(AttributeSet h2Style);

    public abstract Builder h3Style(AttributeSet h3Style);

    public abstract Builder quoteStyle(AttributeSet quoteStyle);

    public abstract Builder unorderedListStyle(AttributeSet unorderedListStyle);

    public abstract BrowserTheme build();
  }
}
