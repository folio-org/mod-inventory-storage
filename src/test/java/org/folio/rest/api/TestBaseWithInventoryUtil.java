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

  // These UUIDs were taken from reference-data folder.
  // When the vertical gets started the data from the reference-data folder are loaded to the DB.
  // see org.folio.rest.impl.TenantRefAPI.refPaths
  protected static final UUID UUID_ISBN = UUID.fromString("8261054f-be78-422d-bd51-4ed9f33c3422");
  protected static final UUID UUID_ASIN = UUID.fromString("7f907515-a1bf-4513-8a38-92e1a07c539d");
  protected static final UUID UUID_PERSONAL_NAME = UUID.fromString("2b94c631-fca9-4892-a730-03ee529ffe2a");
  protected static final UUID UUID_TEXT = UUID.fromString("6312d172-f0cf-40f6-b27d-9fa8feaf332f");
  protected static final UUID UUID_INSTANCE_TYPE = UUID.fromString("535e3160-763a-42f9-b0c0-d8ed7df6e2a2");


  protected static UUID createInstanceAndHolding(UUID holdingsPermanentLocationId)
    throws ExecutionException, InterruptedException, MalformedURLException, TimeoutException {
    return createInstanceAndHolding(holdingsPermanentLocationId, null);
  }

  protected static UUID createInstanceAndHolding(UUID holdingsPermanentLocationId, UUID holdingTempLocation)
    throws ExecutionException,
    InterruptedException,
    MalformedURLException,
    TimeoutException {

    UUID instanceId = UUID.randomUUID();
    instancesClient.create(instance(instanceId));

    return holdingsClient.create(
      new HoldingRequestBuilder()
        .withId(UUID.randomUUID())
        .forInstance(instanceId)
        .withPermanentLocation(holdingsPermanentLocationId)
        .withTemporaryLocation(holdingTempLocation)
    ).getId();
  }

  private static JsonObject instance(UUID id) {
    return createInstanceRequest(
      id,
      "TEST",
      "Long Way to a Small Angry Planet",
      new JsonArray().add(identifier(UUID_ISBN, "9781473619777")),
      new JsonArray().add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky")),
      UUID_INSTANCE_TYPE,
      new JsonArray().add("test-tag")
    );
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
      instanceToCreate.put("id", id.toString());
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
