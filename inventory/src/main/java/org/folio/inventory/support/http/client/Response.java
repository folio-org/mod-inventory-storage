package org.folio.inventory.support.http.client;

import io.vertx.groovy.core.buffer.Buffer;
import io.vertx.groovy.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class Response {
  protected final String body;
  private final int statusCode;
  private final String contentType;

  public Response(int statusCode, String body, String contentType) {
    this.statusCode = statusCode;
    this.body = body;
    this.contentType = contentType;
  }

  public static Response from(HttpClientResponse response, Buffer body) {
    return new Response(response.statusCode(),
      BufferHelper.stringFromBuffer(body),
      convertNullToEmpty(response.getHeader(CONTENT_TYPE.toString())));
  }

  public boolean hasBody() {
    return getBody() != null && getBody().trim() != "";
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }

  public JsonObject getJson() {
    String body = getBody();

    if(hasBody()) {
      return new JsonObject(body);
    }
    else {
      return new JsonObject();
    }
  }

  public String getContentType() {
    return contentType;
  }

  private static String convertNullToEmpty(String text) {
    return text != null ? text : "";
  }
}
