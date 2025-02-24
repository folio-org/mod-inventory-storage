package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceRelationshipsUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.api.entities.Instance;
import org.folio.rest.api.entities.InstanceRelationship;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

public class InstanceRelationshipsTest extends TestBaseWithInventoryUtil {
  static final String INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH = "758f13db-ffb4-440e-bb10-8a364aa6cb4a";
  private static final String INSTANCE_TYPE_ID_TEXT = "6312d172-f0cf-40f6-b27d-9fa8feaf332f";

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    removeAllEvents();
  }

  @Test
  public void canCreateInstanceRelationships() throws InterruptedException, ExecutionException, TimeoutException {

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance3Response = createInstance("Title Three", INSTANCE_TYPE_ID_TEXT);
    CompletableFuture<Response> createRelationshipCompleted = new CompletableFuture<>();

    InstanceRelationship instanceRelationshipRequestObject = new InstanceRelationship(
      instance1Response.getString(Instance.ID_KEY),
      instance2Response.getString(Instance.ID_KEY),
      INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH);
    getClient().post(
      instanceRelationshipsUrl(""),
      instanceRelationshipRequestObject.getJson(),
      TENANT_ID,
      ResponseHandler.json(createRelationshipCompleted)
    );
    Response relationshipPostResponse = createRelationshipCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(relationshipPostResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    CompletableFuture<Response> createRelationshipCompleted2 = new CompletableFuture<>();
    InstanceRelationship instanceRelationshipRequestObject2 = new InstanceRelationship(
      instance1Response.getString(Instance.ID_KEY),
      instance3Response.getString(Instance.ID_KEY),
      INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH);
    getClient().post(
      instanceRelationshipsUrl(""),
      instanceRelationshipRequestObject2.getJson(),
      TENANT_ID,
      ResponseHandler.json(createRelationshipCompleted2)
    );
    Response relationshipPostResponse2 = createRelationshipCompleted2.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(relationshipPostResponse2.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

  }

  @Test
  public void cannotCreateRelationshipWithNonExistingInstance()
    throws InterruptedException, ExecutionException, TimeoutException {
    final String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);

    CompletableFuture<Response> createRelationshipCompleted = new CompletableFuture<>();
    InstanceRelationship instanceRelationshipRequestObject = new InstanceRelationship(
      instance1Response.getString(Instance.ID_KEY),
      nonExistingInstanceId,
      INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH);
    getClient().post(
      instanceRelationshipsUrl(""),
      instanceRelationshipRequestObject.getJson(),
      TENANT_ID,
      ResponseHandler.text(createRelationshipCompleted)
    );
    Response relationshipPostResponse = createRelationshipCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(relationshipPostResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

  }

  @Test
  public void cannotCreateRelationshipOfNonExistingRelationshipType()
    throws InterruptedException, ExecutionException, TimeoutException {
    final String nonExistingRelationshipTypeId = "28b65645-2e49-4a85-8dc1-43d444710570";

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);

    CompletableFuture<Response> createRelationshipCompleted = new CompletableFuture<>();
    InstanceRelationship instanceRelationshipRequestObject = new InstanceRelationship(
      instance1Response.getString(Instance.ID_KEY),
      instance2Response.getString(Instance.ID_KEY),
      nonExistingRelationshipTypeId);
    getClient().post(
      instanceRelationshipsUrl(""),
      instanceRelationshipRequestObject.getJson(),
      TENANT_ID,
      ResponseHandler.text(createRelationshipCompleted)
    );
    Response relationshipPostResponse = createRelationshipCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(relationshipPostResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

  }

  private JsonObject createInstance(String title, String instanceTypeId)
    throws InterruptedException, ExecutionException, TimeoutException {
    Instance requestObject = new Instance(title, "TEST", instanceTypeId);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(
      instancesStorageUrl(""),
      requestObject.getJson(),
      TENANT_ID,
      ResponseHandler.json(createCompleted)
    );
    Response postResponse = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    return postResponse.getJson();
  }

}
