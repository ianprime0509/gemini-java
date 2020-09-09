import xyz.ianjohnson.gemini.browser.AboutCertificates;
import xyz.ianjohnson.gemini.browser.AboutPage;
import xyz.ianjohnson.gemini.browser.DocumentRenderer;
import xyz.ianjohnson.gemini.browser.GeminiDocumentRenderer;

module xyz.ianjohnson.gemini.browser {
  requires static auto.value.annotations;
  requires static java.compiler;
  requires dev.dirs;
  requires java.desktop;
  requires org.slf4j;
  requires xyz.ianjohnson.gemini.client;

  uses AboutPage;
  uses DocumentRenderer;

  provides AboutPage with
      AboutCertificates;
  provides DocumentRenderer with
      GeminiDocumentRenderer;
}
