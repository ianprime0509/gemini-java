package xyz.ianjohnson.gemini.browser;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.URI;
import java.util.function.Consumer;
import javax.swing.JTextPane;

class BrowserContent extends JTextPane {
  private final Consumer<URI> navigate;

  BrowserContent(final Consumer<URI> navigate) {
    this.navigate = navigate;

    setEditable(false);
    addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseMoved(final MouseEvent e) {
            handleMouseMoved(e);
          }
        });
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(final MouseEvent e) {
            handleMouseClicked(e);
          }
        });
  }

  private void handleMouseClicked(final MouseEvent e) {
    if (e.getButton() != MouseEvent.BUTTON1) {
      return;
    }

    final var pos = viewToModel2D(e.getPoint());
    if (pos < 0 || pos >= getStyledDocument().getLength()) {
      return;
    }
    final var elem = getStyledDocument().getCharacterElement(pos);
    final var uri = elem.getAttributes().getAttribute(DocumentRenderer.Link);
    if (uri != null) {
      navigate.accept((URI) uri);
    }
  }

  private void handleMouseMoved(final MouseEvent e) {
    final var pos = viewToModel2D(e.getPoint());
    if (pos < 0 || pos >= getStyledDocument().getLength()) {
      setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
      setToolTipText(null);
      return;
    }
    final var elem = getStyledDocument().getCharacterElement(pos);
    final var uri = elem.getAttributes().getAttribute(DocumentRenderer.Link);
    if (uri != null) {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setToolTipText(uri.toString());
    } else {
      setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
      setToolTipText(null);
    }
  }
}
