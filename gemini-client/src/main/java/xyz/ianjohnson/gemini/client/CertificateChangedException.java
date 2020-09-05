package xyz.ianjohnson.gemini.client;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;

/**
 * An exception thrown to indicate that the certificate presented by a host is not the same as the
 * one the client currently trusts. This can indicate a potential MITM (man in the middle) attack,
 * or simply that the server's certificate has changed while the old one was still valid.
 *
 * <p>The certificate that the client currently trusts is almost always the one originally presented
 * by the host when the it was first connected to, unless the client's key store has been modified
 * to trust a particular server certificate. See the note on {@link GeminiClient} regarding the
 * server certificate validation strategy used by the client.
 */
public class CertificateChangedException extends CertificateException {
  private final String host;
  private final Certificate newCertificate;
  private final Certificate trustedCertificate;

  /**
   * Constructs a new {@link CertificateChangedException}.
   *
   * @param host the host to which a connection was attempted
   * @param newCertificate the certificate that the host is currently presenting
   * @param trustedCertificate the certificate that the client already trusts for the host
   */
  public CertificateChangedException(
      final String host, final Certificate newCertificate, final Certificate trustedCertificate) {
    super("Trusted certificate for host " + host + " does not match that presented by server");
    this.host = host;
    this.newCertificate = newCertificate;
    this.trustedCertificate = trustedCertificate;
  }

  /**
   * Returns the host to which a connection was attempted.
   *
   * @return the host to which a connection was attempted
   */
  public String host() {
    return host;
  }

  /**
   * Returns the certificate that the host is currently presenting.
   *
   * @return the certificate that the host is currently presenting
   */
  public Certificate newCertificate() {
    return newCertificate;
  }

  /**
   * Returns the certificate that the client already trusts for the host.
   *
   * @return the certificate that the client already trusts for the host
   */
  public Certificate trustedCertificate() {
    return trustedCertificate;
  }
}
