package xyz.ianjohnson.gemini.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.StringJoiner;

/** Static utility methods for working with {@link Certificate Certificates}. */
public final class Certificates {
  private Certificates() {}

  /**
   * Returns the fingerprint of the given certificate.
   *
   * <p>The fingerprint of a certificate is the SHA-1 hash of its encoded form. This method formats
   * the hash as colon-separated hexadecimal octets, such as {@code 0E:FA:7B:...}.
   *
   * @param certificate the certificate whose fingerprint to return
   * @return the fingerprint of the given certificate
   * @throws CertificateEncodingException if the given certificate cannot be decoded
   */
  public static String getFingerprint(final Certificate certificate)
      throws CertificateEncodingException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-1");
    } catch (final NoSuchAlgorithmException e) {
      throw new AssertionError("SHA-1 must be supported by MessageDigest", e);
    }
    final var bytes = digest.digest(certificate.getEncoded());
    final var sj = new StringJoiner(":");
    for (final var b : bytes) {
      sj.add(String.format("%02X", b));
    }
    return sj.toString();
  }

  /**
   * Returns the fingerprint of the given certificate or a special value indicating it cannot be
   * decoded.
   *
   * @param certificate the certificate whose fingerprint to return
   * @return the fingerprint of the given certificate, or the string {@code <invalid encoding>} if
   *     it cannot be decoded
   * @see #getFingerprint(Certificate)
   */
  public static String getFingerprintUnchecked(final Certificate certificate) {
    try {
      return getFingerprint(certificate);
    } catch (final CertificateEncodingException e) {
      return "<invalid encoding>";
    }
  }
}
