package org.folio.rest.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.CompletableFuture;

public class ResponseHandler {
  public static Handler<HttpClientResponse> empty(
    CompletableFuture<Response> completed) {

    return response -> {
      int statusCode = response.statusCode();

      completed.complete(new Response(statusCode));;
    };
  }

  public static Handler<HttpClientResponse> json(
    CompletableFuture<JsonResponse> completed) {

    return response -> {
      int statusCode = response.statusCode();

      response.bodyHandler(buffer -> {
        JsonObject body = BufferHelper.jsonObjectFromBuffer(buffer);

        completed.complete(new JsonResponse(statusCode, body));
      });
    };
  }

  public static Handler<HttpClientResponse> text(
    CompletableFuture<TextResponse> completed) {

    return response -> {
      int statusCode = response.statusCode();

      response.bodyHandler(buffer -> {
        String body = BufferHelper.stringFromBuffer(buffer);

        completed.complete(new TextResponse(statusCode, body));
      });
    };
  }
}
