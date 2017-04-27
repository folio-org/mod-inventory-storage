package org.folio.metadata.common

import io.vertx.groovy.core.MultiMap

class MessagingContext implements Context {

  private final MultiMap headers

  MessagingContext(final MultiMap headers) {
    this.headers = headers
  }

  @Override
  String getTenantId() {
    getHeader("tenantId")
  }

  @Override
  String getToken() {
    getHeader("token")
  }

  @Override
  String getOkapiLocation() {
    getHeader("okapiLocation")
  }

  @Override
  def getHeader(String header) {
    headers.get(header)
  }

  @Override
  def getHeader(String header, Object defaultValue) {
    hasHeader(header) ? getHeader(header) : defaultValue
  }

  @Override
  boolean hasHeader(String header) {
    headers.contains(header)
  }
}
