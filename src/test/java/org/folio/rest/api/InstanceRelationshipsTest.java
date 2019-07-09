/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceRelationshipsUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.api.entities.Instance;
import org.folio.rest.api.entities.InstanceRelationship;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

/**
 *
 * @author ne
 */
public class InstanceRelationshipsTest extends TestBaseWithInventoryUtil {

  private final static String INSTANCE_TYPE_ID_TEXT = "6312d172-f0cf-40f6-b27d-9fa8feaf332f";
  private final static String INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH = "758f13db-ffb4-440e-bb10-8a364aa6cb4a";

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void canCreateInstanceRelationships()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance3Response = createInstance("Title Three", INSTANCE_TYPE_ID_TEXT);
    CompletableFuture<Response> createRelationshipCompleted = new CompletableFuture<>();

    InstanceRelationship instanceRelationshipRequestObject = new InstanceRelationship(
            instance1Response.getString(Instance.ID_KEY),
            instance2Response.getString(Instance.ID_KEY),
            INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH);
    client.post(
            instanceRelationshipsUrl(""),
            instanceRelationshipRequestObject.getJson(),
            StorageTestSuite.TENANT_ID,
            ResponseHandler.json(createRelationshipCompleted)
    );
    Response relationshipPostResponse = createRelationshipCompleted.get(5, TimeUnit.SECONDS);
    assertThat(relationshipPostResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    CompletableFuture<Response> createRelationshipCompleted2 = new CompletableFuture<>();
    InstanceRelationship instanceRelationshipRequestObject2 = new InstanceRelationship(
            instance1Response.getString(Instance.ID_KEY),
            instance3Response.getString(Instance.ID_KEY),
            INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH);
    client.post(
            instanceRelationshipsUrl(""),
            instanceRelationshipRequestObject2.getJson(),
            StorageTestSuite.TENANT_ID,
            ResponseHandler.json(createRelationshipCompleted2)
    );
    Response relationshipPostResponse2 = createRelationshipCompleted2.get(5, TimeUnit.SECONDS);
    assertThat(relationshipPostResponse2.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

  }

  @Test
  public void cannotCreateRelationshipWithNonExistingInstance ()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException{

    final String nonExistingInstanceId = "14b65645-2e49-4a85-8dc1-43d444710570";

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);

    CompletableFuture<Response> createRelationshipCompleted = new CompletableFuture<>();
    InstanceRelationship instanceRelationshipRequestObject = new InstanceRelationship(
            instance1Response.getString(Instance.ID_KEY),
            nonExistingInstanceId,
            INSTANCE_RELATIONSHIP_TYPE_ID_BOUNDWITH);
    client.post(
            instanceRelationshipsUrl(""),
            instanceRelationshipRequestObject.getJson(),
            StorageTestSuite.TENANT_ID,
            ResponseHandler.text(createRelationshipCompleted)
    );
    Response relationshipPostResponse = createRelationshipCompleted.get(5, TimeUnit.SECONDS);

    assertThat(relationshipPostResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

  }

  @Test
  public void cannotCreateRelationshipOfNonExistingRelationshipType ()
          throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    final String nonExistingRelationshipTypeId = "28b65645-2e49-4a85-8dc1-43d444710570";

    JsonObject instance1Response = createInstance("Title One", INSTANCE_TYPE_ID_TEXT);
    JsonObject instance2Response = createInstance("Title Two", INSTANCE_TYPE_ID_TEXT);

    CompletableFuture<Response> createRelationshipCompleted = new CompletableFuture<>();
    InstanceRelationship instanceRelationshipRequestObject = new InstanceRelationship(
            instance1Response.getString(Instance.ID_KEY),
            instance2Response.getString(Instance.ID_KEY),
            nonExistingRelationshipTypeId);
    client.post(
            instanceRelationshipsUrl(""),
            instanceRelationshipRequestObject.getJson(),
            StorageTestSuite.TENANT_ID,
            ResponseHandler.text(createRelationshipCompleted)
    );
    Response relationshipPostResponse = createRelationshipCompleted.get(5, TimeUnit.SECONDS);
    assertThat(relationshipPostResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

  }

  private JsonObject createInstance (String title, String instanceTypeId) throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    Instance requestObject = new Instance(title, "TEST", instanceTypeId);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(
            instancesStorageUrl(""),
            requestObject.getJson(),
            StorageTestSuite.TENANT_ID,
            ResponseHandler.json(createCompleted)
    );
    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);
    assertThat(postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    return postResponse.getJson();
  }

}
