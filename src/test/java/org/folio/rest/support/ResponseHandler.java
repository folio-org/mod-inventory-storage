package org.folio.rest.support;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResponseHandler {
  private static final Logger log = LogManager.getLogger();

  public static Handler<HttpResponse<Buffer>> any(
    CompletableFuture<Response> completed) {

    return responseHandler(completed,
      responseToCheck -> true,
      failingResponse -> null);
  }

  public static Handler<HttpResponse<Buffer>> empty(
    CompletableFuture<Response> completed) {

    return response -> {
      try {
        int statusCode = response.statusCode();

        completed.complete(new Response(statusCode, null, null));
      } catch (Exception e) {
        completed.completeExceptionally(e);
      }
    };
  }

  public static Handler<HttpResponse<Buffer>> json(
    CompletableFuture<Response> completed) {

    return strictContentType(completed, "application/json");
  }

  public static Handler<HttpResponse<Buffer>> text(
    CompletableFuture<Response> completed) {

    return strictContentType(completed, "text/plain");
  }

  public static Handler<HttpResponse<Buffer>> jsonErrors(
    CompletableFuture<JsonErrorResponse> completed) {

    return response -> {
      try {
        completed.complete(new JsonErrorResponse(
          response.statusCode(),
          response.bodyAsString(),
          response.headers().get(CONTENT_TYPE)));
      } catch (Exception e) {
        completed.completeExceptionally(e);
      }
    };
  }

  private static Handler<HttpResponse<Buffer>> strictContentType(
    CompletableFuture<Response> completed,
    String expectedContentType) {

    return responseHandler(completed,
      responseToCheck ->
        responseToCheck.getContentType().contains(expectedContentType),
      failingResponse -> new Exception(
        String.format("Expected Content Type: '%s' Actual Content Type: '%s' (Status Code: '%s', Body: '%s')",
          expectedContentType, failingResponse.getContentType(),
          failingResponse.getStatusCode(), failingResponse.getBody())));
  }

  private static Handler<HttpResponse<Buffer>> responseHandler(
    CompletableFuture<Response> completed,
    Predicate<Response> expectation,
    Function<Response, Throwable> expectationFailed) {

    return vertxResponse -> {
      try {
        Response response = Response.from(vertxResponse);

        log.debug("Received Response: {}: {}", response.getStatusCode(), response.getContentType());
        log.debug("Received Response Body: {}", response.getBody());

        if (expectation.test(response)) {
          completed.complete(response);
        } else {
          completed.completeExceptionally(
            expectationFailed.apply(response));
        }
      } catch (Throwable e) {
        completed.completeExceptionally(e);
      }
    };
  }

}
