package xyz.ianjohnson.gemini.browser;

import javax.swing.JTextPane;
import javax.swing.text.EditorKit;

/**
 * A {@link JTextPane} specifically designed to handle Gemini browser content.
 *
 * <p>This class uses {@link BrowserEditorKit}, which provides special support for several
 * additional attributes on top of the standard attributes (such as color and font) supported by
 * {@link javax.swing.text.StyledEditorKit}, such as link URIs (resulting in clickable links) and
 * paragraph wrapping control. See {@link BrowserStyleConstants} for a convenient way to work with
 * these additional attributes.
 */
public class BrowserContent extends JTextPane {
  public BrowserContent() {
    setEditable(false);
  }

  @Override
  protected EditorKit createDefaultEditorKit() {
    return new BrowserEditorKit();
  }
}
