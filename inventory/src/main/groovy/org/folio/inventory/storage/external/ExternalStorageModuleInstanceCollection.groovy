package org.folio.inventory.storage.external

import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.http.HttpClientResponse
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.metadata.common.api.request.PagingParameters

class ExternalStorageModuleInstanceCollection
  implements InstanceCollection {

  private final Vertx vertx
  private final String storageModuleAddress
  private final String tenant

  def ExternalStorageModuleInstanceCollection(Vertx vertx,
                                              String storageModuleAddress,
                                              String tenant) {
    this.vertx = vertx
    this.storageModuleAddress = storageModuleAddress
    this.tenant = tenant
  }

  @Override
  void add(Instance instance, Closure resultCallback) {

    String location = storageModuleAddress + "/instance-storage/instances"

    def onResponse = { HttpClientResponse response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        if(response.statusCode() == 201) {
          def createdInstance = mapFromJson(new JsonObject(responseBody))

          resultCallback(createdInstance)
        }
        else {
          println("Create item failed, reason: ${responseBody}")
          resultCallback(null)
        }
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    def instanceToSend = [:]

    //TODO: Review if this shouldn't be defaulting here
    instanceToSend.put("id", instance.id ?: UUID.randomUUID().toString())
    instanceToSend.put("title", instance.title)
    instanceToSend.put("identifiers", instance.identifiers)

    vertx.createHttpClient().requestAbs(HttpMethod.POST, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Accept", "application/json")
      .putHeader("Content-Type", "application/json")
      .end(Json.encodePrettily(instanceToSend))
  }

  @Override
  void findById(String id, Closure resultCallback) {
    String location = storageModuleAddress + "/instance-storage/instances/${id}"

    def onResponse = { response ->
      if(response.statusCode() == 200) {
        response.bodyHandler({ buffer ->
          def responseBody = "${buffer.getString(0, buffer.length())}"

          def instanceFromServer = new JsonObject(responseBody)

          def foundInstance = mapFromJson(instanceFromServer)

          resultCallback(foundInstance)
        })
      }
      else {
        resultCallback(null)
      }
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Accept", "application/json")
      .end()
  }


  @Override
  void findAll(Closure resultCallback) {
    String location = storageModuleAddress + "/instance-storage/instances"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def instances = new JsonObject(responseBody).getJsonArray("instances")

        def foundInstances = new ArrayList<Instance>()

        instances.each {
          foundInstances.add(mapFromJson(it))
        }

        resultCallback(foundInstances)
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
    String location = String.format(storageModuleAddress
      + "/instance-storage/instances?limit=%s&offset=%s",
      pagingParameters.limit, pagingParameters.offset)

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def instances = new JsonObject(responseBody).getJsonArray("instances")

        def foundInstances = new ArrayList<Instance>()

        instances.each {
          foundInstances.add(mapFromJson(it))
        }

        resultCallback(foundInstances)
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
    String location = storageModuleAddress + "/instance-storage/instances"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        completionCallback()
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.DELETE, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void findByCql(String cqlQuery, PagingParameters pagingParameters,
                 Closure resultCallback) {

    def encodedQuery = URLEncoder.encode(cqlQuery, "UTF-8")

    def location =
      "${storageModuleAddress}/instance-storage/instances?query=${encodedQuery}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def instances = new JsonObject(responseBody).getJsonArray("instances")

        def foundInstances = new ArrayList<Instance>()

        instances.each {
          foundInstances.add(mapFromJson(it))
        }

        resultCallback(foundInstances)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().getAbs(location.toString(), onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("Accept", "application/json")
      .end()
  }

  private Instance mapFromJson(JsonObject instanceFromServer) {

    def identifiers = toList(
      instanceFromServer.getJsonArray("identifiers", new JsonArray()))

    new Instance(
      instanceFromServer.getString("id"),
      instanceFromServer.getString("title"),
      identifiers.collect( {
        [ 'namespace' : it.getString("namespace"),
          'value' : it.getString("value") ] }))
  }

  private toList(JsonArray array) {
    array.stream().collect()
  }
}
