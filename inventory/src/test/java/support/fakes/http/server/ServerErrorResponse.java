package support.fakes.http.server;

import io.vertx.core.http.HttpServerResponse;
import org.apache.http.entity.ContentType;


public class ServerErrorResponse {
  public static void internalError(HttpServerResponse response, String reason) {
    response.setStatusCode(500);

    response.putHeader("content-type", ContentType.TEXT_PLAIN.toString());
    response.end(reason);
  }
}
