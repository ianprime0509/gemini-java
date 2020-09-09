package xyz.ianjohnson.gemini.browser;

import javax.swing.text.StyledDocument;

public interface AboutPage {
  String name();

  StyledDocument display(String path, Browser browser);
}
