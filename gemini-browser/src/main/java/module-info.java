import xyz.ianjohnson.gemini.browser.DocumentRenderer;
import xyz.ianjohnson.gemini.browser.GeminiDocumentRenderer;

module xyz.ianjohnson.gemini.browser {
  requires static auto.value.annotations;
  requires static java.compiler;
  requires java.desktop;
  requires org.slf4j;
  requires xyz.ianjohnson.gemini.client;

  uses DocumentRenderer;

  provides DocumentRenderer with
      GeminiDocumentRenderer;
}
