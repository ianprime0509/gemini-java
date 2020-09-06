package xyz.ianjohnson.gemini.client;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * An abstraction around a {@link KeyStore} or other certificate storage mechanism that manages
 * trusted certificates for hosts. Implementing classes must be thread-safe, as certificates may be
 * read and updated from multiple threads concurrently.
 */
public interface CertificateManager {
  /**
   * Returns a {@link Lock} to control access to the certificates for the given host. Users must not
   * get or set the certificate for a host unless they hold the corresponding lock returned by this
   * method.
   *
   * <p>The requirement of holding a lock on a per-host basis is to prevent the scenario in a {@link
   * TofuClientTrustManager TOFU trust strategy} where two threads make a connection to a previously
   * unknown host within a very short time interval. Without a per-host lock, both connections may
   * simultaneously attempt to get the existing host certificate, find that there is none, and
   * proceed to set the host certificate. However, the certificate that the second connection stores
   * may not be the same as that stored by the first connection, for example if an attacker
   * intercepted the second connection. In this scenario, then, the second certificate would
   * overwrite the first one with no notice to the user.
   *
   * <p>Using a per-host lock, as required by this interface, allows such a trust strategy to hold
   * the lock across both the read and the write operations: in the preceding example, the first
   * connection would acquire the lock, get the (nonexistent) host certificate and set the new host
   * certificate, while the second connection would have to wait until the first connection finishes
   * updating the host certificate. Hence, the second connection would fail, as it would see the
   * mismatched host certificate set by the first connection.
   *
   * @param host the host for which to return the lock
   * @return a lock corresponding to the given host, which must be held by users attempting to get
   *     or set the host's certificate
   */
  Lock certificateLock(String host);

  /**
   * Returns the trusted certificate for the given host.
   *
   * @param host the host for which to return the trusted certificate
   * @return the trusted certificate for the given host, if any
   * @throws KeyStoreException if there is an exception reading from the underlying certificate
   *     storage
   */
  Optional<X509Certificate> getCertificate(String host) throws KeyStoreException;

  /**
   * Sets the trusted certificate for the given host.
   *
   * @param host the host for which to set the trusted certificate
   * @param certificate the new trusted certificate for the given host
   * @throws KeyStoreException if there is an exception writing to the underlying certificate
   *     storage
   */
  void setCertificate(String host, X509Certificate certificate) throws KeyStoreException;
}
