package org.folio.metadata.common

import io.vertx.groovy.ext.web.RoutingContext

class WebContext implements Context {
  private final RoutingContext routingContext

  WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext
  }

  @Override
  String getTenantId() {
    getHeader("X-Okapi-Tenant", "")
  }

  @Override
  String getOkapiLocation() {
    getHeader("X-Okapi-Url", "")
  }

  def getHeader(String header) {
    routingContext.request().getHeader(header)
  }

  def getHeader(String header, defaultValue) {
    hasHeader(header) ? getHeader(header) : defaultValue
  }

  private boolean hasHeader(String header) {
    routingContext.request().headers().contains(header)
  }
}
