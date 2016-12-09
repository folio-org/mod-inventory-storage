package org.folio.metadata.common

import io.vertx.groovy.ext.web.RoutingContext

class Context {
  private final RoutingContext routingContext


  Context(RoutingContext routingContext) {
    this.routingContext = routingContext
  }

  String getTenantId() {
    routingContext.request().getHeader("X-Okapi-Tenant")
  }

  def getHeader(String header) {
    routingContext.request().getHeader(header)
  }
}
