package org.folio.metadata.common

import io.vertx.groovy.ext.web.RoutingContext

class WebContext implements Context {
  private final RoutingContext routingContext

  WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext
  }

  @Override
  String getTenantId() {
    routingContext.request().getHeader("X-Okapi-Tenant")
  }

  def getHeader(String header) {
    routingContext.request().getHeader(header)
  }
}
