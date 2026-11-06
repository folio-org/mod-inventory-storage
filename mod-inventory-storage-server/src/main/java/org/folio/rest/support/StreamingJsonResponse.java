package org.folio.rest.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClientStreamResult;

/**
 * Writes a database stream as a JSON document with the records in an array property, {@code totalRecords}
 * and {@code resultInfo}, without holding all records in memory. The output is the same as the one of RMB's
 * {@code PgUtil.streamGet}.
 */
public final class StreamingJsonResponse {

  private static final Logger log = LogManager.getLogger();
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperTool.getMapper();
  private static final String TEXT_PLAIN = "text/plain";

  private StreamingJsonResponse() {
  }

  /**
   * Streams the records, {@code element} is the name of the property that holds the array of records.
   */
  public static <T> void write(PostgresClientStreamResult<T> result, String element, HttpServerResponse response) {
    response.setStatusCode(200);
    response.setChunked(true);
    response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    response.write("{\n");
    response.write(String.format("  \"%s\": [%n", element));
    var first = new AtomicBoolean(true);
    result.exceptionHandler(error -> {
      result.resultInfo().setDiagnostics(List.of(new Diagnostic().withCode("500").withMessage(error.getMessage())));
      writeTrailer(response, result.resultInfo());
    });
    result.endHandler(end -> writeTrailer(response, result.resultInfo()));
    result.handler(item -> writeRecord(item, first, response));
  }

  /**
   * Ends the response with a text/plain failure: 400 when the failure is caused by the request (for example an
   * invalid query), 500 otherwise.
   */
  public static void writeFailure(Throwable failure, HttpServerResponse response) {
    log.error(failure.getMessage(), failure);
    var badRequest = PgExceptionUtil.badRequestMessage(failure) != null
      || failure instanceof org.folio.rest.exceptions.BadRequestException;
    response.setStatusCode(badRequest ? 400 : 500);
    response.putHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN);
    response.end(failure instanceof org.folio.rest.exceptions.BadRequestException ? failure.getMessage()
      : failure.toString());
  }

  private static void writeRecord(Object item, AtomicBoolean first, HttpServerResponse response) {
    String json;
    try {
      json = OBJECT_MAPPER.writeValueAsString(item);
    } catch (JsonProcessingException e) {
      log.error(e.getMessage(), e);
      throw new IllegalArgumentException(e.getCause());
    }
    if (!first.getAndSet(false)) {
      response.write(String.format(",%n"));
    }
    response.write(json);
  }

  private static void writeTrailer(HttpServerResponse response, ResultInfo resultInfo) {
    response.write("],\n");
    if (resultInfo.getTotalRecords() != null) {
      response.write("  \"totalRecords\": " + resultInfo.getTotalRecords() + ",\n");
    }
    response.end("  \"resultInfo\": " + Json.encode(resultInfo) + "\n}");
  }
}
