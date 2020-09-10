package xyz.ianjohnson.gemini.client;

import static java.util.Collections.list;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An implementation of {@link CertificateManager} backed by a {@link KeyStore}.
 *
 * <p>Trusted certificates are stored with aliases of the form {@code cer-<host>}, where {@code
 * <host>} is the name of the host.
 */
public final class KeyStoreManager implements CertificateManager {
  public static final String CERTIFICATE_ALIAS_PREFIX = "cer-";

  private final KeyStore keyStore;
  private final ReadWriteLock keyStoreLock = new ReentrantReadWriteLock();

  /**
   * Constructs a new {@link KeyStoreManager} wrapping the given {@link KeyStore}. It is not valid
   * to use the key store in any way, except as provided by the methods of this class, while the key
   * store manager is in use by any consumer.
   *
   * @param keyStore the {@link KeyStore} to wrap
   */
  public KeyStoreManager(final KeyStore keyStore) {
    this.keyStore = keyStore;
  }

  /**
   * Returns the underlying {@link KeyStore} managed by this {@link KeyStoreManager}. It is not
   * valid to use the returned key store unless controlled by the locking methods of this class
   * ({@link #readLock()} and {@link #writeLock()}).
   *
   * @return the underlying {@link KeyStore} managed by this {@link KeyStoreManager}
   */
  public KeyStore keyStore() {
    return keyStore;
  }

  /**
   * Returns a {@link Lock} to control read access to the underlying key store. It is not valid to
   * read from the underlying key store unless this lock is held.
   *
   * @return a {@link Lock} to control read access to the underlying key store
   */
  public Lock readLock() {
    return keyStoreLock.readLock();
  }

  /**
   * Returns a {@link Lock} to control write access to the underlying key store. It is not valid to
   * write to the underlying key store unless this lock is held.
   *
   * @return a {@link Lock} to control write access to the underlying key store
   */
  public Lock writeLock() {
    return keyStoreLock.writeLock();
  }

  @Override
  public List<String> hosts() throws KeyStoreException {
    readLock().lock();
    try {
      final var aliases = list(keyStore.aliases());
      final var hosts = new ArrayList<String>();
      for (final var alias : aliases) {
        if (alias.startsWith(CERTIFICATE_ALIAS_PREFIX) && keyStore.isCertificateEntry(alias)) {
          hosts.add(alias.substring(CERTIFICATE_ALIAS_PREFIX.length()));
        }
      }
      return hosts;
    } finally {
      readLock().unlock();
    }
  }

  @Override
  public Optional<X509Certificate> getCertificate(final String host) throws KeyStoreException {
    readLock().lock();
    try {
      final var cert = keyStore.getCertificate(CERTIFICATE_ALIAS_PREFIX + host);
      if (cert instanceof X509Certificate) {
        return Optional.of((X509Certificate) cert);
      }
      return Optional.empty();
    } finally {
      readLock().unlock();
    }
  }

  @Override
  public void setCertificate(final String host, final X509Certificate certificate)
      throws KeyStoreException {
    writeLock().lock();
    try {
      keyStore.setCertificateEntry(CERTIFICATE_ALIAS_PREFIX + host, certificate);
    } finally {
      writeLock().unlock();
    }
  }
}
