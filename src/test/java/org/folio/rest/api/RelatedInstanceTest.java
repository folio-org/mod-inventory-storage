package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.relatedInstanceTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.relatedInstanceStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public class RelatedInstanceTest extends TestBaseWithInventoryUtil {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(relatedInstanceStorageUrl(""));
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
    StorageTestSuite.deleteAll(relatedInstanceTypesStorageUrl(""));
  }

  @Test
  public void canCreateRelatedInstance() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    createRelatedInstanceType(relatedInstanceTypeId, "Reenactment of");
    instancesClient.create(smallAngryPlanet(smallAngryUuid));
    instancesClient.create(nod(nodUuid));

    Response response = createRelatedInstance( 
      smallAngryUuid.toString(), 
      nodUuid.toString(), 
      relatedInstanceTypeId.toString());


    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("instanceId"), is(smallAngryUuid.toString()));
    assertThat(response.getJson().getString("relatedInstanceId"), is(nodUuid.toString()));
    assertThat(response.getJson().getString("relatedInstanceType"), is(relatedInstanceTypeId.toString()));
  }
 
  @Test
  public void cannotRelateInstanceToSelf() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    createRelatedInstanceType(relatedInstanceTypeId, "Reenactment of");
    instancesClient.create(smallAngryPlanet(smallAngryUuid));

    Response response = createRelatedInstance( 
      smallAngryUuid.toString(), 
      smallAngryUuid.toString(), 
      relatedInstanceTypeId.toString());
    
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void creationFailsWithInvalidInstanceIds() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID invalidInstance = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    createRelatedInstanceType(relatedInstanceTypeId, "Reenactment of");
    instancesClient.create(smallAngryPlanet(smallAngryUuid));

    Response response = createRelatedInstance( 
      smallAngryUuid.toString(), 
      invalidInstance.toString(), 
      relatedInstanceTypeId.toString());
    
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    response = createRelatedInstance(  
      invalidInstance.toString(),
      smallAngryUuid.toString(),
      relatedInstanceTypeId.toString());
    
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void CreationFailsWithInvalidRelationTypeId() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    UUID invalidRelationType = UUID.randomUUID();
    createRelatedInstanceType(relatedInstanceTypeId, "Reenactment of");
    instancesClient.create(smallAngryPlanet(smallAngryUuid));

    Response response = createRelatedInstance( 
      smallAngryUuid.toString(), 
      nodUuid.toString(), 
      invalidRelationType.toString());

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canUpdateRelatedInstance() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID upRootedUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    
    Response response = createRelatedInstanceSetup(smallAngryUuid, nodUuid, relatedInstanceTypeId);
    String relatedInstanceId = response.getJson().getString("id");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("instanceId"), is(smallAngryUuid.toString()));
    assertThat(response.getJson().getString("relatedInstanceId"), is(nodUuid.toString()));
    assertThat(response.getJson().getString("relatedInstanceType"), is(relatedInstanceTypeId.toString()));

    instancesClient.create(uprooted(upRootedUuid));

    Response updateOperation = updateRelatedInstance(
      relatedInstanceId, 
      upRootedUuid.toString(), 
      nodUuid.toString(), 
      relatedInstanceTypeId.toString());
    
    assertThat(updateOperation.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    
    Response updateResponse = getById(relatedInstanceId);

    assertThat(updateResponse.getJson().getString("instanceId"), is(upRootedUuid.toString()));

  }

  @Test
  public void cannotSetInstanceRelationToSelf() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    
    Response response = createRelatedInstanceSetup(smallAngryUuid, nodUuid, relatedInstanceTypeId);
    String relatedInstanceId = response.getJson().getString("id");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response updateOperation = updateRelatedInstance(
      relatedInstanceId, 
      nodUuid.toString(), 
      nodUuid.toString(), 
      relatedInstanceTypeId.toString());
    
    assertThat(updateOperation.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

  }

  @Test
  public void updateFailsWithInvalidInstanceIds() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    UUID invalidInstance = UUID.randomUUID();
    
    Response response = createRelatedInstanceSetup(smallAngryUuid, nodUuid, relatedInstanceTypeId);
    String relatedInstanceId = response.getJson().getString("id");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response updateOperation = updateRelatedInstance(
      relatedInstanceId, 
      nodUuid.toString(), 
      invalidInstance.toString(), 
      relatedInstanceTypeId.toString());
    
    assertThat(updateOperation.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    updateOperation = updateRelatedInstance(
      relatedInstanceId, 
      invalidInstance.toString(),
      nodUuid.toString(),
      relatedInstanceTypeId.toString());

    assertThat(updateOperation.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

  }

  @Test
  public void updateFailsWithInvalidRelationTypeId() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    UUID invalidRelationType = UUID.randomUUID();

    Response response = createRelatedInstanceSetup(smallAngryUuid, nodUuid, relatedInstanceTypeId);
    String relatedInstanceId = response.getJson().getString("id");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response updateOperation = updateRelatedInstance(
      relatedInstanceId, 
      smallAngryUuid.toString(),
      nodUuid.toString(),
      invalidRelationType.toString());
      
    assertThat(updateOperation.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void updateFailsIfRelationNotFound() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();
    UUID invalidRelation = UUID.randomUUID();

    Response response = createRelatedInstanceSetup(smallAngryUuid, nodUuid, relatedInstanceTypeId);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    Response updateOperation = updateRelatedInstance(
      invalidRelation.toString(), 
      smallAngryUuid.toString(),
      nodUuid.toString(),
      relatedInstanceTypeId.toString());

    assertThat(updateOperation.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canDeleteRelationship() {
    UUID smallAngryUuid = UUID.randomUUID();
    UUID nodUuid = UUID.randomUUID();
    UUID relatedInstanceTypeId = UUID.randomUUID();

    Response response = createRelatedInstanceSetup(smallAngryUuid, nodUuid, relatedInstanceTypeId);
    String relatedInstanceId = response.getJson().getString("id");

    response = deleteRelation(relatedInstanceId);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    response = getById(relatedInstanceId);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @SneakyThrows
  private Response deleteRelation(String relationId) {

    CompletableFuture<Response> deleteRelatedInstance = new CompletableFuture<>();

    String relatedInstanceUrl = relatedInstanceStorageUrl("/" + relationId).toString();

    send(relatedInstanceUrl, HttpMethod.DELETE, null,
    SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteRelatedInstance));

    return deleteRelatedInstance.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private Response createRelatedInstanceSetup(UUID instanceId, UUID relatedInstanceId, UUID relatedInstanceTypeUuid) {
    createRelatedInstanceType(relatedInstanceTypeUuid, "Reenactment of");
    instancesClient.create(smallAngryPlanet(instanceId));
    instancesClient.create(nod(relatedInstanceId));

    return createRelatedInstance( 
      instanceId.toString(), 
      relatedInstanceId.toString(), 
      relatedInstanceTypeUuid.toString());
  }

  @SneakyThrows
  private Response updateRelatedInstance(
    String existingRecordUuid,
    String instanceId, 
    String relatedInstanceId, 
    String relatedInstanceTypeId) {

    CompletableFuture<Response> updateRelatedInstance = new CompletableFuture<>();
    String relatedInstanceUrl = relatedInstanceStorageUrl("/" + existingRecordUuid.toString()).toString();
    JsonObject request = new JsonObject()
      .put("id", existingRecordUuid)
      .put("instanceId", instanceId)
      .put("relatedInstanceId", relatedInstanceId)
      .put("relatedInstanceType", relatedInstanceTypeId);

    send(relatedInstanceUrl, HttpMethod.PUT, request.toString(),
    SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updateRelatedInstance));

    return updateRelatedInstance.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private Response createRelatedInstance(
    String instanceId, 
    String relatedInstanceId, 
    String relatedInstanceTypeId) {

    CompletableFuture<Response> createRelatedInstance = new CompletableFuture<>();
    String createRelatedInstanceUrl = relatedInstanceStorageUrl("").toString();
    JsonObject request = new JsonObject()
      .put("instanceId", instanceId)
      .put("relatedInstanceId", relatedInstanceId)
      .put("relatedInstanceType", relatedInstanceTypeId);

    send(createRelatedInstanceUrl, HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(createRelatedInstance));

    return createRelatedInstance.get(5, TimeUnit.SECONDS);
  }
  @SneakyThrows
  private Response createRelatedInstanceType(String name) {

    CompletableFuture<Response> createRelatedInstanceType = new CompletableFuture<>();
    String createURL = relatedInstanceTypesStorageUrl("").toString();
    JsonObject request = new JsonObject().put("name", name);

    send(createURL, HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createRelatedInstanceType));

    return createRelatedInstanceType.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private Response createRelatedInstanceType(UUID id, String name) {

    CompletableFuture<Response> createRelatedInstanceType = new CompletableFuture<>();
    String createURL = relatedInstanceTypesStorageUrl("").toString();
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", name);

    send(createURL, HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createRelatedInstanceType));

    return createRelatedInstanceType.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private Response getById(String id) {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(relatedInstanceStorageUrl("/" + id).toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonObject uprooted(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "1447294149"));
    identifiers.add(identifier(UUID_ISBN, "9781447294146"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Novik, Naomi"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "TEST", "Uprooted",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject smallAngryPlanet(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "TEST", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject nod(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ASIN, "B01D1PLMDO"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Barnes, Adrian"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");
    return createInstanceRequest(id, "TEST", "Nod",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }
}
