package xyz.ianjohnson.gemini;

import static java.util.stream.Collectors.toUnmodifiableMap;

import com.google.auto.value.AutoValue;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/** A MIME type, as defined in RFC 2045. */
@AutoValue
public abstract class MimeType {
  public static final MimeType TEXT_GEMINI = of("text", "gemini");

  MimeType() {}

  /** Returns a {@link MimeType} with the given type and subtype. */
  public static MimeType of(final String type, final String subtype) {
    return of(type, subtype, Map.of());
  }

  /** Returns a {@link MimeType} with the given type, subtype and parameters. */
  public static MimeType of(
      final String type, final String subtype, final Map<String, String> parameters) {
    return new AutoValue_MimeType(
        type.toLowerCase(),
        subtype.toLowerCase(),
        parameters.entrySet().stream()
            .collect(toUnmodifiableMap(e -> e.getKey().toLowerCase(), Entry::getValue)));
  }

  /** Returns a {@link MimeType} parsed from the given value. */
  public static MimeType parse(final String value) throws MimeTypeSyntaxException {
    return new MimeTypeParser(value).parse();
  }

  /** The media type, for example {@code text}. This is always a lowercase value. */
  public abstract String type();

  /** The media subtype, for example {@code gemini}. This is always a lowercase value. */
  public abstract String subtype();

  /** Returns the value of the given parameter. */
  public final Optional<String> parameter(final String key) {
    return Optional.ofNullable(parameters().get(key.toLowerCase()));
  }

  /** The parameters, with lowercase keys. */
  abstract Map<String, String> parameters();

  /** Returns whether this MIME type is the same as {@code other}, ignoring any parameters. */
  public final boolean sameType(final MimeType other) {
    return type().equals(other.type()) && subtype().equals(other.subtype());
  }
}
