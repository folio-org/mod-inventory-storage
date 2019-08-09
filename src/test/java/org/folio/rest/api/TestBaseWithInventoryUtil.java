/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.builders.HoldingRequestBuilder;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 *
 * @author ne
 */
public abstract class TestBaseWithInventoryUtil extends TestBase {

  protected static final UUID UUID_ISBN = UUID.fromString("8261054f-be78-422d-bd51-4ed9f33c3422");
  protected static final UUID UUID_ASIN = UUID.fromString("7f907515-a1bf-4513-8a38-92e1a07c539d");
  protected static final UUID UUID_PERSONAL_NAME = UUID.fromString("2b94c631-fca9-4892-a730-03ee529ffe2a");
  protected static final UUID UUID_TEXT = UUID.fromString("6312d172-f0cf-40f6-b27d-9fa8feaf332f");

  protected static UUID createInstanceAndHolding(UUID holdingsPermanentLocationId) throws ExecutionException, InterruptedException, MalformedURLException, TimeoutException{
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
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "TEST", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID.randomUUID(),tags);
  }


  protected static JsonObject identifier(UUID identifierTypeId, String value) {
    return new JsonObject()
      .put("identifierTypeId", identifierTypeId.toString())
      .put("value", value);
  }

  protected static JsonObject contributor(UUID contributorNameTypeId, String name) {
    return new JsonObject()
      .put("contributorNameTypeId", contributorNameTypeId.toString())
      .put("name", name);
  }

  protected static JsonObject createInstanceRequest(
    UUID id,
    String source,
    String title,
    JsonArray identifiers,
    JsonArray contributors,
    UUID instanceTypeId,
    JsonArray tags) {

    JsonObject instanceToCreate = new JsonObject();

    if(id != null) {
      instanceToCreate.put("id",id.toString());
    }

    instanceToCreate.put("title", title);
    instanceToCreate.put("source", source);
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    instanceToCreate.put("instanceTypeId", instanceTypeId.toString());
    instanceToCreate.put("tags", new JsonObject().put("tagList", tags));
    return instanceToCreate;
  }


}
