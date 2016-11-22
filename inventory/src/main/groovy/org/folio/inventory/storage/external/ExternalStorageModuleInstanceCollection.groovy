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

import java.util.regex.Pattern

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

    String location = storageModuleAddress + "/instances"

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

    instanceToSend.put("title", instance.title)

    vertx.createHttpClient().requestAbs(HttpMethod.POST, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .end(Json.encodePrettily(instanceToSend))
  }

  @Override
  void findById(String id, Closure resultCallback) {
    String location = storageModuleAddress + "/instances/${id}"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        def instanceFromServer = new JsonObject(responseBody)

        def foundInstance = mapFromJson(instanceFromServer)

        resultCallback(foundInstance)
      })
    }

    Handler<Throwable> onException = { println "Exception: ${it}" }

    vertx.createHttpClient().requestAbs(HttpMethod.GET, location, onResponse)
      .exceptionHandler(onException)
      .putHeader("X-Okapi-Tenant", tenant)
      .end()
  }


  @Override
  void findAll(Closure resultCallback) {
    String location = storageModuleAddress + "/instances"

    def onResponse = { response ->
      response.bodyHandler({ buffer ->
        def responseBody = "${buffer.getString(0, buffer.length())}"

        JsonArray instances = new JsonArray(responseBody)

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
      .end()
  }

  @Override
  void empty(Closure completionCallback) {
    String location = storageModuleAddress + "/instances"

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

  private Instance mapFromJson(JsonObject instanceFromServer) {
    new Instance(
      instanceFromServer.getString("id"),
      instanceFromServer.getString("title"))
  }
}
