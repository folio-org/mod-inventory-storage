package org.folio.metadata.common.messaging

import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.eventbus.EventBus

class JsonMessage {
  private final String address
  private final Map headers
  private final JsonObject body

  JsonMessage(String address, Map headers, JsonObject body) {
    this.address = address
    this.headers = headers
    this.body = body
  }

  void send(Vertx vertx) {
    send(vertx.eventBus())
  }

  void send(EventBus eventBus) {
    eventBus.send(
      address,
      body,
      ["headers" : headers ])
  }
}
