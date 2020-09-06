package xyz.ianjohnson.gemini.client;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An implementation of {@link CertificateManager} backed by a {@link KeyStore}.
 *
 * <p>Trusted certificates are stored with aliases of the form {@code cer-<host>}, where {@code
 * <host>} is the name of the host.
 */
public class KeyStoreManager implements CertificateManager {
  public static final String CERTIFICATE_ALIAS_PREFIX = "cer-";

  private final KeyStore keyStore;
  private final ReadWriteLock keyStoreLock = new ReentrantReadWriteLock();
  private final ConcurrentHashMap<String, ReentrantLock> hostLocks = new ConcurrentHashMap<>();

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
   * valid to use the returned key store in any way while this key store manager is in use by any
   * consumer.
   *
   * @return the underlying {@link KeyStore} managed by this {@link KeyStoreManager}
   */
  public KeyStore keyStore() {
    return keyStore;
  }

  @Override
  public Lock certificateLock(final String host) {
    return hostLocks.computeIfAbsent(host, h -> new ReentrantLock());
  }

  @Override
  public Optional<X509Certificate> getCertificate(final String host) throws KeyStoreException {
    assert ((ReentrantLock) certificateLock(host)).isHeldByCurrentThread();
    keyStoreLock.readLock().lock();
    try {
      final var cert = keyStore.getCertificate(CERTIFICATE_ALIAS_PREFIX + host);
      if (cert instanceof X509Certificate) {
        return Optional.of((X509Certificate) cert);
      }
      return Optional.empty();
    } finally {
      keyStoreLock.readLock().unlock();
    }
  }

  @Override
  public void setCertificate(final String host, final X509Certificate certificate)
      throws KeyStoreException {
    assert ((ReentrantLock) certificateLock(host)).isHeldByCurrentThread();
    keyStoreLock.writeLock().lock();
    try {
      keyStore.setCertificateEntry(CERTIFICATE_ALIAS_PREFIX + host, certificate);
    } finally {
      keyStoreLock.writeLock().unlock();
    }
  }
}