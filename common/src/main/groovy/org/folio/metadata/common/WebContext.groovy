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
  String getToken() {
    getHeader("X-Okapi-Token", "")
  }

  @Override
  String getOkapiLocation() {
    getHeader("X-Okapi-Url", "")
  }

  @Override
  def getHeader(String header) {
    routingContext.request().getHeader(header)
  }

  @Override
  def getHeader(String header, defaultValue) {
    hasHeader(header) ? getHeader(header) : defaultValue
  }

  @Override
  boolean hasHeader(String header) {
    routingContext.request().headers().contains(header)
  }

  def URL absoluteUrl(String path) {
    def currentRequestUrl = new URL(routingContext.request().absoluteURI())

    //It would seem Okapi preserves headers from the original request,
    // so there is no need to use X-Okapi-Url for this?

    new URL(currentRequestUrl.protocol, currentRequestUrl.host,
      currentRequestUrl.port, path)
  }

  Integer getIntegerParameter(String name, Integer defaultValue) {
    def value = routingContext.request().getParam(name)

    value != null ? Integer.parseInt(value) : defaultValue
  }

  String getStringParameter(String name, String defaultValue) {
    def value = routingContext.request().getParam(name)

    value != null ? value : defaultValue
  }
}
