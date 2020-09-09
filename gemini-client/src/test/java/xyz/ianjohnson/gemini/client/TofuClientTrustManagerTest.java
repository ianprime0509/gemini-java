package xyz.ianjohnson.gemini.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TofuClientTrustManagerTest {
  private static X509Certificate validCert;
  private static X509Certificate validCert2;
  private static X509Certificate expiredCert;
  private static X509Certificate notYetValidCert;

  private SimpleCertificateManager certificateManager;
  private TofuClientTrustManager trustManager;

  @BeforeAll
  public static void createCerts() {
    final var twoYearsAgo = Instant.now().minus(2 * 365, ChronoUnit.DAYS);
    final var oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
    final var oneYearFromNow = Instant.now().plus(365, ChronoUnit.DAYS);
    final var twoYearsFromNow = Instant.now().plus(2 * 365, ChronoUnit.DAYS);
    validCert = CertificateGenerator.generateCertificate(oneYearAgo, oneYearFromNow);
    validCert2 = CertificateGenerator.generateCertificate(oneYearAgo, oneYearFromNow);
    expiredCert = CertificateGenerator.generateCertificate(twoYearsAgo, oneYearAgo);
    notYetValidCert = CertificateGenerator.generateCertificate(oneYearFromNow, twoYearsFromNow);
  }

  @BeforeEach
  public void setUp() {
    certificateManager = new SimpleCertificateManager();
    trustManager = new TofuClientTrustManager(certificateManager);
  }

  @Test
  public void testCheckServerTrusted_withSSLEngineAndNewCertificate_trustsNewCertificate() {
    assertThatCode(
            () ->
                trustManager.checkServerTrusted(
                    new X509Certificate[] {validCert}, "RSA", mockEngine("gemini.test")))
        .doesNotThrowAnyException();
    assertThat(certificateManager.certificates()).isEqualTo(Map.of("gemini.test", validCert));
  }

  @Test
  public void testCheckServerTrusted_withSSLSocketAndNewCertificate_trustsNewCertificate() {
    assertThatCode(
            () ->
                trustManager.checkServerTrusted(
                    new X509Certificate[] {validCert}, "RSA", mockSocket("gemini.test")))
        .doesNotThrowAnyException();
    assertThat(certificateManager.certificates()).isEqualTo(Map.of("gemini.test", validCert));
  }

  @Test
  public void testCheckServerTrusted_withOldCertificateExpired_trustsNewCertificate() {
    certificateManager.setCertificate("gemini.test", expiredCert);
    assertThatCode(
            () ->
                trustManager.checkServerTrusted(
                    new X509Certificate[] {validCert}, "RSA", mockEngine("gemini.test")))
        .doesNotThrowAnyException();
    assertThat(certificateManager.certificates()).isEqualTo(Map.of("gemini.test", validCert));
  }

  @Test
  public void testCheckServerTrusted_withOldCertificateNotYetValid_trustsNewCertificate() {
    certificateManager.setCertificate("gemini.test", notYetValidCert);
    assertThatCode(
            () ->
                trustManager.checkServerTrusted(
                    new X509Certificate[] {validCert}, "RSA", mockEngine("gemini.test")))
        .doesNotThrowAnyException();
    assertThat(certificateManager.certificates()).isEqualTo(Map.of("gemini.test", validCert));
  }

  @Test
  public void testCheckServerTrusted_withNewCertificateExpired_throwsCertificateExpiredException() {
    assertThatThrownBy(
            () ->
                trustManager.checkServerTrusted(
                    new X509Certificate[] {expiredCert}, "RSA", mockEngine("gemini.test")))
        .isInstanceOf(CertificateExpiredException.class);
    assertThat(certificateManager.certificates()).isEmpty();
  }

  @Test
  public void
      testCheckServerTrusted_withNewCertificateNotYetValid_throwsCertificateNotYetValidException() {
    assertThatThrownBy(
            () ->
                trustManager.checkServerTrusted(
                    new X509Certificate[] {notYetValidCert}, "RSA", mockEngine("gemini.test")))
        .isInstanceOf(CertificateNotYetValidException.class);
    assertThat(certificateManager.certificates()).isEmpty();
  }

  @Test
  public void
      testCheckServerTrusted_withCertificateNotMatching_throwsCertificateChangedException() {
    certificateManager.setCertificate("gemini.test", validCert);
    assertThatThrownBy(
            () ->
                trustManager.checkServerTrusted(
                    new X509Certificate[] {validCert2}, "RSA", mockEngine("gemini.test")))
        .isInstanceOfSatisfying(
            CertificateChangedException.class,
            e -> {
              assertThat(e.host()).isEqualTo("gemini.test");
              assertThat(e.newCertificate()).isEqualTo(validCert2);
              assertThat(e.trustedCertificate()).isEqualTo(validCert);
            });
    assertThat(certificateManager.certificates()).isEqualTo(Map.of("gemini.test", validCert));
  }

  @SuppressWarnings("SameParameterValue")
  private SSLEngine mockEngine(final String host) {
    final var engine = mock(SSLEngine.class);
    final var session = mockSession(host);
    when(engine.getHandshakeSession()).thenReturn(session);
    return engine;
  }

  @SuppressWarnings("SameParameterValue")
  private SSLSocket mockSocket(final String host) {
    final var socket = mock(SSLSocket.class);
    final var session = mockSession(host);
    when(socket.getHandshakeSession()).thenReturn(session);
    return socket;
  }

  private SSLSession mockSession(final String host) {
    final var session = mock(SSLSession.class);
    when(session.getPeerHost()).thenReturn(host);
    return session;
  }

  private static final class SimpleCertificateManager implements CertificateManager {
    private final ConcurrentMap<String, X509Certificate> certificates = new ConcurrentHashMap<>();

    public ConcurrentMap<String, X509Certificate> certificates() {
      return certificates;
    }

    @Override
    public List<String> hosts() {
      return List.copyOf(certificates.keySet());
    }

    @Override
    public Optional<X509Certificate> getCertificate(final String host) {
      return Optional.ofNullable(certificates.get(host));
    }

    @Override
    public void setCertificate(final String host, final X509Certificate certificate) {
      certificates.put(host, certificate);
    }
  }
}
