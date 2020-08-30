package xyz.ianjohnson.gemini.client;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

final class TestUtils {
  private TestUtils() {}

  static SSLContext mockSSLContext(final SSLSocketFactory sslSocketFactory) {
    final var sslContextSpi =
        new SSLContextSpi() {
          @Override
          protected void engineInit(
              final KeyManager[] km, final TrustManager[] tm, final SecureRandom sr) {}

          @Override
          protected SSLSocketFactory engineGetSocketFactory() {
            return sslSocketFactory;
          }

          @Override
          protected SSLServerSocketFactory engineGetServerSocketFactory() {
            return null;
          }

          @Override
          protected SSLEngine engineCreateSSLEngine() {
            return null;
          }

          @Override
          protected SSLEngine engineCreateSSLEngine(final String host, final int port) {
            return null;
          }

          @Override
          protected SSLSessionContext engineGetServerSessionContext() {
            return null;
          }

          @Override
          protected SSLSessionContext engineGetClientSessionContext() {
            return null;
          }
        };

    return new SSLContext(sslContextSpi, null, "TEST") {};
  }

  static byte[] utf8(final String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
