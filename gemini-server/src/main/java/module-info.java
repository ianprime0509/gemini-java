module xyz.ianjohnson.gemini.server {
  requires static auto.value.annotations;
  requires static java.compiler;
  requires transitive xyz.ianjohnson.gemini;
  requires io.netty.buffer;
  requires io.netty.codec;
  requires io.netty.common;
  requires io.netty.handler;
  requires io.netty.transport;
  requires org.slf4j;

  exports xyz.ianjohnson.gemini.server;
}
