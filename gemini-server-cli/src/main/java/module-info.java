module xyz.ianjohnson.gemini.server.cli {
  requires info.picocli;
  requires xyz.ianjohnson.gemini.server;

  exports xyz.ianjohnson.gemini.server.cli;

  opens xyz.ianjohnson.gemini.server.cli to
      info.picocli;
}
