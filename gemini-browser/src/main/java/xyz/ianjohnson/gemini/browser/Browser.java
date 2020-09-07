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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
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
  private static final String KEY_STORE_PASSWORD = "password";
  private static final Logger log = LoggerFactory.getLogger(Browser.class);

  private final ExecutorService executorService;
  private final KeyStoreManager keyStoreManager;
  private final GeminiClient client;
  private final BrowserTheme theme;

  private final BrowserNavigation navigation;
  private final BrowserContent contentDisplay;
  private final BrowserStatusBar statusBar;

  public Browser() {
    executorService = Executors.newCachedThreadPool();
    keyStoreManager = new KeyStoreManager(loadKeyStore());
    client =
        GeminiClient.newBuilder()
            .executor(executorService)
            .trustManager(new TofuClientTrustManager(keyStoreManager))
            .build();
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
            saveKeyStore(keyStoreManager.keyStore());
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

  /** Attempts to load the user's key store or create a new one if it doesn't exist. */
  private static KeyStore loadKeyStore() {
    final KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance("PKCS12");
    } catch (final KeyStoreException e) {
      throw new IllegalStateException("Could not get key store instance", e);
    }
    if (Files.exists(KEY_STORE_PATH)) {
      log.info("Attempting to read existing key store {}", KEY_STORE_PATH);
      try (final var is = Files.newInputStream(KEY_STORE_PATH)) {
        keyStore.load(is, KEY_STORE_PASSWORD.toCharArray());
      } catch (final IOException e) {
        throw new UncheckedIOException("Could not read from key store", e);
      } catch (final NoSuchAlgorithmException | CertificateException e) {
        throw new IllegalStateException("Could not load key store", e);
      }
      log.info("Loaded key store from {}", KEY_STORE_PATH);
    } else {
      log.warn("No existing key store found; creating a new one");
      try {
        keyStore.load(null, null);
      } catch (final IOException | NoSuchAlgorithmException | CertificateException e) {
        throw new IllegalStateException("Could not initialize new key store", e);
      }
      log.info("Created new key store");
    }
    return keyStore;
  }

  /** Attempts to save the given key store to the user's key store path. */
  private static void saveKeyStore(final KeyStore keyStore) {
    try {
      Files.createDirectories(KEY_STORE_PATH.getParent());
    } catch (final IOException e) {
      throw new IllegalStateException("Could not create parent directories for key store file", e);
    }
    try (final var os = Files.newOutputStream(KEY_STORE_PATH)) {
      keyStore.store(os, KEY_STORE_PASSWORD.toCharArray());
    } catch (final IOException e) {
      throw new UncheckedIOException("Could not save key store", e);
    } catch (final KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      throw new IllegalStateException("Could not save key store", e);
    }
    log.info("Saved key store to {}", KEY_STORE_PATH);
  }

  private void handleHyperlinkUpdate(final HyperlinkEvent e) {
    URI uri = null;
    if (e.getURL() != null) {
      try {
        uri = e.getURL().toURI();
      } catch (final URISyntaxException ignored) {
      }
    }
    if (uri == null && e.getDescription() != null) {
      try {
        uri = new URI(e.getDescription());
      } catch (final URISyntaxException ignored) {
      }
    }
    if (uri == null) {
      return;
    }

    if (e.getEventType() == EventType.ACTIVATED) {
      navigation.navigate(uri);
    } else if (e.getEventType() == EventType.ENTERED) {
      statusBar.setTemporaryText(uri.toString());
    } else if (e.getEventType() == EventType.EXITED) {
      statusBar.setTemporaryText(null);
    }
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
      final var subscriber = renderer.render(type, theme);
      if (subscriber.isPresent()) {
        return subscriber.get();
      }
    }
    throw new UnsupportedMimeTypeException(type);
  }

  private void handleResponse(final GeminiResponse<StyledDocument> response) {
    log.info("Received response with status {} and meta {}", response.status(), response.meta());

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
          final var hostLock = keyStoreManager.certificateLock(host);
          hostLock.lock();
          try {
            keyStoreManager.setCertificate(host, newCert);
          } catch (final KeyStoreException kse) {
            log.error("Error updating key store", kse);
            statusBar.setText("Error updating key store: " + kse);
            return;
          } finally {
            hostLock.unlock();
          }
          navigation.refresh();
        });
    final var buttonAttributes = new SimpleAttributeSet();
    StyleConstants.setComponent(buttonAttributes, trustButton);
    doc.append("Trust new certificate", buttonAttributes);

    contentDisplay.setStyledDocument(doc);
  }
}
