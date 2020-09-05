package xyz.ianjohnson.gemini.browser;

import java.util.EventListener;

public interface NavigationListener extends EventListener {
  void navigated(NavigationEvent e);
}
