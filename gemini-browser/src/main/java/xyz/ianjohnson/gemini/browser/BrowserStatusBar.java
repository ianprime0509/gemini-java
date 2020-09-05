package xyz.ianjohnson.gemini.browser;

import javax.swing.JLabel;
import xyz.ianjohnson.gemini.Nullable;

/** The browser status bar shown at the bottom of the window. */
public class BrowserStatusBar extends JLabel {
  @Nullable private String text;
  @Nullable private String temporaryText;

  /**
   * Returns the temporary text being shown in the status bar.
   *
   * @return the temporary text being shown in the status bar
   * @see #setTemporaryText(String)
   */
  @Nullable
  public String getTemporaryText() {
    return temporaryText;
  }

  /**
   * Sets temporary text to be shown in the status bar.
   *
   * <p>Temporary text does not overwrite the primary text set using {@link #setText(String)}. Once
   * the temporary text is reverted to {@code null}, the primary text will again be shown. This
   * makes temporary text a good solution for displaying transient information such as the target of
   * a link under the user's pointer.
   *
   * @param temporaryText the temporary text to show in the status bar
   */
  public void setTemporaryText(@Nullable final String temporaryText) {
    this.temporaryText = temporaryText;
    if (this.temporaryText != null) {
      super.setText(this.temporaryText);
    } else {
      super.setText(text);
    }
  }

  @Override
  public void setText(final String text) {
    this.text = text;
    if (temporaryText == null) {
      super.setText(this.text);
    }
  }
}
