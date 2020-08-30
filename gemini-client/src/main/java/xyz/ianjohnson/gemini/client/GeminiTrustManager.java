package xyz.ianjohnson.gemini.client;

import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/** TODO: make this configurable and secure. */
final class GeminiTrustManager implements X509TrustManager {
  GeminiTrustManager() {}

  @Override
  public void checkClientTrusted(final X509Certificate[] chain, final String authType) {}

  @Override
  public void checkServerTrusted(final X509Certificate[] chain, final String authType) {}

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }
}
