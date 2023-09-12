package org.folio.rest.support.http.client;

import io.vertx.core.buffer.Buffer;

public class BufferHelper {
  public static String stringFromBuffer(Buffer buffer) {
    if (buffer.length() == 0) {
      return "";
    }

    return buffer.getString(0, buffer.length());
  }
}
