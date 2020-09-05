module xyz.ianjohnson.gemini.client {
  requires static java.compiler;
  requires static auto.value.annotations;
  requires transitive xyz.ianjohnson.gemini;
  requires io.netty.buffer;
  requires io.netty.common;
  requires io.netty.handler;
  requires io.netty.transport;
  requires org.slf4j;

  exports xyz.ianjohnson.gemini.client;
}
