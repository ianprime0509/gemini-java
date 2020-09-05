package xyz.ianjohnson.gemini.browser;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.client.CertificateChangedException;
import xyz.ianjohnson.gemini.client.Certificates;
import xyz.ianjohnson.gemini.client.GeminiClient;
import xyz.ianjohnson.gemini.client.GeminiResponse;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodySubscriber;

public class Browser extends JFrame {
  private static final Logger log = LoggerFactory.getLogger(Browser.class);

  private final ExecutorService executorService;
  private final GeminiClient client;
  private final BrowserTheme theme;

  private final BrowserNavigation navigation;
  private final BrowserContent contentDisplay;
  private final BrowserStatusBar statusBar;

  public Browser() {
    executorService = Executors.newCachedThreadPool();
    client = GeminiClient.newBuilder().executor(executorService).build();
    theme = BrowserTheme.defaultTheme();

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(200, 200));
    setPreferredSize(new Dimension(800, 600));
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(final WindowEvent e) {
            client.close();
            executorService.shutdownNow();
          }
        });

    final var menuBar = new JMenuBar();
    setJMenuBar(menuBar);
    final var fileMenu = new JMenu("File");
    menuBar.add(fileMenu);
    final var exitMenuItem = new JMenuItem("Exit");
    fileMenu.add(exitMenuItem);
    exitMenuItem.addActionListener(e -> dispose());

    navigation = new BrowserNavigation();
    add(navigation, BorderLayout.PAGE_START);
    navigation.addNavigationListener(e -> load(e.getUri()));

    contentDisplay = new BrowserContent();
    add(new JScrollPane(contentDisplay), BorderLayout.CENTER);
    contentDisplay.addLinkListener(
        new LinkListener() {
          @Override
          public void linkClicked(final LinkEvent e) {
            navigation.navigate(e.getUri());
          }

          @Override
          public void linkHoverStarted(final LinkEvent e) {
            statusBar.setTemporaryText(e.getUri().toString());
          }

          @Override
          public void linkHoverEnded(final LinkEvent e) {
            statusBar.setTemporaryText(null);
          }
        });

    statusBar = new BrowserStatusBar();
    add(statusBar, BorderLayout.PAGE_END);

    pack();
  }

  public static void main(final String... args) {
    try {
      for (final var laf : UIManager.getInstalledLookAndFeels()) {
        if ("Nimbus".equals(laf.getName())) {
          UIManager.setLookAndFeel(laf.getClassName());
          break;
        }
      }
      log.error("Nimbus look and feel not found; using default");
    } catch (final Exception e) {
      log.error("Failed to set Nimbus look and feel; using default", e);
    }
    SwingUtilities.invokeLater(() -> new Browser().setVisible(true));
  }

  private void load(final URI uri) {
    if (uri.getHost() == null) {
      statusBar.setText("Host required");
      return;
    }
    statusBar.setText("Loading...");
    client
        .sendAsync(uri, this::render)
        .whenComplete(
            (response, error) -> {
              if (response != null) {
                handleResponse(response);
              } else {
                handleResponse(error);
              }
            });
  }

  private BodySubscriber<StyledDocument> render(final MimeType type) {
    for (final var renderer : ServiceLoader.load(DocumentRenderer.class)) {
      if (renderer.canRender(type)) {
        return renderer.render(type, theme);
      }
    }
    throw new UnsupportedMimeTypeException(type);
  }

  private void handleResponse(final GeminiResponse<StyledDocument> response) {
    response
        .body()
        .ifPresentOrElse(
            contentDisplay::setStyledDocument,
            () -> {
              final var doc = new BrowserDocument(theme);
              doc.appendHeadingText(response.status().code() + " - " + response.status().name(), 1);
              doc.appendText("\n\n" + response.meta() + "\n");
              contentDisplay.setStyledDocument(doc);
            });
    statusBar.setText("Done: " + response.status() + " - " + response.meta());
  }

  private void handleResponse(final Throwable t) {
    if (t instanceof CertificateChangedException) {
      final var cce = (CertificateChangedException) t;
      handleCertificateChange(cce.host(), cce.newCertificate(), cce.trustedCertificate());
    } else {
      final var doc = new BrowserDocument(theme);
      doc.appendHeadingText("Error", 1);
      doc.appendText("\n\n" + t.getMessage() + "\n\nStack trace:\n\n");
      for (final var elem : t.getStackTrace()) {
        doc.appendText(elem + "\n");
      }
      contentDisplay.setStyledDocument(doc);
      statusBar.setText("Error: " + t.getMessage());
    }
  }

  private void handleCertificateChange(
      final String host, final Certificate newCert, final Certificate trustedCert) {
    statusBar.setText("Certificate mismatch!");

    final var doc = new BrowserDocument(theme);

    doc.appendHeadingText("WARNING", 1);
    doc.appendLineBreak();
    final var text =
        "\nCertificate fingerprint mismatch!\n"
            + "\n"
            + "This may mean that someone has tampered with your connection, or just that the server has updated its certificate.\n"
            + "\n"
            + "Trusted certificate fingerprint: "
            + Certificates.getFingerprintUnchecked(trustedCert)
            + "\n"
            + "New certificate fingerprint: "
            + Certificates.getFingerprintUnchecked(newCert)
            + "\n"
            + "\n"
            + "If you are absolutely sure that you trust the new certificate, you may replace the old one by clicking the button below.\n"
            + "\n";
    doc.appendText(text);

    final var trustButton = new JButton("Trust new certificate");
    trustButton.setCursor(Cursor.getDefaultCursor());
    trustButton.addActionListener(
        e -> {
          try {
            client.keyStore().setCertificateEntry(host, newCert);
            navigation.refresh();
          } catch (final KeyStoreException kse) {
            statusBar.setText("Error updating key store: " + kse);
          }
        });
    final var buttonAttributes = new SimpleAttributeSet();
    StyleConstants.setComponent(buttonAttributes, trustButton);
    doc.append("Trust new certificate", buttonAttributes);

    contentDisplay.setStyledDocument(doc);
  }
}
