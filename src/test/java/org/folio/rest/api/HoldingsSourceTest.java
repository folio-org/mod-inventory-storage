package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsSourceUrl;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.http.ResourceClient;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class HoldingsSourceTest extends TestBaseWithInventoryUtil {
  private static ResourceClient holdingsSourceClient;

  @BeforeClass
  public static void beforeAll() {
    holdingsSourceClient = ResourceClient.forHoldingsSource(client);
  }

  @Test
  public void canCreateHoldingsSource()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID sourceId = UUID.randomUUID();

    JsonObject source = holdingsSourceClient.create(
      new JsonObject()
      .put("id", sourceId.toString())
      .put("name", "test source")
    ).getJson();

    assertThat(source.getString("id"), is(sourceId.toString()));
    assertThat(source.getString("name"), is("test source"));

    Response getResponse = holdingsSourceClient.getById(sourceId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject sourceFromGet = getResponse.getJson();

    assertThat(sourceFromGet.getString("id"), is(sourceId.toString()));
    assertThat(sourceFromGet.getString("name"), is("test source"));
  }

  @Test
  public void cannotCreateHoldingsSourceWithDuplicateId()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID sourceId = UUID.randomUUID();

    holdingsSourceClient.create(
      new JsonObject()
          .put("id", sourceId.toString())
          .put("name", "source with id")
    ).getJson();

    Response response = holdingsSourceClient.attemptToCreate(
        new JsonObject()
            .put("id", sourceId.toString())
            .put("name", "new source with duplicate id")
    	);
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateHoldingsSourceWithDuplicateName()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID sourceId = UUID.randomUUID();

    JsonObject source = holdingsSourceClient.create(
      new JsonObject()
      .put("id", sourceId.toString())
      .put("name", "original source name")
    ).getJson();

    assertThat(source.getString("id"), is(sourceId.toString()));
    assertThat(source.getString("name"), is("original source name"));

    Response response = holdingsSourceClient.attemptToCreate(
    	      new JsonObject()
    	      .put("name", "original source name")
    	    );
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canCreateHoldingsSourcesWithoutProvidingAnId()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    IndividualResource sourceResponse = holdingsSourceClient.create(
      new JsonObject()
      .put("name", "test source without id")
    );

    JsonObject source = sourceResponse.getJson();

    assertThat(source.getString("id"), is(notNullValue()));
    assertThat(source.getString("name"), is("test source without id"));

    UUID sourceId = sourceResponse.getId();

    Response getResponse = holdingsSourceClient.getById(sourceId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject sourceFromGet = getResponse.getJson();

    assertThat(sourceFromGet.getString("id"), is(sourceId.toString()));
    assertThat(sourceFromGet.getString("name"), is("test source without id"));
  }

  @Test
  public void cannotCreateAHoldingsSourceWithIDThatIsNotUUID()
    throws InterruptedException,
    ExecutionException, TimeoutException {

    String nonUUIDId = "1234567";

    JsonObject request = new JsonObject()
      .put("id", nonUUIDId)
      .put("name", "source with invalid id");

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(holdingsSourceUrl(""), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), containsString("must match"));
    assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("id"));
  }

  @Test
  public void canReplaceAHoldingsSource() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    IndividualResource sourceResponse = holdingsSourceClient.create(
    	      new JsonObject()
    	      .put("name", "replaceable source")
    	    );

    JsonObject source = sourceResponse.getJson();

    assertThat(source.getString("id"), is(notNullValue()));
    assertThat(source.getString("name"), is("replaceable source"));

    sourceResponse = holdingsSourceClient.create(
  	      new JsonObject()
  	      .put("name", "replacement source")
  	    );

    source = sourceResponse.getJson();

    assertThat(source.getString("id"), is(notNullValue()));
    assertThat(source.getString("name"), is("replacement source"));
  }

  @Test
  public void canQueryForMultipleHoldingsSources() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
	holdingsSourceClient.create(
			new JsonObject()
				.put("name", "multisource 1")
		    );

	holdingsSourceClient.create(
			new JsonObject()
				.put("name", "multisource 2")
	    );

    final List<IndividualResource> sources = holdingsSourceClient
    	      .getMany("name==\"multisource*\"");

    assertThat(sources.size(), is(2));
  }

  @Test
  public void cannotReplaceANonexistentHoldingsSource() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
	  Response sourceResponse = holdingsSourceClient.attemptToReplace(UUID.randomUUID().toString(), new JsonObject()
		      .put("name", "updated source name"));
	  assertThat(sourceResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canRemoveAHoldingsSource() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
	  IndividualResource holdingsSource = holdingsSourceClient.create(
			  new JsonObject()
			  	.put("name", "deleteable source")
			  );
	  UUID deleteableHoldingsSourceId = holdingsSource.getId();
	  holdingsSourceClient.delete(deleteableHoldingsSourceId);

	  Response deleteResponse = holdingsSourceClient.getById(deleteableHoldingsSourceId);
	  assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

  }

  @Test
  public void canNotRemoveSpecialHoldingsSources() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
      IndividualResource holdingsSource = holdingsSourceClient.create(
              new JsonObject()
                .put("name", "source with folio source")
                .put("source", "folio")
              );

      UUID folioHoldingsSourceId = holdingsSource.getId();

      Response folioDeleteResponse = holdingsSourceClient.attemptToDelete(folioHoldingsSourceId);

      //it should not have been deleted:
      assertThat(folioDeleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canNotRemoveHoldingsSourcesAttachedToHoldings() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    UUID instanceId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();

    JsonObject instanceToCreate = new JsonObject();

    instanceToCreate.put("id", instanceId.toString());
    instanceToCreate.put("source", "Test Source");
    instanceToCreate.put("title", "Test Instance");
    instanceToCreate.put("instanceTypeId", "535e3160-763a-42f9-b0c0-d8ed7df6e2a2");

    instancesClient.create(instanceToCreate);

    holdingsSourceClient.create(
              new JsonObject()
              .put("id", sourceId.toString())
              .put("name", "associated source")
            ).getJson();

    holdingsClient.create(new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instanceId)
      .withPermanentLocation(mainLibraryLocationId)
      .withSource(sourceId));


      Response sourceDeleteResponse = holdingsSourceClient.attemptToDelete(sourceId);

      //the associated source should not have been deleted:
      assertThat(sourceDeleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canAssociateSourceWithHolding() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
	    UUID instanceId = UUID.randomUUID();
	    UUID sourceId = UUID.randomUUID();

	    JsonObject instanceToCreate = new JsonObject();

	    instanceToCreate.put("id", instanceId.toString());
	    instanceToCreate.put("source", "Test Source");
	    instanceToCreate.put("title", "Test Instance");
	    instanceToCreate.put("instanceTypeId", "535e3160-763a-42f9-b0c0-d8ed7df6e2a2");

	    instancesClient.create(instanceToCreate);

	    holdingsSourceClient.create(
	    	      new JsonObject()
	    	      .put("id", sourceId.toString())
	    	      .put("name", "associatable source")
	    	    ).getJson();

	    IndividualResource holdingsResponse = holdingsClient.create(new HoldingRequestBuilder()
	      .withId(UUID.randomUUID())
	      .forInstance(instanceId)
	      .withPermanentLocation(mainLibraryLocationId)
	      .withSource(sourceId));

	    assertThat(holdingsResponse.getJson().getString("sourceId"), is(sourceId.toString()));
  }
}
