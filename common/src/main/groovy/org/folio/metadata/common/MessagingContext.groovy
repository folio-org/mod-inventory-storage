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
  String getOkapiLocation() {
    getHeader("okapiLocation")
  }

  def getHeader(String header) {
    headers.get(header)
  }
}
