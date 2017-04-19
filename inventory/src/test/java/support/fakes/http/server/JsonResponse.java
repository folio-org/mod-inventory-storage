package support.fakes.http.server;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class JsonResponse {

  //TODO: Needs a location
  public static void created(HttpServerResponse response,
                      JsonObject body) {

    response(response, body, 201);
  }

  public static void success(HttpServerResponse response,
                             JsonObject body) {

    response(response, body, 200);
  }

  private static void response(HttpServerResponse response,
                               JsonObject body,
                               int statusCode) {

    String json = Json.encodePrettily(body);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(statusCode);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    response.write(buffer);
    response.end();
  }
}
