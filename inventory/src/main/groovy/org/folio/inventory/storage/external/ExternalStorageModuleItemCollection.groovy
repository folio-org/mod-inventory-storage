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
  private final String storageAddress
  private final String tenant

  def ExternalStorageModuleItemCollection(Vertx vertx,
                                          String storageAddress,
                                          String tenant) {
    this.vertx = vertx
    this.storageAddress = storageAddress
    this.tenant = tenant
  }

  @Override
  void add(Item item, Closure resultCallback) {
    String location = storageAddress + "/items"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def createdItem = mapFromJson(new JsonObject(responseBody))

        resultCallback(createdItem)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    def itemToSend = [:]

    itemToSend.put("title", item.title)
    itemToSend.put("barcode", item.barcode)
    itemToSend.put("instanceId", item.instanceId)

    vertx.createHttpClient().requestAbs(HttpMethod.POST, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .end(Json.encodePrettily(itemToSend))
  }

  @Override
  void findById(String id, Closure resultCallback) {
    String location = storageAddress + "/items/${id}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def itemFromServer = new JsonObject(responseBody)

        def foundItem = mapFromJson(itemFromServer)

        resultCallback(foundItem)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant",  tenant)
      .end()
  }


  @Override
  void findAll(Closure resultCallback) {
    String location = storageAddress + "/items"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        JsonArray itemsFromServer = new JsonArray(responseBody)

        def foundItems = new ArrayList<Item>()

        itemsFromServer.each {
          foundItems.add(mapFromJson(it))
        }

        resultCallback(foundItems)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .end()
  }

  @Override
  void empty(Closure completionCallback) {
    String location = storageAddress + "/items"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        completionCallback()
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.DELETE, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
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

  private Item mapFromJson(JsonObject itemFromServer) {
    new Item(
      itemFromServer.getString("id"),
      itemFromServer.getString("title"),
      itemFromServer.getString("barcode"),
      itemFromServer.getString("instanceId"))
  }
}
