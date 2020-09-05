package xyz.ianjohnson.gemini.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
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
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.client.CertificateChangedException;
import xyz.ianjohnson.gemini.client.Certificates;
import xyz.ianjohnson.gemini.client.GeminiClient;

public final class Browser {
  private static final Logger log = LoggerFactory.getLogger(Browser.class);

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
    frame.setMinimumSize(new Dimension(200, 200));
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
              } else if (error instanceof CertificateChangedException) {
                final var certError = (CertificateChangedException) error;
                handleCertificateChange(
                    certError.host(), certError.newCertificate(), certError.trustedCertificate());
              } else {
                log.error("Error loading page", error);
                SwingUtilities.invokeLater(() -> statusBar.setText("Error: " + error.getMessage()));
              }
            });
  }

  private void navigate(final URI uri) {
    uriInput.setText(currentUri.resolve(uri).toString());
    navigate();
  }

  private void handleCertificateChange(
      final String host, final Certificate newCert, final Certificate trustedCert) {
    statusBar.setText("Certificate mismatch!");

    final StyledDocument doc = new DefaultStyledDocument();
    final var warningStyle = doc.addStyle("warning", null);
    StyleConstants.setBold(warningStyle, true);
    StyleConstants.setFontSize(warningStyle, 24);
    StyleConstants.setForeground(warningStyle, Color.RED);

    try {
      doc.insertString(doc.getLength(), "WARNING\n", warningStyle);
      doc.insertString(
          doc.getLength(),
          "Certificate fingerprint mismatch! Something fishy may be going on.\n",
          null);
      doc.insertString(
          doc.getLength(),
          "Trusted certificate fingerprint: "
              + Certificates.getFingerprintUnchecked(trustedCert)
              + "\n",
          null);
      doc.insertString(
          doc.getLength(),
          "New certificate fingerprint: " + Certificates.getFingerprintUnchecked(newCert) + "\n",
          null);
      doc.insertString(
          doc.getLength(),
          "\nIf you are absolutely sure that you trust the new certificate, you may replace the old one by clicking below.\n",
          null);

      final var trustButton = new JButton("Trust new certificate");
      trustButton.addActionListener(
          ev -> {
            try {
              client.keyStore().setCertificateEntry(host, newCert);
            } catch (final KeyStoreException e) {
              statusBar.setText("Error updating key store: " + e);
            }
          });
      final var buttonAttributes = new SimpleAttributeSet();
      StyleConstants.setComponent(buttonAttributes, trustButton);
      doc.insertString(doc.getLength(), "Trust new certificate", buttonAttributes);
    } catch (final BadLocationException ignored) {
      // impossible
    }

    contentDisplay.setStyledDocument(doc);
  }
}
