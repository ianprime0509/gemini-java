package xyz.ianjohnson.gemini.server;

import com.google.auto.value.AutoValue;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import xyz.ianjohnson.gemini.Nullable;

@AutoValue
public abstract class GeminiRequest {
  GeminiRequest() {}

  public static Builder newBuilder() {
    return new AutoValue_GeminiRequest.Builder();
  }

  /**
   * Normalizes the given URI path by performing the following operations:
   *
   * <ol>
   *   <li>Decodes any percent-encoded sequences that do not need to be encoded. Such characters are
   *       those that will not be re-encoded by the {@link URI#URI(String, String, String, String)}
   *       constructor and friends (except for the {@code /} character, which remains encoded to
   *       prevent encoded slashes from being confused with path separators). Invalid UTF-8
   *       sequences are not decoded.
   *   <li>Capitalizes any remaining percent-encoded sequences (for example, {@code %2f} becomes
   *       {@code %2F}).
   *   <li>Resolves any relative {@code .} and {@code ..} segments in the path. Unlike the {@link
   *       URI#normalize()} method, this method removes any extra {@code ..} segments completely
   *       rather than retaining them at the beginning of the path.
   *   <li>Removes any empty segments, such as {@code //} (there will be no adjacent forward slashes
   *       in the normalized path).
   *   <li>Prepends a leading slash ({@code /}) if there is none already. In particular, the empty
   *       string is normalized to {@code /}.
   * </ol>
   *
   * The path is assumed to be a valid URI path already. This method does not encode any characters
   * that should be encoded but aren't already: it only decodes characters that were unnecessarily
   * encoded.
   *
   * @param path the path to normalize. The path is assumed to be absolute: if no leading {@code /}
   *     is present, one will be prepended to the resulting path. {@code null} is treated as an
   *     empty path.
   * @return the normalized path. The normalized path ends in {@code /} if and only if the original
   *     path does, except that if the original path is empty, the normalized path is {@code /}.
   * @throws IllegalArgumentException if there are any illegal escaped characters in the given path
   *     (such as {@code %2G})
   */
  public static String normalizePath(@Nullable final String path) {
    if (path == null || path.isEmpty()) {
      return "/";
    }

    final var comps = new ArrayList<String>(8);
    for (final var comp : unescapePath(path).split("/")) {
      if ("..".equals(comp)) {
        if (!comps.isEmpty()) {
          comps.remove(comps.size() - 1);
        }
      } else if (!".".equals(comp) && !comp.isEmpty()) {
        comps.add(comp);
      }
    }
    if (path.endsWith("/")) {
      comps.add("");
    }
    return "/" + String.join("/", comps);
  }

  private static String unescapePath(final String path) {
    final var sb = new StringBuilder();
    // Each encoded octet takes up three bytes/chars, so there can be at most length / 3 encoded
    // octets in the path
    final var bytes = ByteBuffer.allocate(path.length() / 3);
    final var chars = CharBuffer.allocate(path.length() / 3);
    final var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .onMalformedInput(CodingErrorAction.REPORT);

    for (var i = 0; i < path.length(); i++) {
      if (path.charAt(i) == '%') {
        if (i + 2 >= path.length()) {
          throw new IllegalArgumentException("Incomplete encoded octet");
        }
        final var n1 = Character.digit(path.charAt(i + 1), 16);
        final var n2 = Character.digit(path.charAt(i + 2), 16);
        if (n1 == -1 || n2 == -1) {
          throw new IllegalArgumentException("Invalid encoded octet");
        }
        bytes.put((byte) (16 * n1 + n2));
        i += 2;
      } else {
        if (bytes.position() > 0) {
          // Reached end of encoded octet sequence
          handleEncodedOctetSequence(sb, bytes, chars, decoder);
        }
        sb.append(path.charAt(i));
      }
    }
    if (bytes.position() > 0) {
      // Path ended with encoded octet sequence
      handleEncodedOctetSequence(sb, bytes, chars, decoder);
    }

    return sb.toString();
  }

  private static void handleEncodedOctetSequence(
      final StringBuilder sb,
      final ByteBuffer bytes,
      final CharBuffer chars,
      final CharsetDecoder decoder) {
    bytes.flip();
    CoderResult res;

    do {
      res = decoder.decode(bytes, chars, true);
      chars.flip();
      chars
          .codePoints()
          .forEach(
              c -> {
                if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || "_-!.~'()*,;:$&+=@".indexOf(c) != -1
                    || (c >= 0x80 && !Character.isISOControl(c) && !Character.isSpaceChar(c))) {
                  sb.appendCodePoint(c);
                } else {
                  // Re-encode character. This doesn't seem very efficient (compared to the rest of
                  // the method), but it probably doesn't matter. It can be revisited later.
                  final var encoded =
                      StandardCharsets.UTF_8.encode(new String(new int[] {c}, 0, 1));
                  for (var j = 0; j < encoded.limit(); j++) {
                    encodeOctet(sb, encoded.get(j));
                  }
                }
              });
      chars.clear();

      // Any error bytes need to be re-encoded
      if (res.isError()) {
        for (var i = 0; i < res.length(); i++) {
          encodeOctet(sb, bytes.get());
        }
      }
    } while (bytes.hasRemaining());

    bytes.clear();
    decoder.reset();
  }

  private static void encodeOctet(final StringBuilder sb, final byte b) {
    // Why are Java bytes signed :(
    final var bUnsigned = (b + 256) % 256;
    final var n1 = bUnsigned / 16;
    final var n2 = bUnsigned % 16;
    sb.append('%');
    sb.append((char) (n1 >= 10 ? n1 - 10 + 'A' : n1 + '0'));
    sb.append((char) (n2 >= 10 ? n2 - 10 + 'A' : n2 + '0'));
  }

  /** The local (server) address that received the request. */
  public abstract SocketAddress localAddress();

  /** The remote (client) address that sent the request. */
  public abstract SocketAddress remoteAddress();

  /** The requested URI. */
  public abstract URI uri();

  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder localAddress(SocketAddress localAddress);

    public abstract Builder remoteAddress(SocketAddress remoteAddress);

    public abstract Builder uri(URI uri);

    public final GeminiRequest build() {
      if (uri().getHost() == null) {
        throw new IllegalArgumentException("Host is required");
      } else if (uri().getUserInfo() != null) {
        throw new IllegalArgumentException("Userinfo is not allowed");
      }
      return autoBuild();
    }

    abstract URI uri();

    abstract GeminiRequest autoBuild();
  }
}
