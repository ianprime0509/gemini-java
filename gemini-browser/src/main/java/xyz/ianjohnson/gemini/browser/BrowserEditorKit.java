package xyz.ianjohnson.gemini.browser;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import xyz.ianjohnson.gemini.browser.BrowserStyleConstants.WrapStyle;

/** The {@link javax.swing.text.EditorKit} used in {@link BrowserContent}. */
public class BrowserEditorKit extends StyledEditorKit {
  @Override
  public ViewFactory getViewFactory() {
    return new BrowserViewFactory();
  }

  public static class BrowserViewFactory implements ViewFactory {
    @Override
    public View create(final Element elem) {
      final var name = elem.getName();
      if (name != null) {
        switch (name) {
          case AbstractDocument.ContentElementName:
            return new LabelView(elem);
          case AbstractDocument.ParagraphElementName:
            return new BrowserParagraphView(elem);
          case AbstractDocument.SectionElementName:
            return new BoxView(elem, View.Y_AXIS);
          case StyleConstants.ComponentElementName:
            return new ComponentView(elem);
          case StyleConstants.IconElementName:
            return new IconView(elem);
        }
      }
      return new LabelView(elem);
    }
  }

  public static class BrowserParagraphView extends ParagraphView {
    public BrowserParagraphView(final Element elem) {
      super(elem);
    }

    @Override
    public float getMinimumSpan(final int axis) {
      if (BrowserStyleConstants.getWrapStyle(getAttributes()) == WrapStyle.STANDARD) {
        return super.getMinimumSpan(axis);
      } else {
        // Tip thanks to http://java-sl.com/wrap.html
        return getPreferredSpan(axis);
      }
    }

    @Override
    protected void layout(final int width, final int height) {
      if (BrowserStyleConstants.getWrapStyle(getAttributes()) == WrapStyle.STANDARD) {
        super.layout(width, height);
      } else {
        super.layout(Integer.MAX_VALUE, height);
      }
    }
  }
}
