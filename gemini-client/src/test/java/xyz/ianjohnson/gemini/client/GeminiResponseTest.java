package xyz.ianjohnson.gemini.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xyz.ianjohnson.gemini.client.TestUtils.utf8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.MimeType;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodyHandlers;
import xyz.ianjohnson.gemini.client.GeminiResponse.BodySubscribers;

public class GeminiResponseTest {
  private static final int TIMEOUT_MILLISECONDS = 1000;

  private static <T> T getResult(final CompletionStage<T> completionStage) throws Throwable {
    try {
      return completionStage.toCompletableFuture().get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
    } catch (final ExecutionException e) {
      throw e.getCause();
    }
  }

  public static class BodyHandlersTest {
    @Test
    public void testDiscarding_returnsBodySubscriberDiscardingAllData() throws Throwable {
      final var discarding =
          BodyHandlers.discarding().apply(MimeType.of("application", "octet-stream"));
      final var result = discarding.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(discarding);
        publisher.submit(List.of(ByteBuffer.wrap(new byte[500])));
      }
      //noinspection ConstantConditions
      assertThat(getResult(result)).isNull();
    }

    @Test
    public void testOfByteArray_returnsBodySubscriberCollectingDataIntoByteArray()
        throws Throwable {
      final var ofByteArray =
          BodyHandlers.ofByteArray().apply(MimeType.of("application", "octet-stream"));
      final var bytes = new byte[1024];
      for (var i = 0; i < bytes.length; i++) {
        bytes[i] = (byte) i;
      }
      final var result = ofByteArray.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(ofByteArray);
        publisher.submit(List.of(ByteBuffer.wrap(bytes)));
      }
      assertThat(getResult(result)).containsExactly(bytes);
    }

    @Test
    public void
        testOfString_withNoCharsetSpecifiedInMimeType_returnsBodySubscriberCollectingDataIntoUtf8String()
            throws Throwable {
      final var ofString = BodyHandlers.ofString().apply(MimeType.of("text", "gemini"));
      final var result = ofString.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(ofString);
        publisher.submit(List.of(ByteBuffer.wrap(utf8("Hello, world!\n"))));
      }
      assertThat(getResult(result)).isEqualTo("Hello, world!\n");
    }
  }

  @Test
  public void testOfString_withCharsetInMimeType_returnsBodySubscriberCollectingDataIntoString()
      throws Throwable {
    final var ofString =
        BodyHandlers.ofString()
            .apply(MimeType.of("text", "gemini", Map.of("charset", "iso-8859-1")));
    final var result = ofString.getBody();
    try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
      publisher.subscribe(ofString);
      publisher.submit(
          List.of(
              ByteBuffer.wrap(
                  "Les élèves vont à l'école\n".getBytes(StandardCharsets.ISO_8859_1))));
    }
    assertThat(getResult(result)).isEqualTo("Les élèves vont à l'école\n");
  }

  public static class BodySubscribersTest {
    @Test
    public void testDiscarding_withSuccessfulData_discardsAllData() throws Throwable {
      final var discarding = BodySubscribers.discarding();
      final var result = discarding.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(discarding);
        for (var i = 0; i < 100; i++) {
          publisher.submit(List.of(ByteBuffer.wrap(utf8("Data\n"))));
        }
      }
      //noinspection ConstantConditions
      assertThat(getResult(result)).isNull();
    }

    @Test
    public void testDiscarding_withFailure_resultsInFailure() {
      final var discarding = BodySubscribers.discarding();
      final var result = discarding.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(discarding);
        for (var i = 0; i < 100; i++) {
          publisher.submit(List.of(ByteBuffer.wrap(utf8("Data\n"))));
        }
        publisher.closeExceptionally(new IOException("Read error"));
      }
      assertThatThrownBy(() -> getResult(result))
          .isInstanceOf(IOException.class)
          .hasMessage("Read error");
    }

    @Test
    public void testOfByteArray_withSuccessfulData_collectsDataIntoByteArray() throws Throwable {
      final var ofByteArray = BodySubscribers.ofByteArray();
      final var result = ofByteArray.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(ofByteArray);
        for (var i = 0; i < 100; i++) {
          publisher.submit(List.of(ByteBuffer.wrap(utf8("Data\n"))));
        }
      }
      assertThat(getResult(result)).containsExactly(utf8("Data\n".repeat(100)));
    }

    @Test
    public void testOfByteArray_withFailure_resultsInFailure() {
      final var ofByteArray = BodySubscribers.ofByteArray();
      final var result = ofByteArray.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(ofByteArray);
        for (var i = 0; i < 100; i++) {
          publisher.submit(List.of(ByteBuffer.wrap(utf8("Data\n"))));
        }
        publisher.closeExceptionally(new IOException("Read error"));
      }
      assertThatThrownBy(() -> getResult(result))
          .isInstanceOf(IOException.class)
          .hasMessage("Read error");
    }

    @Test
    public void testOfString_withSuccessfulData_collectsDataIntoString() throws Throwable {
      final var ofString = BodySubscribers.ofString(StandardCharsets.UTF_8);
      final var result = ofString.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(ofString);
        for (var i = 0; i < 100; i++) {
          publisher.submit(List.of(ByteBuffer.wrap(utf8("Data\n"))));
        }
      }
      assertThat(getResult(result)).isEqualTo("Data\n".repeat(100));
    }

    @Test
    public void testOfString_withFailure_resultsInFailure() {
      final var ofString = BodySubscribers.ofString(StandardCharsets.UTF_8);
      final var result = ofString.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(ofString);
        for (var i = 0; i < 100; i++) {
          publisher.submit(List.of(ByteBuffer.wrap(utf8("Data\n"))));
        }
        publisher.closeExceptionally(new IOException("Read error"));
      }
      assertThatThrownBy(() -> getResult(result))
          .isInstanceOf(IOException.class)
          .hasMessage("Read error");
    }

    @Test
    public void testMapping_withSuccessfulData_appliesFinisherToSubscribedData() throws Throwable {
      final var mapping =
          BodySubscribers.mapping(
              BodySubscribers.ofString(StandardCharsets.UTF_8), Integer::parseInt);
      final var result = mapping.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(mapping);
        publisher.submit(List.of(ByteBuffer.wrap(utf8("10"))));
      }
      assertThat(getResult(result)).isEqualTo(10);
    }

    @Test
    public void testMapping_withFailure_resultsInFailure() {
      final var mapping =
          BodySubscribers.mapping(
              BodySubscribers.ofString(StandardCharsets.UTF_8), Integer::parseInt);
      final var result = mapping.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(mapping);
        publisher.submit(List.of(ByteBuffer.wrap(utf8("10"))));
        publisher.closeExceptionally(new IOException("Read error"));
      }
      assertThatThrownBy(() -> getResult(result))
          .isInstanceOf(IOException.class)
          .hasMessage("Read error");
    }

    @Test
    public void testMapping_withFailureInFinisher_resultsInFailure() {
      final var mapping =
          BodySubscribers.mapping(
              BodySubscribers.ofString(StandardCharsets.UTF_8), Integer::parseInt);
      final var result = mapping.getBody();
      try (final var publisher = new SubmissionPublisher<List<ByteBuffer>>()) {
        publisher.subscribe(mapping);
        publisher.submit(List.of(ByteBuffer.wrap(utf8("Not a number"))));
      }
      assertThatThrownBy(() -> getResult(result)).isInstanceOf(NumberFormatException.class);
    }
  }
}
