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
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success

import java.util.function.Consumer

class ExternalStorageModuleInstanceCollection
  implements InstanceCollection {

  private final Vertx vertx
  private final String storageModuleAddress
  private final String tenant
  private final String token

  def ExternalStorageModuleInstanceCollection(Vertx vertx,
                                              String storageModuleAddress,
                                              String tenant,
                                              String token) {
    this.vertx = vertx
    this.storageModuleAddress = storageModuleAddress
    this.tenant = tenant
    this.token = token
  }

  @Override
  void add(Instance instance,
           Consumer<Success<Instance>> resultCallback,
           Consumer<Failure> failureCallback) {

    String location = storageModuleAddress + "/instance-storage/instances"

    def onResponse = { HttpClientResponse response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 201) {
          def createdInstance = mapFromJson(new JsonObject(responseBody))

          resultCallback.accept(new Success<Instance>(createdInstance))
        }
        else {
          failureCallback.accept(new Failure(responseBody, statusCode))
        }
      })
    }

    def instanceToSend = mapToInstanceRequest(instance)

    vertx.createHttpClient().requestAbs(HttpMethod.POST, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Accept", "application/json")
      .putHeader("Content-Type", "application/json")
      .end(Json.encodePrettily(instanceToSend))
  }

  @Override
  void findById(String id,
                Consumer<Success<Instance>> resultCallback,
                Consumer<Failure> failureCallback) {
    String location = storageModuleAddress + "/instance-storage/instances/${id}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        switch (statusCode) {
          case 200:
            def instanceFromServer = new JsonObject(responseBody)

            def foundInstance = mapFromJson(instanceFromServer)

            resultCallback.accept(new Success(foundInstance))
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
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Accept", "application/json")
      .end()
  }

  @Override
  void findAll(PagingParameters pagingParameters,
               Consumer<Success<List<Instance>>> resultCallback,
               Consumer<Failure> failureCallback) {
    String location = String.format(storageModuleAddress
      + "/instance-storage/instances?limit=%s&offset=%s",
      pagingParameters.limit, pagingParameters.offset)

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 200) {
          def instances = new JsonObject(responseBody).getJsonArray("instances")

          def foundInstances = new ArrayList<Instance>()

          instances.each {
            foundInstances.add(mapFromJson(it))
          }

          resultCallback.accept(new Success(foundInstances))
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
  void delete(String id,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback) {
    String location = "${storageModuleAddress}/instance-storage/instances/${id}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statucCode = response.statusCode()

        if(statucCode == 204) {
          completionCallback.accept(new Success())
        }
        else {
          failureCallback.accept(new Failure(responseBody, statucCode))
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
  void empty(Consumer<Success> completionCallback,
             Consumer<Failure> failureCallback) {

    String location = storageModuleAddress + "/instance-storage/instances"

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
                 Consumer<Success<List<Instance>>> resultCallback,
                 Consumer<Failure> failureCallback) {

    def encodedQuery = URLEncoder.encode(cqlQuery, "UTF-8")

    def location =
      "${storageModuleAddress}/instance-storage/instances?query=${encodedQuery}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"
        def statusCode = response.statusCode()

        if(statusCode == 200) {
          def instances = new JsonObject(responseBody).getJsonArray("instances")

          def foundInstances = new ArrayList<Instance>()

          instances.each {
            foundInstances.add(mapFromJson(it))
          }

          resultCallback.accept(new Success(foundInstances))
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
  void update(Instance instance,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback) {

    String location = "${storageModuleAddress}/instance-storage/instances/${instance.id}"

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

    def instanceToSend = mapToInstanceRequest(instance)

    vertx.createHttpClient().requestAbs(HttpMethod.PUT, location, onResponse)
      .exceptionHandler(exceptionHandler(failureCallback))
      .putHeader("X-Okapi-Tenant", tenant)
      .putHeader("X-Okapi-Token", token)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "text/plain")
      .end(Json.encodePrettily(instanceToSend))

  }

  private Map mapToInstanceRequest(Instance instance) {
    def instanceToSend = [:]

    //TODO: Review if this shouldn't be defaulting here
    instanceToSend.put("id", instance.id ?: UUID.randomUUID().toString())
    instanceToSend.put("title", instance.title)
    instanceToSend.put("identifiers", instance.identifiers)
    instanceToSend
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

  private Handler<Throwable> exceptionHandler(Consumer<Failure> failureCallback) {
    return { Throwable it ->
      failureCallback.accept(new Failure(it.getMessage(), null))
    }
  }
}
