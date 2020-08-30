module xyz.ianjohnson.gemini.client {
  requires static java.compiler;
  requires static auto.value.annotations;
  requires xyz.ianjohnson.gemini;
  requires io.netty.buffer;
  requires io.netty.common;
  requires io.netty.handler;
  requires io.netty.transport;

  exports xyz.ianjohnson.gemini.client;
}
