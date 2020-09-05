package xyz.ianjohnson.gemini.browser;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.EventListenerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserNavigation extends JPanel {
  private static final Logger log = LoggerFactory.getLogger(BrowserNavigation.class);

  protected final EventListenerList listenerList = new EventListenerList();
  private final JTextField uriInput;
  private final JButton backButton;
  private final JButton refreshButton;
  private final JButton forwardButton;
  private final Deque<URI> backHistory = new ArrayDeque<>();
  private final Deque<URI> forwardHistory = new ArrayDeque<>();
  private URI currentUri = URI.create("");

  public BrowserNavigation() {
    super(new GridBagLayout());
    final var constraints = new GridBagConstraints();
    constraints.gridy = 0;
    constraints.fill = GridBagConstraints.BOTH;

    backButton = new JButton("←");
    constraints.weightx = 0;
    add(backButton, constraints);
    backButton.setToolTipText("Back");
    backButton.addActionListener(e -> goBack());

    refreshButton = new JButton("⟳");
    constraints.weightx = 0;
    add(refreshButton, constraints);
    refreshButton.setToolTipText("Refresh");
    refreshButton.addActionListener(e -> refresh());

    forwardButton = new JButton("→");
    constraints.weightx = 0;
    add(forwardButton, constraints);
    forwardButton.setToolTipText("Forward");
    forwardButton.addActionListener(e -> goForward());

    updateNavigationEnabledState();

    uriInput = new JTextField();
    constraints.weightx = 1;
    add(uriInput, constraints);
    uriInput.addActionListener(e -> navigate());

    final var goButton = new JButton("Go");
    constraints.weightx = 0;
    add(goButton, constraints);
    goButton.addActionListener(e -> navigate());
  }

  /** Navigates to the given URI, updating the history accordingly. */
  public void navigate(final URI uri) {
    navigate(uri, true);
  }

  /**
   * Navigates to the current page (firing a {@link NavigationEvent}) without updating the history.
   */
  public void refresh() {
    navigate(currentUri, false);
  }

  /** Goes back one page in the history. */
  public void goBack() {
    final var backUri = backHistory.poll();
    if (backUri == null) {
      return;
    }

    forwardHistory.push(currentUri);
    updateNavigationEnabledState();
    navigate(backUri, false);
  }

  /** Goes forward one page in the history. */
  public void goForward() {
    final var forwardUri = forwardHistory.poll();
    if (forwardUri == null) {
      return;
    }

    backHistory.push(currentUri);
    updateNavigationEnabledState();
    navigate(forwardUri, false);
  }

  public void addNavigationListener(final NavigationListener listener) {
    listenerList.add(NavigationListener.class, listener);
  }

  public void removeNavigationListener(final NavigationListener listener) {
    listenerList.remove(NavigationListener.class, listener);
  }

  /** Navigates to the address currently entered in the URI input. */
  protected void navigate() {
    var input = uriInput.getText();
    URI uri;
    try {
      uri = new URI(input);
    } catch (final URISyntaxException e) {
      // TODO: handle this nicely
      log.error("Invalid URI: " + input, e);
      return;
    }

    // If the URI isn't absolute, we should try to parse it as such by adding the scheme so that the
    // user can simply enter "gemini.example.com" without specifying the scheme and it will work
    if (!uri.isAbsolute()) {
      try {
        input = "gemini://" + input;
        uri = new URI(input);
      } catch (final URISyntaxException e) {
        // TODO
        log.error("Invalid URI: " + input, e);
        return;
      }
    }

    // Finally, if the scheme is not specified, we use gemini as a default explicitly
    if (uri.getScheme() == null) {
      try {
        uri =
            new URI(
                "gemini",
                uri.getAuthority(),
                uri.getHost(),
                uri.getPort(),
                uri.getRawPath(),
                uri.getRawQuery(),
                uri.getRawFragment());
      } catch (final URISyntaxException e) {
        // TODO
        log.error("Invalid URI: " + input, e);
        return;
      }
    }

    navigate(uri);
  }

  /** Navigates to the given URI and updates the history if requested. */
  protected void navigate(final URI uri, final boolean updateHistory) {
    if (updateHistory && !currentUri.toString().isEmpty()) {
      backHistory.push(currentUri);
      forwardHistory.clear();
    }

    currentUri = currentUri.resolve(uri);
    uriInput.setText(currentUri.toString());
    updateNavigationEnabledState();

    fireNavigated(currentUri);
  }

  protected void fireNavigated(final URI uri) {
    final NavigationEvent e = new NavigationEvent(this, uri);
    for (final var listener : listenerList.getListeners(NavigationListener.class)) {
      listener.navigated(e);
    }
  }

  protected void updateNavigationEnabledState() {
    backButton.setEnabled(!backHistory.isEmpty());
    refreshButton.setEnabled(!currentUri.toString().isEmpty());
    forwardButton.setEnabled(!forwardHistory.isEmpty());
  }
}
