package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.resource.InstanceStorageResource;

import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.vertx.groovy.ext.mongo.MongoClient


public class InstanceStorageAPI implements InstanceStorageResource {

  @Override
  public void getInstanceStorageInstance(@DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang, 
                                         Map<String, String> okapiHeaders, 
                                         Handler<AsyncResult<Response>> asyncResultHandler, 
                                         Context vertxContext) throws Exception {

    List<Instance> instances = new ArrayList<Instance>();

    Instance instance = new Instance();

    instance.setTitle("Refactoring");
    instance.setId(UUID.randomUUID().toString());

    instances.add(instance);

    Instances instanceList = new Instances();
    instanceList.setInstances(instances);
    instanceList.setTotalRecords(instances.size());
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStorageInstanceResponse.withJsonOK(instanceList)));
  }

  @Override
  public void postInstanceStorageInstance(@DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang, 
                                          Instance entity, 
                                          Map<String, String> okapiHeaders, 
                                          Handler<AsyncResult<Response>> asyncResultHandler, 
                                          Context vertxContext) throws Exception {

    java.util.Map<java.lang.String, java.lang.Object> mongo_config = new java.util.HashMap<java.lang.String, java.lang.Object>()
    MongoClient client = MongoClient.createShared(vertxContext.owner(), config, "instance-storage-pool")
    

    // This is sample code from the Virt.x async mongoClient -- it's groovy code tho, so will not work here, suspect without it this would be very verbose

    // def document = [
    //   title:"The Hobbit"
    // ]

    // mongoClient.save("books", document, { res ->
    //   if (res.succeeded()) {
    //   def id = res.result()
    //   println("Saved book with id ${id}")
    //   } else {
    //     res.cause().printStackTrace()
    //   }
    // })

    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStorageInstanceResponse.withPlainInternalServerError("Not implemented")));
  }

  @Override

  public void getInstanceStorageInstanceByInstanceId(@DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") java.lang.String lang,
                                                     java.lang.String instanceId,
                                                     java.util.Map<java.lang.String,java.lang.String> okapiHeaders,
                                                     io.vertx.core.Handler<io.vertx.core.AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                                     io.vertx.core.Context vertxContext) throws Exception {
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStorageInstanceResponse.withPlainInternalServerError("Not implemented")));
  }

}
