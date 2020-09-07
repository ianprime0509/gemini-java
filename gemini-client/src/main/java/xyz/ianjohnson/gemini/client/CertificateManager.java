package xyz.ianjohnson.gemini.client;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * An abstraction around a {@link KeyStore} or other certificate storage mechanism that manages
 * trusted certificates for hosts. Implementing classes must be thread-safe, as certificates may be
 * read and updated from multiple threads concurrently.
 */
public interface CertificateManager {
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
