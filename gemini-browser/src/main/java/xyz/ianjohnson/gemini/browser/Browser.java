package xyz.ianjohnson.gemini.browser;

import dev.dirs.ProjectDirectories;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLHandshakeException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
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
import xyz.ianjohnson.gemini.client.KeyStoreManager;
import xyz.ianjohnson.gemini.client.TofuClientTrustManager;

public class Browser extends JFrame {
  private static final ProjectDirectories PROJECT_DIRECTORIES =
      ProjectDirectories.from("xyz", "Ian Johnson", "Gemini Browser");
  private static final Path KEY_STORE_PATH =
      Paths.get(PROJECT_DIRECTORIES.dataDir).resolve("keystore.p12");
  // I'd rather have no password at all, but that didn't work when I was experimenting with it, so
  // here's a worthless constant password
  private static final String KEY_STORE_PASSWORD = "password";
  private static final Logger log = LoggerFactory.getLogger(Browser.class);

  private final ExecutorService executorService;
  private final UserKeyStoreManager userKeyStoreManager =
      new UserKeyStoreManager(KEY_STORE_PATH, KEY_STORE_PASSWORD);
  private final KeyStoreManager keyStoreManager;
  private final TofuClientTrustManager trustManager;
  private final GeminiClient client;
  private final BrowserTheme theme;

  private final BrowserNavigation navigation;
  private final BrowserContent contentDisplay;
  private final BrowserStatusBar statusBar;

  public Browser() {
    executorService = Executors.newCachedThreadPool();
    try {
      keyStoreManager = userKeyStoreManager.loadIfPresent();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    trustManager = new TofuClientTrustManager(keyStoreManager);
    client = GeminiClient.newBuilder().executor(executorService).trustManager(trustManager).build();
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
            try {
              userKeyStoreManager.store(keyStoreManager);
            } catch (final IOException ex) {
              log.error("Error saving user key store", ex);
            }
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
    contentDisplay.addHyperlinkListener(this::handleHyperlinkUpdate);

    statusBar = new BrowserStatusBar();
    add(statusBar, BorderLayout.PAGE_END);

    pack();
  }

  public static void main(final String... args) {
    setNimbusLookAndFeel();
    SwingUtilities.invokeLater(() -> new Browser().setVisible(true));
  }

  private static void setNimbusLookAndFeel() {
    try {
      for (final var laf : UIManager.getInstalledLookAndFeels()) {
        if ("Nimbus".equals(laf.getName())) {
          UIManager.setLookAndFeel(laf.getClassName());
          return;
        }
      }
      log.error("Nimbus look and feel not found; using default");
    } catch (final Exception e) {
      log.error("Failed to set Nimbus look and feel; using default", e);
    }
  }

  public KeyStoreManager keyStoreManager() {
    return keyStoreManager;
  }

  public BrowserTheme theme() {
    return theme;
  }

  private void handleHyperlinkUpdate(final HyperlinkEvent e) {
    Events.getUri(e)
        .ifPresent(
            uri -> {
              if (e.getEventType() == EventType.ACTIVATED) {
                navigation.navigate(uri);
              } else if (e.getEventType() == EventType.ENTERED) {
                statusBar.setTemporaryText(uri.toString());
              } else if (e.getEventType() == EventType.EXITED) {
                statusBar.setTemporaryText(null);
              }
            });
  }

  private void load(final URI uri) {
    if ("about".equalsIgnoreCase(uri.getScheme())) {
      final var pathParts = uri.getSchemeSpecificPart().split("/", 2);
      final var name = pathParts[0];
      final var path = pathParts.length > 1 ? pathParts[1] : "";
      for (final var page : ServiceLoader.load(AboutPage.class)) {
        if (name.equalsIgnoreCase(page.name())) {
          contentDisplay.setStyledDocument(page.display(path, this));
          statusBar.setText(null);
          return;
        }
      }

      final var doc = new BrowserDocument(theme);
      doc.appendHeadingText("Unknown about page", 1);
      doc.appendText("\n\nUnknown about page: " + name + "\n");
      contentDisplay.setStyledDocument(doc);
      statusBar.setText("Unknown about page: " + name);
      return;
    } else if (uri.getScheme() != null && !"gemini".equalsIgnoreCase(uri.getScheme())) {
      statusBar.setText("Unsupported URI scheme: " + uri.getScheme());
      return;
    } else if (uri.getHost() == null) {
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
      final var subscriber = renderer.render(type, theme);
      if (subscriber.isPresent()) {
        return subscriber.get();
      }
    }
    throw new UnsupportedMimeTypeException(type);
  }

  private void handleResponse(final GeminiResponse<StyledDocument> response) {
    log.info("Received response with status {} and meta {}", response.status(), response.meta());

    navigation.setCurrentUri(response.uri());
    response
        .body()
        .ifPresentOrElse(
            contentDisplay::setStyledDocument,
            () -> {
              final var doc = new BrowserDocument(theme);
              doc.appendHeadingText(response.status().code() + " - " + response.status(), 1);
              doc.appendText("\n\n" + response.meta() + "\n");
              contentDisplay.setStyledDocument(doc);
            });
    statusBar.setText("Done: " + response.status() + " - " + response.meta());
  }

  private void handleResponse(final Throwable t) {
    log.error("Exception while handling response", t);

    if (t instanceof SSLHandshakeException && t.getCause() instanceof CertificateChangedException) {
      final var cce = (CertificateChangedException) t.getCause();
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
      final String host, final X509Certificate newCert, final X509Certificate trustedCert) {
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
          final var lock = trustManager.hostLock(host);
          lock.lock();
          try {
            keyStoreManager.setCertificate(host, newCert);
          } catch (final KeyStoreException kse) {
            log.error("Error updating key store", kse);
            statusBar.setText("Error updating key store: " + kse);
            return;
          } finally {
            lock.unlock();
          }
          navigation.refresh();
        });
    final var buttonAttributes = new SimpleAttributeSet();
    StyleConstants.setComponent(buttonAttributes, trustButton);
    doc.append("Trust new certificate", buttonAttributes);

    contentDisplay.setStyledDocument(doc);
  }
}
