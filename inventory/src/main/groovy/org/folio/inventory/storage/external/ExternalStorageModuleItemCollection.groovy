package org.folio.inventory.storage.external

import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.http.HttpClientResponse
import org.folio.inventory.domain.Item
import org.folio.inventory.domain.ItemCollection
import org.folio.metadata.common.api.request.PagingParameters

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
    String location = storageAddress + "/item-storage/items"

    def onResponse = { HttpClientResponse response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        if(response.statusCode() == 201) {
          def createdItem = mapFromJson(new JsonObject(responseBody))

          resultCallback(createdItem)
        }
        else {
          println("Create item failed, reason: ${responseBody}")
          resultCallback(null)
        }
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    def itemToSend = mapToItemRequest(item)

    vertx.createHttpClient().requestAbs(HttpMethod.POST, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "application/json")
      .end(Json.encodePrettily(itemToSend))
  }

  @Override
  void findById(String id, Closure resultCallback) {
    String location = storageAddress + "/item-storage/items/${id}"

    def onResponse = { response ->
      if(response.statusCode() == 200) {
        response.bodyHandler({ buffer ->
          def responseBody = "${buffer.getString(0, buffer.length())}"

          def itemFromServer = new JsonObject(responseBody)

          def foundItem = mapFromJson(itemFromServer)

          resultCallback(foundItem)
        })
      }
      else {
        resultCallback(null)
      }
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant",  tenant)
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void findAll(Closure resultCallback) {
    String location = storageAddress + "/item-storage/items"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def itemsFromServer = new JsonObject(responseBody).getJsonArray("items")

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
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void findAll(PagingParameters pagingParameters, Closure resultCallback) {
    String location = String.format(storageAddress
      + "/item-storage/items?limit=%s&offset=%s",
      pagingParameters.limit, pagingParameters.offset)

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def itemsFromServer = new JsonObject(responseBody).getJsonArray("items")

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
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void empty(Closure completionCallback) {
    String location = storageAddress + "/item-storage/items"

    def onResponse = { response ->

      println("DELETE ${location}: status code: ${response.statusCode()}")

      response.bodyHandler({ buffer ->
        completionCallback()
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.DELETE, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Accept", "application/json, text/plain")
      .end()
  }

  @Override
  void findByCql(String cqlQuery, PagingParameters pagingParameters,
                 Closure resultCallback) {

    def encodedQuery = URLEncoder.encode(cqlQuery, "UTF-8")

    def location =
      "${storageAddress}/item-storage/items?query=${encodedQuery}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def items = new JsonObject(responseBody).getJsonArray("items")

        def foundItems = new ArrayList<Item>()

        items.each {
          foundItems.add(mapFromJson(it))
        }

        resultCallback(foundItems)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().getAbs(location.toString(), onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void update(Item item, Closure completionCallback, Closure failureCallback) {
    String location = storageAddress + "/item-storage/items/${item.id}"

    def onResponse = { HttpClientResponse response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        if(response.statusCode() == 204) {
          completionCallback()
        }
        else {
          println("Update item failed, reason: ${responseBody}")
          failureCallback("${responseBody}")
        }
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    def itemToSend = mapToItemRequest(item)

    vertx.createHttpClient().requestAbs(HttpMethod.PUT, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "text/plain")
      .end(Json.encodePrettily(itemToSend))
  }

  @Override
  void delete(String id, Closure completionCallback) {
    String location = "${storageAddress}/item-storage/items/${id}"

    def onResponse = { response ->

      println("DELETE ${location}: status code: ${response.statusCode()}")

      response.bodyHandler({ buffer ->
        completionCallback()
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.DELETE, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Accept", "application/json, text/plain")
      .end()
  }

  private Item mapFromJson(JsonObject itemFromServer) {
    new Item(
      itemFromServer.getString("id"),
      itemFromServer.getString("title"),
      itemFromServer.getString("barcode"),
      itemFromServer.getString("instanceId"),
      itemFromServer?.getJsonObject("status")?.getString("name"),
      itemFromServer?.getJsonObject("materialType")?.getString("name"),
      itemFromServer?.getJsonObject("location")?.getString("name"))
  }

  private Map mapToItemRequest(Item item) {
    def itemToSend = [:]

    //TODO: Review if this shouldn't be defaulting here
    itemToSend.put("id", item.id ?: UUID.randomUUID().toString())
    itemToSend.put("title", item.title)
    itemToSend.put("barcode", item.barcode)
    itemToSend.put("instanceId", item.instanceId)
    itemToSend.put("status", new JsonObject().put("name", item.status))
    itemToSend.put("materialType",
      new JsonObject().put("name", item.materialType))
    itemToSend.put("location",
      new JsonObject().put("name", item.location))
    itemToSend
  }
}
