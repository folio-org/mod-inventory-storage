/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import static org.folio.rest.api.TestBase.instancesClient;
import org.folio.rest.support.builders.HoldingRequestBuilder;

/**
 *
 * @author ne
 */
public abstract class TestBaseWithInventoryUtil extends TestBase {

  public static UUID createInstanceAndHolding(UUID holdingsPermanentLocationId) throws ExecutionException, InterruptedException, MalformedURLException, TimeoutException{
    UUID instanceId = UUID.randomUUID();

    instancesClient.create(instance(instanceId));

    UUID holdingsRecordId = UUID.randomUUID();

    JsonObject holding = holdingsClient.create(new HoldingRequestBuilder()
      .withId(holdingsRecordId)
      .forInstance(instanceId)
      .withPermanentLocation(holdingsPermanentLocationId)).getJson();

    return holdingsRecordId;
  }
  
  private static JsonObject instance(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier("isbn", "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor("personal name", "Chambers, Becky"));

    return createInstanceRequest(id, "TEST", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID.randomUUID().toString());
  }

  private static JsonObject identifier(String identifierTypeId, String value) {
    return new JsonObject()
      .put("identifierTypeId", identifierTypeId)
      .put("value", value);
  }

  private static JsonObject contributor(String contributorNameTypeId, String name) {
    return new JsonObject()
      .put("contributorNameTypeId", contributorNameTypeId)
      .put("name", name);
  }
  
  private static JsonObject createInstanceRequest(
    UUID id,
    String source,
    String title,
    JsonArray identifiers,
    JsonArray contributors,
    String instanceTypeId) {

    JsonObject instanceToCreate = new JsonObject();

    if(id != null) {
      instanceToCreate.put("id",id.toString());
    }

    instanceToCreate.put("title", title);
    instanceToCreate.put("source", source);
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    instanceToCreate.put("instanceTypeId", instanceTypeId);

    return instanceToCreate;
  }
  
  
}
