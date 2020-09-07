package xyz.ianjohnson.gemini.client;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

final class CertificateGenerator {
  private CertificateGenerator() {}

  static X509Certificate generateCertificate(final Instant notBefore, final Instant notAfter) {
    final KeyPairGenerator keyPairGenerator;
    try {
      keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    keyPairGenerator.initialize(2048);
    final var keyPair = keyPairGenerator.generateKeyPair();
    final var subject = new X500Principal("CN=gemini.test");
    final ContentSigner contentSigner;
    try {
      contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    } catch (final OperatorCreationException e) {
      throw new IllegalStateException(e);
    }
    final var certificateHolder =
        new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.ZERO,
                Date.from(notBefore),
                Date.from(notAfter),
                subject,
                keyPair.getPublic())
            .build(contentSigner);

    try {
      return new JcaX509CertificateConverter().getCertificate(certificateHolder);
    } catch (final CertificateException e) {
      throw new IllegalStateException(e);
    }
  }
}
