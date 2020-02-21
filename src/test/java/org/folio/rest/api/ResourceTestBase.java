package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.rest.api.entities.JsonEntity;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class ResourceTestBase extends TestBase {

  protected static final int UNPROCESSABLE_ENTITY = 422;

  protected Response createReferenceRecord(String path, JsonEntity referenceObject)
    throws ExecutionException, InterruptedException, TimeoutException {

    URL referenceUrl = StorageTestSuite.storageUrl(path);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    client.post(
      referenceUrl,
      referenceObject.getJson(),
      TENANT_ID,
      ResponseHandler.any(createCompleted)
    );
    Response postResponse = createCompleted.get(5, TimeUnit.SECONDS);
    return postResponse;
  }

  protected Response getById(URL getByIdUrl)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getByIdUrl, TENANT_ID,
      ResponseHandler.any(getCompleted));

    Response getByIdResponse = getCompleted.get(5, TimeUnit.SECONDS);

    return getByIdResponse;
  }

  protected Response getByQuery(URL getByQueryUrl)
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(getByQueryUrl, TENANT_ID,
      ResponseHandler.any(getCompleted));

    Response getByQueryResponse = getCompleted.get(5, TimeUnit.SECONDS);

    return getByQueryResponse;

  }

  protected Response deleteReferenceRecordById(URL entityUrl)
    throws ExecutionException, InterruptedException, TimeoutException {

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(
      entityUrl,
      TENANT_ID,
      ResponseHandler.any(deleteCompleted)
    );
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    return deleteResponse;
  }

  protected Response updateRecord(URL entityUrl, JsonEntity referenceObject)
    throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Response> updateCompleted = new CompletableFuture<>();
    client.put(
      entityUrl,
      referenceObject.getJson(),
      TENANT_ID,
      ResponseHandler.any(updateCompleted)
    );
    Response putResponse = updateCompleted.get(5, TimeUnit.SECONDS);
    return putResponse;
  }

  protected void testGetPutDeletePost(String path, String entityId, JsonEntity entity, String updateProperty)
    throws ExecutionException,
    InterruptedException,
    MalformedURLException,
    TimeoutException {

    entity.put(updateProperty, entity.getString(updateProperty) + " UPDATED");

    URL url = StorageTestSuite.storageUrl(path + "/" + entityId);
    URL urlWithBadUUID = StorageTestSuite.storageUrl(path + "/baduuid");
    URL urlWithBadParameter = StorageTestSuite.storageUrl(path + "?offset=-3");
    URL urlWithBadCql = StorageTestSuite.storageUrl(path + "?query=badcql");

    Response putResponse = updateRecord(url, entity);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(url);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString(updateProperty), is(entity.getString(updateProperty)));

    entity.put("id", entityId);
    Response postResponse1 = createReferenceRecord(path, entity);
    if (Arrays.asList("/electronic-access-relationships", "/instance-statuses",
      "/modes-of-issuance", "/statistical-code-types", "/holdings-types",
      "/preceding-succeeding-titles").contains(path)) {
      assertThat(postResponse1.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    } else {
      assertThat(postResponse1.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    Response badParameterResponse = getByQuery(urlWithBadParameter);
    if ("/preceding-succeeding-titles".equals(path)) {
      assertThat(badParameterResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    } else {
      assertThat(badParameterResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    Response badQueryResponse = getByQuery(urlWithBadCql);
    assertThat(badQueryResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    Response putResponse2 = updateRecord(urlWithBadUUID, entity);

    if ("/preceding-succeeding-titles".equals(path)) {
      assertThat(putResponse2.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    } else {
      assertThat(putResponse2.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    Response deleteResponse = deleteReferenceRecordById(url);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse2 = getById(url);

    assertThat(getResponse2.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    Response deleteResponse2 = deleteReferenceRecordById(url);

    assertThat(deleteResponse2.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    Response deleteResponse3 = deleteReferenceRecordById(urlWithBadUUID);

    assertThat(deleteResponse3.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    entity.put("id", "baduuid");
    Response postResponse2 = createReferenceRecord(path, entity);
    if (Arrays.asList("/instance-note-types", "/nature-of-content-terms", "/preceding-succeeding-titles").contains(path)) {
      assertThat(postResponse2.getStatusCode(), is(UNPROCESSABLE_ENTITY)); // unprocessable entity, fails UUID pattern
    } else {
      assertThat(postResponse2.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    }
  }
}
