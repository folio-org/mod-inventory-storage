package org.folio.metadata.common.api.response

import io.vertx.groovy.core.http.HttpServerResponse
import org.folio.metadata.common.domain.Failure

import java.util.function.Consumer

class FailureResponseConsumer {
  static Consumer<Failure> serverError(HttpServerResponse response) {
    return { Failure failure ->
      ServerErrorResponse.internalError(response, failure.reason) }
  }
}
