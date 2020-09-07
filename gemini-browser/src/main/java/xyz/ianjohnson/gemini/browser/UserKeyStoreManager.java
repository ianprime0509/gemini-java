package xyz.ianjohnson.gemini.browser;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ianjohnson.gemini.client.KeyStoreManager;

/** A class providing uniform handling of the user's key store file in a pre-defined location. */
public final class UserKeyStoreManager {
  private static final Logger log = LoggerFactory.getLogger(UserKeyStoreManager.class);

  private final Path keyStorePath;
  private final String keyStorePassword;

  public UserKeyStoreManager(final Path keyStorePath, final String keyStorePassword) {
    this.keyStorePath = requireNonNull(keyStorePath, "keyStorePath");
    this.keyStorePassword = requireNonNull(keyStorePassword, "keyStorePassword");
  }

  /**
   * Creates a new {@link KeyStoreManager} wrapping a new {@link KeyStore} and loads it from the
   * user's key store path using {@link #loadIfPresent(KeyStoreManager)}.
   *
   * @return a {@link KeyStoreManager} for the user's key store
   * @throws IOException if an exception occurs while loading the key store
   */
  public KeyStoreManager loadIfPresent() throws IOException {
    final KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance("PKCS12");
    } catch (final KeyStoreException e) {
      throw new IOException("Could not initialize new key store", e);
    }
    final var keyStoreManager = new KeyStoreManager(keyStore);
    loadIfPresent(keyStoreManager);
    return keyStoreManager;
  }

  /**
   * Loads the underlying {@link KeyStore} of the given {@link KeyStoreManager} from the user's key
   * store path, if it exists, or loads a new, empty key store.
   *
   * @param keyStoreManager the {@link KeyStoreManager} whose underlying {@link KeyStore} to load
   * @throws IOException if an exception occurs while loading the key store
   */
  public void loadIfPresent(final KeyStoreManager keyStoreManager) throws IOException {
    final var lock = keyStoreManager.writeLock();
    lock.lock();
    final InputStream is;
    if (Files.exists(keyStorePath)) {
      log.info("Loading user key store from {}", keyStorePath);
      is = Files.newInputStream(keyStorePath);
    } else {
      log.info("No user key store found at {}; using a new one", keyStorePath);
      is = null;
    }
    try (is) {
      keyStoreManager.keyStore().load(is, keyStorePassword.toCharArray());
    } catch (final NoSuchAlgorithmException | CertificateException e) {
      throw new IOException("Could not load key store", e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Stores the underlying {@link KeyStore} of the given {@link KeyStoreManager} to the user's key
   * store path.
   *
   * @param keyStoreManager the {@link KeyStoreManager} whose {@link KeyStore} to store
   * @throws IOException if an exception occurs while storing the key store
   */
  public void store(final KeyStoreManager keyStoreManager) throws IOException {
    Files.createDirectories(keyStorePath.getParent());
    final var lock = keyStoreManager.readLock();
    lock.lock();
    try (final var os = Files.newOutputStream(keyStorePath)) {
      keyStoreManager.keyStore().store(os, keyStorePassword.toCharArray());
    } catch (final KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      throw new IOException("Could not store key store", e);
    } finally {
      lock.unlock();
    }
    log.info("Stored user key store to {}", keyStorePath);
  }
}
