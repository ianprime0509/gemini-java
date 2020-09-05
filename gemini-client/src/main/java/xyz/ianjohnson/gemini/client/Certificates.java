package xyz.ianjohnson.gemini.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.StringJoiner;

public final class Certificates {
  private Certificates() {}

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

  public static String getFingerprintUnchecked(final Certificate certificate) {
    try {
      return getFingerprint(certificate);
    } catch (final CertificateEncodingException e) {
      return "<invalid encoding>";
    }
  }
}
