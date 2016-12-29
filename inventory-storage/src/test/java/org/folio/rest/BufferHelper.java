package org.folio.rest;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class BufferHelper {

  static JsonObject jsonObjectFromBuffer(Buffer buffer) {
    return new JsonObject(stringFromBuffer(buffer));
  }

  static String stringFromBuffer(Buffer buffer) {
    return buffer.getString(0, buffer.length());
  }
}
