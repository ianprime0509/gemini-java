package xyz.ianjohnson.gemini.client;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public class CertificateChangedException extends CertificateException {
  private final String host;
  private final Certificate newCertificate;
  private final Certificate trustedCertificate;

  public CertificateChangedException(
      final String host, final Certificate newCertificate, final Certificate trustedCertificate) {
    super("Trusted certificate does not match that presented by server");
    this.host = host;
    this.newCertificate = newCertificate;
    this.trustedCertificate = trustedCertificate;
  }

  public String host() {
    return host;
  }

  public Certificate newCertificate() {
    return newCertificate;
  }

  public Certificate trustedCertificate() {
    return trustedCertificate;
  }
}
