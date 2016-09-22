package org.folio.metadata.common

import io.vertx.groovy.ext.web.RoutingContext

class WebRequestDiagnostics {

  static void outputDiagnostics(RoutingContext routingContext) {

    printf "Handling %s\n", routingContext.normalisedPath()
    printf "Method: %s\n", routingContext.request().rawMethod()

    outputHeaders routingContext

    routingContext.next()
  }

  private static void outputHeaders(RoutingContext routingContext) {
    println "Headers"

    for (def name : routingContext.request().headers().names()) {
      for (def entry : routingContext.request().headers().getAll(name))
        printf "%s : %s\n", name, entry
    }

    println()
  }
}
