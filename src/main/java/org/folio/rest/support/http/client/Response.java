package org.folio.rest.support.http.client;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

public class Response {
  protected final String body;
  private final int statusCode;
  private final String contentType;
  private final String location;

  public Response(int statusCode, String body, String contentType, String location) {
    this.statusCode = statusCode;
    this.body = body;
    this.contentType = contentType;
    this.location = location;
  }

  public static Response from(HttpClientResponse response, Buffer body) {
    return new Response(response.statusCode(),
      BufferHelper.stringFromBuffer(body),
      convertNullToEmpty(response.getHeader(CONTENT_TYPE.toString())),
      response.getHeader("Location"));
  }

  public boolean hasBody() {
    return getBody() != null && !getBody().trim().equals("");
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }

  public JsonObject getJson() {
    String body = getBody();

    if (hasBody()) {
      return new JsonObject(body);
    } else {
      return new JsonObject();
    }
  }

  public String getContentType() {
    return contentType;
  }

  public String getLocation() {
    return this.location;
  }

  private static String convertNullToEmpty(String text) {
    return text != null ? text : "";
  }
}
