package xyz.ianjohnson.gemini.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import xyz.ianjohnson.gemini.StandardGeminiStatus;

public class RouterTest {
  private static final SocketAddress TEST_SOCKET_ADDRESS = new SocketAddress() {};

  private static GeminiRequest newRequest(final String uri) {
    return GeminiRequest.newBuilder()
        .localAddress(TEST_SOCKET_ADDRESS)
        .remoteAddress(TEST_SOCKET_ADDRESS)
        .uri(URI.create(uri))
        .build();
  }

  @Test
  public void testApply_withExactMatch_appliesHandler() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addExactRoute("/path", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/path"))).isEqualTo(response);
  }

  @Test
  public void testApply_withExactMatchAndPrefixMatch_usesExactMatch() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var response2 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test2");
    final var router =
        Router.newBuilder()
            .addExactRoute("/path", r -> response)
            .addPrefixRoute("/path", r -> response2)
            .build();

    assertThat(router.apply(newRequest("gemini://localhost/path"))).isEqualTo(response);
  }

  @Test
  public void testApply_withOnlyExactRoutesAndNoneMatching_returnsNotFound() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var response2 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test2");
    final var router =
        Router.newBuilder()
            .addExactRoute("/path", r -> response)
            .addExactRoute("/path2", r -> response2)
            .build();

    assertThat(router.apply(newRequest("gemini://localhost/path3")))
        .isEqualTo(GeminiResponse.of(StandardGeminiStatus.NOT_FOUND, "Not found"));
  }

  @Test
  public void testApply_withExactRouteEndingInSlash_doesNotRedirectNonSlashRequest() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addExactRoute("/tree/", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/tree")))
        .isEqualTo(GeminiResponse.of(StandardGeminiStatus.NOT_FOUND, "Not found"));
  }

  @Test
  public void testApply_withExactPrefixPathMatch_appliesHandler() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addPrefixRoute("/path", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/path"))).isEqualTo(response);
  }

  @Test
  public void testApply_withExactPrefixTreePathMatch_appliesHandler() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router =
        Router.newBuilder().addPrefixRoute("/directory/path/", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/directory/path/"))).isEqualTo(response);
  }

  @Test
  public void testApply_withPrefixPathMatch_appliesHandler() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router =
        Router.newBuilder().addPrefixRoute("/directory/path/", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/directory/path/subpath")))
        .isEqualTo(response);
  }

  @Test
  public void testApply_withPrefixTreePathMatch_appliesHandler() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router =
        Router.newBuilder().addPrefixRoute("/directory/path/", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/directory/path/subpath")))
        .isEqualTo(response);
  }

  @Test
  public void testApply_withSeveralCandidatePrefixPaths_choosesLongestPrefix() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var response2 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test2");
    final var response3 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test3");
    final var router =
        Router.newBuilder()
            .addPrefixRoute("/", r -> response)
            .addPrefixRoute("/path/", r -> response2)
            .addPrefixRoute("/path/other/", r -> response3)
            .build();

    assertThat(router.apply(newRequest("gemini://localhost/path/otherpath"))).isEqualTo(response2);
  }

  @Test
  public void testApply_withManyPrefixRoutes_choosesLongestPrefixMatchingRequestPath() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var response2 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test2");
    final var response3 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test3");
    final var response4 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test4");
    final var response5 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test5");
    final var router =
        Router.newBuilder()
            .addPrefixRoute("/", r -> response)
            .addPrefixRoute("/path/", r -> response2)
            .addPrefixRoute("/path/other/", r -> response3)
            .addPrefixRoute("/path/other/path", r -> response4)
            .addPrefixRoute("/tree/other", r -> response5)
            .build();

    assertThat(router.apply(newRequest("gemini://localhost/path/other1path"))).isEqualTo(response2);
  }

  @Test
  public void testApply_withPrefixPathNotEndingInSlash_doesNotForcePrefixToPathBoundary() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var response2 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test2");
    final var router =
        Router.newBuilder()
            .addPrefixRoute("/tree/", r -> response)
            .addPrefixRoute("/tree/path", r -> response2)
            .build();

    assertThat(router.apply(newRequest("gemini://localhost/tree/path2"))).isEqualTo(response2);
  }

  @Test
  public void testApply_withNonTreeUri_choosesExactNonTreePrefixOverTreePrefix() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var response2 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test2");
    final var router =
        Router.newBuilder()
            .addPrefixRoute("/tree", r -> response)
            .addPrefixRoute("/tree/", r -> response2)
            .build();

    assertThat(router.apply(newRequest("gemini://localhost/tree"))).isEqualTo(response);
  }

  @Test
  public void testApply_withNonTreeRequestAndTreePrefixMatch_returnsRedirectWithTrailingSlash() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addPrefixRoute("/path/", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/path")))
        .isEqualTo(GeminiResponse.of(StandardGeminiStatus.PERMANENT_REDIRECT, "/path/"));
  }

  @Test
  public void testApply_withNonTreeRootRequestAndTreeRootPrefix_returnsRedirectWithTrailingSlash() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addPrefixRoute("/", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost")))
        .isEqualTo(GeminiResponse.of(StandardGeminiStatus.PERMANENT_REDIRECT, "/"));
  }

  @Test
  public void testApply_withNonTreeRootRequestAndNonTreeRootPrefix_appliesHandler() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addPrefixRoute("", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost"))).isEqualTo(response);
  }

  @Test
  public void testApply_withNoPrefixMatch_returnsNotFound() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var response2 = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test2");
    final var router =
        Router.newBuilder()
            .addPrefixRoute("/path/", r -> response)
            .addPrefixRoute("/path2/", r -> response2)
            .build();

    assertThat(router.apply(newRequest("gemini://localhost/path3/")))
        .isEqualTo(GeminiResponse.of(StandardGeminiStatus.NOT_FOUND, "Not found"));
  }

  @Test
  public void testApply_withNonNormalizedUriAndNormalizedMatch_redirectsToNormalizedUri() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addPrefixRoute("/tree/path/", r -> response).build();

    assertThat(
            router.apply(newRequest("gemini://localhost///../../tree/./subtree/../path/subpath")))
        .isEqualTo(
            GeminiResponse.of(StandardGeminiStatus.PERMANENT_REDIRECT, "/tree/path/subpath"));
  }

  @Test
  public void testApply_withNonNormalizedUriAndNoMatchOnNormalizedUri_returnsNotFound() {
    final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
    final var router = Router.newBuilder().addPrefixRoute("/tree/path/", r -> response).build();

    assertThat(router.apply(newRequest("gemini://localhost/././././tree")))
        .isEqualTo(GeminiResponse.of(StandardGeminiStatus.NOT_FOUND, "Not found"));
  }

  public static class BuilderTest {
    @Test
    public void testAddExactRoute_withNonNormalizedUri_normalizesUri() {
      final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
      final var router =
          Router.newBuilder().addExactRoute("/../..///test/./../", r -> response).build();

      assertThat(router.apply(newRequest("gemini://localhost/"))).isEqualTo(response);
    }

    @Test
    public void testAddExactRoute_withDuplicateRoute_throwsIllegalStateException() {
      final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
      final var builder = Router.newBuilder().addExactRoute("/route", r -> response);

      assertThatThrownBy(() -> builder.addExactRoute("/route", r -> response))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Exact route already defined: /route");
    }

    @Test
    public void testAddPrefixRoute_withNonNormalizedUri_normalizesUri() {
      final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
      final var router =
          Router.newBuilder().addPrefixRoute("/../..///test/./../prefix/", r -> response).build();

      assertThat(router.apply(newRequest("gemini://localhost/prefix/path"))).isEqualTo(response);
    }

    @Test
    public void testAddPrefixRoute_withDuplicateRoute_throwsIllegalStateException() {
      final var response = GeminiResponse.of(StandardGeminiStatus.INPUT, "Test");
      final var builder = Router.newBuilder().addPrefixRoute("/route/", r -> response);

      assertThatThrownBy(() -> builder.addPrefixRoute("/route/", r -> response))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Prefix route already defined: /route/");
    }
  }
}
