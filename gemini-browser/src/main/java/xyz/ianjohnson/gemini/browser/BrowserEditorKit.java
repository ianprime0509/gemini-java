package xyz.ianjohnson.gemini.browser;

import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import javax.swing.JEditorPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
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
import xyz.ianjohnson.gemini.Nullable;
import xyz.ianjohnson.gemini.browser.BrowserStyleConstants.WrapStyle;

/**
 * The {@link javax.swing.text.EditorKit EditorKit} used in {@link BrowserContent}. This class
 * provides support for the special browser-related attributes in {@link BrowserStyleConstants}.
 */
public class BrowserEditorKit extends StyledEditorKit {
  private final HyperlinkController hyperlinkController = new HyperlinkController();

  @Override
  public ViewFactory getViewFactory() {
    return new BrowserViewFactory();
  }

  @Override
  public void install(final JEditorPane c) {
    super.install(c);
    c.addMouseListener(hyperlinkController);
    c.addMouseMotionListener(hyperlinkController);
  }

  @Override
  public void deinstall(final JEditorPane c) {
    super.deinstall(c);
    c.removeMouseListener(hyperlinkController);
    c.removeMouseMotionListener(hyperlinkController);
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
            return BrowserStyleConstants.getWrapStyle(elem.getAttributes()) == WrapStyle.NONE
                ? new PreformattedParagraphView(elem)
                : new ParagraphView(elem);
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

  /** A {@link ParagraphView} that does not wrap its text. */
  public static class PreformattedParagraphView extends ParagraphView {
    public PreformattedParagraphView(final Element elem) {
      super(elem);
    }

    @Override
    public float getMinimumSpan(final int axis) {
      // Tip thanks to http://java-sl.com/wrap.html
      return getPreferredSpan(axis);
    }

    @Override
    protected void layout(final int width, final int height) {
      super.layout(Integer.MAX_VALUE, height);
    }
  }

  private static final class HyperlinkController extends MouseAdapter {
    private JTextPane currentSource;
    private URI currentUri;
    private Element currentElement;

    private HyperlinkController() {}

    @Override
    public void mouseClicked(final MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e) || !(e.getSource() instanceof JTextPane)) {
        return;
      }

      final var source = (JTextPane) e.getSource();
      final var doc = source.getStyledDocument();
      final var pos = source.viewToModel2D(e.getPoint());
      if (pos < 0 || pos >= doc.getLength()) {
        return;
      }

      final var elem = doc.getCharacterElement(pos);
      final var uri = BrowserStyleConstants.getLink(elem.getAttributes());
      if (uri != null) {
        source.fireHyperlinkUpdate(createHyperlinkEvent(source, EventType.ACTIVATED, uri, elem, e));
      }
    }

    @Override
    public void mouseMoved(final MouseEvent e) {
      if (!(e.getSource() instanceof JTextPane)) {
        return;
      }

      final var source = (JTextPane) e.getSource();
      final var doc = source.getStyledDocument();
      final var pos = source.viewToModel2D(e.getPoint());
      if (pos < 0 || pos >= doc.getLength()) {
        handlePossibleHyperlinkExit(source, null, null, e);
        return;
      }

      final var elem = doc.getCharacterElement(pos);
      final var uri = BrowserStyleConstants.getLink(elem.getAttributes());
      handlePossibleHyperlinkExit(source, uri, elem, e);
      if (uri != null) {
        currentSource = source;
        currentUri = uri;
        currentElement = elem;
        source.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        source.fireHyperlinkUpdate(createHyperlinkEvent(source, EventType.ENTERED, uri, elem, e));
      }
    }

    private HyperlinkEvent createHyperlinkEvent(
        final JTextPane source,
        final EventType eventType,
        final URI uri,
        final Element element,
        final InputEvent inputEvent) {
      // Unfortunately, HyperlinkEvents deal with URLs and not URIs, which we have to work around
      // here
      URL url;
      String desc = null;
      try {
        url = uri.toURL();
      } catch (final MalformedURLException e) {
        url = null;
        desc = uri.toString();
      }

      return new HyperlinkEvent(source, eventType, url, desc, element, inputEvent);
    }

    private void handlePossibleHyperlinkExit(
        final JTextPane source,
        @Nullable final URI uri,
        @Nullable final Element element,
        final InputEvent inputEvent) {
      if (currentSource != null
          && currentUri != null
          && currentElement != null
          && (currentSource != source || !currentUri.equals(uri) || currentElement != element)) {
        source.setCursor(null);
        currentSource.fireHyperlinkUpdate(
            createHyperlinkEvent(
                currentSource, EventType.EXITED, currentUri, currentElement, inputEvent));
        currentSource = null;
        currentUri = null;
        currentElement = null;
      }
    }
  }
}
