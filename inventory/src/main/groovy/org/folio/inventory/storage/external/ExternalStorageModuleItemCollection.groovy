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
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success

import java.util.function.Consumer

class ExternalStorageModuleItemCollection
  implements ItemCollection {

  private final Vertx vertx
  private final String storageAddress
  private final String tenant
  private final String token

  def ExternalStorageModuleItemCollection(Vertx vertx,
                                          String storageAddress,
                                          String tenant,
                                          String token) {
    this.vertx = vertx
    this.storageAddress = storageAddress
    this.tenant = tenant
    this.token = token
  }

  @Override
  void add(Item item,
           Consumer<Success<Item>> resultCallback,
           Consumer<Failure> failureCallback) {

    String location = storageAddress + "/item-storage/items"

    def onResponse = { HttpClientResponse response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 201) {
          def createdItem = mapFromJson(new JsonObject(responseBody))

          resultCallback.accept(new Success<Item>(createdItem))
        }
        else {
          failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    def itemToSend = mapToItemRequest(item)

    vertx.createHttpClient().requestAbs(HttpMethod.POST, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "application/json")
      .end(Json.encodePrettily(itemToSend))
  }

  @Override
  void findById(String id,
                Consumer<Success<Item>> resultCallback,
                Consumer<Failure> failureCallback) {

    String location = storageAddress + "/item-storage/items/${id}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        switch (statusCode) {
          case 200:
            def itemFromServer = new JsonObject(responseBody)

            def foundItem = mapFromJson(itemFromServer)

            resultCallback.accept(new Success(foundItem))
            break

          case 404:
            resultCallback.accept(new Success(null))
            break

          default:
            failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant",  tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void findAll(PagingParameters pagingParameters,
               Consumer<Success<List<Item>>> resultCallback,
               Consumer<Failure> failureCallback) {

    String location = String.format(storageAddress
      + "/item-storage/items?limit=%s&offset=%s",
      pagingParameters.limit, pagingParameters.offset)

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 200) {
          def itemsFromServer = new JsonObject(responseBody)
            .getJsonArray("items")

          def foundItems = new ArrayList<Item>()

          itemsFromServer.each {
            foundItems.add(mapFromJson(it))
          }

          resultCallback.accept(new Success(foundItems))
        }
        else {
          failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void empty(Consumer<Success> completionCallback,
             Consumer<Failure> failureCallback) {
    String location = storageAddress + "/item-storage/items"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 204) {
          completionCallback.accept(new Success())
        }
        else {
          failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    vertx.createHttpClient().requestAbs(HttpMethod.DELETE, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Accept", "application/json, text/plain")
      .end()
  }

  @Override
  void findByCql(String cqlQuery,
                 PagingParameters pagingParameters,
                 Consumer<Success<List<Item>>> resultCallback,
                 Consumer<Failure> failureCallback) {

    def encodedQuery = URLEncoder.encode(cqlQuery, "UTF-8")

    def location =
      "${storageAddress}/item-storage/items?query=${encodedQuery}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 200) {
          def items = new JsonObject(responseBody).getJsonArray("items")

          def foundItems = new ArrayList<Item>()

          items.each {
            foundItems.add(mapFromJson(it))
          }

          resultCallback.accept(new Success(foundItems))
        }
        else {
          failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    vertx.createHttpClient().getAbs(location.toString(), onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void update(Item item,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback) {

    String location = storageAddress + "/item-storage/items/${item.id}"

    def onResponse = { HttpClientResponse response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 204) {
          completionCallback.accept(new Success(null))
        }
        else {
          failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    def itemToSend = mapToItemRequest(item)

    vertx.createHttpClient().requestAbs(HttpMethod.PUT, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "text/plain")
      .end(Json.encodePrettily(itemToSend))
  }

  @Override
  void delete(String id,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback) {
    String location = "${storageAddress}/item-storage/items/${id}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 204) {
          completionCallback.accept(new Success())
        }
        else {
          failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    vertx.createHttpClient().requestAbs(HttpMethod.DELETE, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
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
      ["id": itemFromServer?.getString("materialTypeId")],
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

    if(item?.materialType?.id != null) {
      itemToSend.put("materialTypeId", item.materialType.id.toString())
    }
    
    itemToSend.put("location",
      new JsonObject().put("name", item.location))
    itemToSend
  }

  private Handler<Throwable> exceptionHandler(Consumer<Failure> failureCallback) {
    return { Throwable it ->
        failureCallback.accept(new Failure(it.getMessage(), null))
    }
  }
}
