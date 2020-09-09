package xyz.ianjohnson.gemini.browser;

import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import javax.swing.text.StyledDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.client.Certificates;
import xyz.ianjohnson.gemini.client.KeyStoreManager;

public class AboutCertificates implements AboutPage {
  private static final Logger log = LoggerFactory.getLogger(AboutCertificates.class);

  @Override
  public String name() {
    return "certificates";
  }

  @Override
  public StyledDocument display(final String path, final Browser browser) {
    final var doc = new BrowserDocument(browser.theme());
    if (path.isBlank()) {
      displayCertificateOverview(doc, browser.keyStoreManager());
    } else {
      displayCertificate(doc, browser.keyStoreManager(), path);
    }
    return doc;
  }

  private void displayCertificate(
      final BrowserDocument doc, final KeyStoreManager keyStoreManager, final String host) {
    final Optional<X509Certificate> cert;
    try {
      cert = keyStoreManager.getCertificate(host);
    } catch (final KeyStoreException e) {
      log.error("Key store error", e);
      return;
    }

    cert.ifPresentOrElse(
        certificate -> {
          doc.appendHeadingText("Certificate for " + host, 1);
          doc.appendText(
              "\n\nFingerprint: " + Certificates.getFingerprintUnchecked(certificate) + "\n");
          doc.appendText("Subject: " + certificate.getSubjectX500Principal() + "\n");
          doc.appendText("Issuer: " + certificate.getIssuerX500Principal() + "\n");
          doc.appendText("Not valid before: " + certificate.getNotBefore() + "\n");
          doc.appendText("Not valid after: " + certificate.getNotAfter() + "\n");
        },
        () -> {
          doc.appendHeadingText("No certificate found", 1);
          doc.appendText("\n\nNo certificate found for host " + host);
        });
  }

  private void displayCertificateOverview(
      final BrowserDocument doc, final KeyStoreManager keyStoreManager) {
    doc.appendHeadingText("Certificates", 1);
    doc.appendText("\n\n");
    final List<String> hosts;
    try {
      hosts = keyStoreManager.hosts().stream().sorted().collect(toList());
    } catch (final KeyStoreException e) {
      log.error("Key store error", e);
      return;
    }
    for (final var host : hosts) {
      try {
        final var uri = new URI("about", "certificates/" + host, null);
        doc.appendLink(host, uri);
        doc.appendLineBreak();
      } catch (final URISyntaxException e) {
        log.error("Invalid URI when creating about:certificates link", e);
      }
    }
  }
}
