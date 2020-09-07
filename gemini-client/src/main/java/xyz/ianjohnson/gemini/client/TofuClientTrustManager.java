package xyz.ianjohnson.gemini.client;

import static java.util.Objects.requireNonNull;

import java.net.Socket;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link javax.net.ssl.TrustManager TrustManager} that implements a TOFU ("trust on first use")
 * trust model.
 *
 * <p>This trust manager trusts the certificate presented by a server on first connecting to it, and
 * henceforth validates that the server presents the same certificate for all future connections as
 * long as it is valid. This is the approach recommended by section 4.2 of the <a
 * href="https://gemini.circumlunar.space/docs/specification.html">Gemini specification</a>, as it
 * allows servers to use self-signed certificates (avoiding the overhead of getting one from a
 * certificate authority) without forcing clients to trust all certificates indiscriminately.
 */
public class TofuClientTrustManager extends X509ExtendedTrustManager {
  private static final Logger log = LoggerFactory.getLogger(TofuClientTrustManager.class);

  private final CertificateManager certificateManager;
  private final ConcurrentMap<String, Lock> hostLocks = new ConcurrentHashMap<>();

  public TofuClientTrustManager(final CertificateManager certificateManager) {
    this.certificateManager = requireNonNull(certificateManager, "certificateManager");
  }

  /**
   * Given a host, returns a {@link Lock} to control access to the certificates for the given host.
   * Handshakes with a host will hold the corresponding lock for the entire duration of the trust
   * decision process. Users who update the underlying certificates directly should also use this
   * lock to prevent any race conditions with trust decisions.
   *
   * <p>The requirement of holding a lock on a per-host basis is to prevent the scenario where two
   * threads make a connection to a previously unknown host within a very short time interval.
   * Without a per-host lock, both connections may simultaneously attempt to get the existing host
   * certificate, find that there is none, and proceed to set the host certificate. However, the
   * certificate that the second connection stores may not be the same as that stored by the first
   * connection, for example if an attacker intercepted the second connection. In this scenario,
   * then, the second certificate would overwrite the first one with no notice to the user.
   *
   * <p>Using a per-host lock allows this class to hold the lock across both the read and the write
   * operations: in the preceding example, the first connection would acquire the lock, get the
   * (nonexistent) host certificate and set the new host certificate, while the second connection
   * would have to wait until the first connection finishes updating the host certificate. Hence,
   * the second connection would fail, as it would see the mismatched host certificate set by the
   * first connection.
   *
   * @param host the host whose lock to return
   * @return a {@link Lock} to control access to trust decisions for the given host
   */
  public Lock hostLock(final String host) {
    return hostLocks.computeIfAbsent(host, h -> new ReentrantLock());
  }

  /**
   * Throws {@link UnsupportedOperationException}, as this {@link javax.net.ssl.TrustManager
   * TrustManager} is for use by clients only.
   */
  @Override
  public void checkClientTrusted(
      final X509Certificate[] chain, final String authType, final Socket socket) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkServerTrusted(
      final X509Certificate[] chain, final String authType, final Socket socket)
      throws CertificateException {
    if (!(socket instanceof SSLSocket)) {
      throw new UnsupportedOperationException("Only SSLSocket is supported");
    }
    checkServerTrustedForHost(chain, ((SSLSocket) socket).getHandshakeSession().getPeerHost());
  }

  /**
   * Throws {@link UnsupportedOperationException}, as this {@link javax.net.ssl.TrustManager
   * TrustManager} is for use by clients only.
   */
  @Override
  public void checkClientTrusted(
      final X509Certificate[] chain, final String authType, final SSLEngine engine) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkServerTrusted(
      final X509Certificate[] chain, final String authType, final SSLEngine engine)
      throws CertificateException {
    checkServerTrustedForHost(chain, engine.getHandshakeSession().getPeerHost());
  }

  /**
   * Throws {@link UnsupportedOperationException}, as this {@link javax.net.ssl.TrustManager
   * TrustManager} is for use by clients only.
   */
  @Override
  public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Throws {@link UnsupportedOperationException}, as this {@link javax.net.ssl.TrustManager
   * TrustManager} requires the server host to be known to validate the certificate.
   */
  @Override
  public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
    throw new UnsupportedOperationException("Server host must be known");
  }

  /**
   * Returns an empty array of certificates.
   *
   * @return an empty array of certificates
   */
  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }

  private void checkServerTrustedForHost(final X509Certificate[] chain, final String host)
      throws CertificateException {
    if (chain.length == 0) {
      throw new CertificateException("No valid certificate supplied by peer");
    }
    final var peerCert = chain[0];
    peerCert.checkValidity();

    final var hostLock = hostLocks.computeIfAbsent(host, h -> new ReentrantLock());
    hostLock.lock();
    try {
      final X509Certificate knownCert;
      try {
        knownCert = certificateManager.getCertificate(host).orElse(null);
      } catch (final KeyStoreException e) {
        throw new CertificateException("Could not look up existing known certificate", e);
      }
      var knownCertValid = knownCert != null;
      try {
        if (knownCertValid) {
          knownCert.checkValidity();
        }
      } catch (final CertificateNotYetValidException e) {
        log.warn(
            "Known certificate for host {} ({}) is somehow not yet valid",
            host,
            Certificates.getFingerprint(knownCert));
        knownCertValid = false;
      } catch (final CertificateExpiredException e) {
        log.info(
            "Known certificate for host {} ({}) is expired",
            host,
            Certificates.getFingerprint(knownCert));
        knownCertValid = false;
      }

      if (!knownCertValid) {
        log.info(
            "Trusting new certificate for host {} with fingerprint {}",
            host,
            Certificates.getFingerprint(peerCert));
        try {
          certificateManager.setCertificate(host, peerCert);
        } catch (final KeyStoreException e) {
          throw new CertificateException("Could not store certificate for new host", e);
        }
      } else if (!knownCert.equals(peerCert)) {
        throw new CertificateChangedException(host, peerCert, knownCert);
      }
    } finally {
      hostLock.unlock();
    }
  }
}
