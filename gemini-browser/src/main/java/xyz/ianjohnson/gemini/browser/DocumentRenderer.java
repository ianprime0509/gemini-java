package xyz.ianjohnson.gemini.browser;

import javax.swing.text.StyledDocument;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodySubscriber;

public interface DocumentRenderer {
  boolean canApply(MimeType mimeType);

  BodySubscriber<StyledDocument> render(MimeType mimeType, BrowserTheme theme);
}
