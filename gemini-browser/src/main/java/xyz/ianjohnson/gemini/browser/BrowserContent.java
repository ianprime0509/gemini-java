package xyz.ianjohnson.gemini.browser;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.URI;
import javax.swing.JTextPane;
import javax.swing.event.EventListenerList;

public class BrowserContent extends JTextPane {
  protected final EventListenerList listenerList = new EventListenerList();

  public BrowserContent() {
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

  public void addLinkListener(final LinkListener listener) {
    listenerList.add(LinkListener.class, listener);
  }

  public void removeLinkListener(final LinkListener listener) {
    listenerList.remove(LinkListener.class, listener);
  }

  protected void fireLinkClicked(final URI uri) {
    final LinkEvent event = new LinkEvent(this, uri);
    for (final var listener : listenerList.getListeners(LinkListener.class)) {
      listener.linkClicked(event);
    }
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
    final var uri = BrowserStyleConstants.getLink(elem.getAttributes());
    if (uri != null) {
      fireLinkClicked(uri);
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
    final var uri = BrowserStyleConstants.getLink(elem.getAttributes());
    if (uri != null) {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setToolTipText(uri.toString());
    } else {
      setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
      setToolTipText(null);
    }
  }
}
