package xyz.ianjohnson.gemini.browser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import xyz.ianjohnson.gemini.client.GeminiClient;

public final class Browser {
  private final ExecutorService executorService;
  private final GeminiClient client;

  private JFrame frame;
  private JTextField uriInput;
  private URI currentUri;
  private JTextPane contentDisplay;
  private JLabel statusBar;

  public Browser() {
    executorService = Executors.newCachedThreadPool();
    client = GeminiClient.newBuilder().executor(executorService).build();
  }

  public static void main(final String... args) {
    SwingUtilities.invokeLater(() -> new Browser().start());
  }

  public void start() {
    frame = new JFrame();
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setPreferredSize(new Dimension(800, 600));
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(final WindowEvent e) {
            client.close();
            executorService.shutdownNow();
          }
        });

    final var menuBar = new JMenuBar();
    frame.setJMenuBar(menuBar);
    final var fileMenu = new JMenu("File");
    menuBar.add(fileMenu);
    final var exitMenuItem = new JMenuItem("Exit");
    exitMenuItem.addActionListener(ev -> frame.dispose());
    fileMenu.add(exitMenuItem);

    final var navigationPane = new JPanel(new BorderLayout());
    frame.add(navigationPane, BorderLayout.PAGE_START);
    uriInput = new JTextField();
    uriInput.addActionListener(ev -> navigate());
    navigationPane.add(uriInput, BorderLayout.CENTER);
    final var goButton = new JButton("Go");
    goButton.addActionListener(ev -> navigate());
    navigationPane.add(goButton, BorderLayout.LINE_END);

    contentDisplay = new BrowserContent(this::navigate);
    frame.add(new JScrollPane(contentDisplay), BorderLayout.CENTER);

    statusBar = new JLabel("Ready");
    frame.add(statusBar, BorderLayout.PAGE_END);

    frame.pack();
    frame.setVisible(true);
  }

  private void navigate() {
    final URI uri;
    try {
      uri = new URI(uriInput.getText());
    } catch (final URISyntaxException e) {
      statusBar.setText("Invalid URI: " + e);
      return;
    }
    if (uri.getHost() == null) {
      statusBar.setText("Host required");
      return;
    }
    statusBar.setText("Loading...");
    client
        .sendAsync(uri, type -> new GeminiDocumentRenderer().render(type))
        .whenComplete(
            (response, error) -> {
              if (response != null) {
                SwingUtilities.invokeLater(
                    () -> {
                      response.body().ifPresent(contentDisplay::setStyledDocument);
                      statusBar.setText("Done: " + response.status() + " - " + response.meta());
                      currentUri = uri;
                    });
              } else {
                SwingUtilities.invokeLater(() -> statusBar.setText("Error: " + error.getMessage()));
              }
            });
  }

  private void navigate(final URI uri) {
    uriInput.setText(currentUri.resolve(uri).toString());
    navigate();
  }
}
