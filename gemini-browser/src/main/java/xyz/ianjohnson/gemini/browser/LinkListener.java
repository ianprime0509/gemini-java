package xyz.ianjohnson.gemini.browser;

import java.util.EventListener;

public interface LinkListener extends EventListener {
  void linkClicked(LinkEvent e);
}
