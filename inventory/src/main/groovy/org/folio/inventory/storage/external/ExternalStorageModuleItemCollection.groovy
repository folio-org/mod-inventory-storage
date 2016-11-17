package org.folio.inventory.storage.external

import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection

import java.util.regex.Pattern

class ExternalStorageModuleItemCollection
  implements ItemCollection {

  private final Vertx vertx

  def ExternalStorageModuleItemCollection(final Vertx vertx) {
    this.vertx = vertx
  }

  @Override
  void add(Item item, Closure resultCallback) {
    println("Making create request to external storage")

    String location = "http://localhost:9492/inventory-storage/item"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def status = "${response.statusCode()}"
        def responseBody = "${buffer.getString(0, buffer.length())}"

        println "Create Response: ${responseBody}"

        def itemFromServer = new JsonObject(responseBody)

        def createdItem = new Item(itemFromServer.getString("id"),
          itemFromServer.getString("title"),
          itemFromServer.getString("barcode"))

        resultCallback(createdItem)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    def itemToSend = [:]

    itemToSend.put("title", item.title)
    itemToSend.put("barcode", item.barcode)

    vertx.createHttpClient().requestAbs(HttpMethod.POST, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", "not-blank")
      .end(Json.encodePrettily(itemToSend))
  }

  @Override
  void findById(String id, Closure resultCallback) {
    println("Making get by id request to external storage")

    String location = "http://localhost:9492/inventory-storage/item/${id}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def status = "${response.statusCode()}"
        def responseBody = "${buffer.getString(0, buffer.length())}"

        println "Get by ID Response: ${responseBody}"

        def itemFromServer = new JsonObject(responseBody)

        def foundItem = new Item(itemFromServer.getString("id"),
          itemFromServer.getString("title"),
          itemFromServer.getString("barcode"))

        resultCallback(foundItem)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", "not-blank")
      .end()
  }

  @Override
  void findAll(Closure resultCallback) {
    println("Making get all request to external storage")

    String location = "http://localhost:9492/inventory-storage/item"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def status = "${response.statusCode()}"
        def responseBody = "${buffer.getString(0, buffer.length())}"

        println "Get All Response: ${responseBody}"

        JsonArray itemsFromServer = new JsonArray(responseBody)

        def foundItems = new ArrayList<Item>()

        itemsFromServer.each {
          println "Each item from all: $it"

          def foundItem = new Item(it.getString("id"),
            it.getString("title"),
            it.getString("barcode"))

          foundItems.add(foundItem)
        }

        resultCallback(foundItems)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", "not-blank")
      .end()
  }

  @Override
  void empty(Closure completionCallback) {
    println("Making delete all request to external storage")

    String location = "http://localhost:9492/inventory-storage/item"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        completionCallback()
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.DELETE, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", "not-blank")
      .end()
  }

  @Override
  def findByTitle(String partialTitle, Closure resultCallback) {

    //HACK: Replace by server side implementation
    findAll {
      def results = it.findAll {
        Pattern.compile(
        Pattern.quote(partialTitle),
        Pattern.CASE_INSENSITIVE).matcher(it.title).find()
      }

      resultCallback(results)
    }
  }
}
