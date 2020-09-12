package xyz.ianjohnson.gemini.server.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.Callable;
import javax.net.ssl.KeyManagerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.StandardGeminiStatus;
import xyz.ianjohnson.gemini.server.GeminiResponse;
import xyz.ianjohnson.gemini.server.GeminiResponse.BodyPublishers;
import xyz.ianjohnson.gemini.server.GeminiServer;

@Command(name = "gemini-server")
public final class GeminiServerCli implements Callable<Integer> {
  @Parameters(index = "0")
  private Path keyStorePath;

  public static void main(final String... args) {
    new CommandLine(new GeminiServerCli()).execute(args);
  }

  @Override
  public Integer call() throws Exception {
    final var keyStore = KeyStore.getInstance("PKCS12");
    try (final var is = Files.newInputStream(keyStorePath)) {
      keyStore.load(is, "changeit".toCharArray());
    }
    final var keyManagerFactory = KeyManagerFactory.getInstance("PKIX");
    keyManagerFactory.init(keyStore, "changeit".toCharArray());

    final var server =
        GeminiServer.newBuilder(
                r ->
                    GeminiResponse.of(
                        StandardGeminiStatus.SUCCESS,
                        MimeType.TEXT_GEMINI.toString(),
                        BodyPublishers.ofString("Hello, world!")),
                keyManagerFactory)
            .build();
    server.start().get();
    server.closeFuture().get();
    return 0;
  }
}
