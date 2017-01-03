package org.folio.metadata.common.api.request

import io.vertx.groovy.ext.web.RoutingContext

class VertxBodyParser {
  def toMap(RoutingContext routingContext) {
    println("Received Body: ${routingContext.bodyAsString}")

    if (hasBody(routingContext)) {
      routingContext.getBodyAsJson()
    } else {
      new HashMap<String, Object>()
    }
  }

  private static boolean hasBody(RoutingContext routingContext) {
    routingContext.bodyAsString != null &&
      routingContext.bodyAsString.trim()
  }

}
