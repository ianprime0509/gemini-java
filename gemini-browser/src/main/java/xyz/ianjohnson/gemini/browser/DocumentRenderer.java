package xyz.ianjohnson.gemini.browser;

import java.util.Optional;
import javax.swing.text.StyledDocument;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodySubscriber;

/**
 * A service interface for classes that can render content of one or more MIME type(s).
 *
 * <p>This interface is used in conjunction with a {@link java.util.ServiceLoader} to find
 * implementations when the {@link Browser} needs to render a response body.
 */
public interface DocumentRenderer {
  /**
   * Returns a {@link BodySubscriber} that processes the Gemini response body (which is content of
   * the given MIME type) and completes with a {@link StyledDocument} to display in the {@link
   * Browser}. If the given MIME type is not supported, returns {@code null}.
   *
   * <p>See the documentation of {@link BrowserContent} for details on how the {@link
   * StyledDocument} will be displayed.
   *
   * @param mimeType the MIME type of the response body
   * @param theme the browser theme. Renderers should abide by the styles set in this theme as much
   *     as possible to respect the user's choices.
   * @return a {@link BodySubscriber} that renders the response body
   */
  Optional<BodySubscriber<StyledDocument>> render(MimeType mimeType, BrowserTheme theme);
}
