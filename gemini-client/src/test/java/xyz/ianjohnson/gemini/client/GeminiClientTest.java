package xyz.ianjohnson.gemini.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.ssl.SslContextBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;

public class GeminiClientTest {
  @Test
  public void testExecutor_withNoUserDefinedExecutor_returnsEmptyOptional() {
    try (final var client = GeminiClient.newGeminiClient()) {
      assertThat(client.executor()).isEmpty();
    }
  }

  @Test
  public void testExecutor_withUserDefinedExecutor_returnsUserDefinedExecutor() {
    final ExecutorService es = Executors.newCachedThreadPool();
    try (final var client = GeminiClient.newBuilder().executor(es).build()) {
      assertThat(client.executor()).hasValue(es);
    }
  }

  @Test
  public void testSslContext_returnsSslContext() throws SSLException {
    final var sslContext = SslContextBuilder.forClient().build();
    try (final var client = GeminiClient.newBuilder().sslContext(sslContext).build()) {
      assertThat(client.sslContext()).isEqualTo(sslContext);
    }
  }
}
