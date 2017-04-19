package support.fakes.http.server;

import io.vertx.core.http.HttpServerResponse;
import org.apache.http.entity.ContentType;

public class ClientErrorResponse {
  public static void notFound(HttpServerResponse response) {
    response.setStatusCode(404);
    response.end();
  }

  public static void badRequest(HttpServerResponse response, String reason) {
    response.setStatusCode(400);
    response.putHeader("content-type", ContentType.TEXT_PLAIN.toString());
    response.end(reason);
  }

  public static void forbidden(HttpServerResponse response) {
    response.setStatusCode(403);
    response.end();
  }
}
