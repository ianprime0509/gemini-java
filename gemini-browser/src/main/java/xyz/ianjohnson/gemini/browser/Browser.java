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
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import xyz.ianjohnson.gemini.client.GeminiClient;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandlers;

public class Browser {
  private final ExecutorService executorService;
  private final GeminiClient client;

  public Browser() {
    executorService = Executors.newCachedThreadPool();
    client = GeminiClient.newBuilder().executor(executorService).build();
  }

  public static void main(final String... args) {
    SwingUtilities.invokeLater(() -> new Browser().start());
  }

  public void start() {
    final var frame = new JFrame();
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
    final var uriInput = new JTextField();
    navigationPane.add(uriInput, BorderLayout.CENTER);
    final var goButton = new JButton("Go");
    navigationPane.add(goButton, BorderLayout.LINE_END);

    final var contentDisplay = new JTextPane();
    frame.add(new JScrollPane(contentDisplay), BorderLayout.CENTER);

    goButton.addActionListener(
        event -> {
          final URI uri;
          try {
            uri = new URI(uriInput.getText());
          } catch (final URISyntaxException e) {
            JOptionPane.showMessageDialog(frame, "Invalid URI: " + e);
            return;
          }
          if (uri.getHost() == null) {
            JOptionPane.showMessageDialog(frame, "Host required");
          }
          client
              .sendAsync(uri, BodyHandlers.ofString())
              .whenComplete(
                  (response, error) -> {
                    if (response != null) {
                      final var content =
                          "URI: "
                              + response.uri()
                              + "\n"
                              + "Status: "
                              + response.status()
                              + "\n"
                              + "Meta: "
                              + response.meta()
                              + "\n"
                              + response.body().orElse("");
                      SwingUtilities.invokeLater(() -> contentDisplay.setText(content));
                    } else {
                      SwingUtilities.invokeLater(
                          () -> JOptionPane.showMessageDialog(frame, "Error: " + error));
                    }
                  });
        });

    frame.pack();
    frame.setVisible(true);
  }
}
