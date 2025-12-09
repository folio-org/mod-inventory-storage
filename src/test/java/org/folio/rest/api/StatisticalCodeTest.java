package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.statisticalCodesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.builders.StatisticalCodeBuilder;
import org.junit.Before;
import org.junit.Test;

public class StatisticalCodeTest extends TestBaseWithInventoryUtil {

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
    statisticalCodeFixture.removeTestStatisticalCodes();

    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    removeAllEvents();
  }

  @Test
  public void cannotCreateStatisticalCodeWhenNameIsTheSameButInUpperCase() {
    final var firstStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stcone")
      .withName("Statistical code");

    final var secondStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stctwo")
      .withName("STATISTICAL CODE");

    statisticalCodeFixture.createSerialManagementCode(firstStatisticalCode);
    final var response = statisticalCodeFixture
      .attemptCreateSerialManagementCode(secondStatisticalCode);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(
      "duplicate key value violates unique constraint \"statistical_code_name_idx_unique\""));
  }

  @Test
  public void canCreateStatisticalCodeWhenNamesAreDifferent() {
    final var firstStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stcone")
      .withName("Statistical code 1");

    final var secondStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stctwo")
      .withName("STATISTICAL CODE 2");

    final var firstCreated = statisticalCodeFixture.createSerialManagementCode(firstStatisticalCode);
    final var secondCreated = statisticalCodeFixture.createSerialManagementCode(secondStatisticalCode);

    assertThat(firstCreated.getJson().getString("name"), is("Statistical code 1"));
    assertThat(secondCreated.getJson().getString("name"), is("STATISTICAL CODE 2"));
  }

  @Test
  public void canNotDeleteStatisticalCodeIfInUseByInstance() throws Exception {
    final var statisticalCode = new StatisticalCodeBuilder()
      .withId(UUID.fromString("b06fa5fe-a267-4597-8e74-3b308bd4c932"))
      .withCode("stcone")
      .withName("STATISTICAL CODE 1");

    final var createdCode = statisticalCodeFixture.createSerialManagementCode(statisticalCode);

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(withStatisticalCode(createdCode, smallAngryPlanet(instanceId)));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    getClient().delete(statisticalCodesUrl("/" + createdCode.getId().toString()), TENANT_ID,
      ResponseHandler.text(deleteCompleted));

    Response response = deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody().trim(),
      is("foreign_key_violation: Key (id)=(b06fa5fe-a267-4597-8e74-3b308bd4c932) "
        + "is still referenced from table \"instance\"."));
  }

  @Test
  public void canNotDeleteStatisticalCodeIfInUseByHoldings() throws Exception {
    final var statisticalCode = new StatisticalCodeBuilder()
      .withId(UUID.fromString("b06fa5fe-a267-4597-8e74-3b308bd4c932"))
      .withCode("stcone")
      .withName("STATISTICAL CODE 1");

    final var createdCode = statisticalCodeFixture.createSerialManagementCode(statisticalCode);

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    List<UUID> codes = new ArrayList<>();
    codes.add(createdCode.getId());

    final var holdingToCreate = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withStatisticalCodeIds(codes);

    holdingsClient.create(holdingToCreate.create(), TENANT_ID, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    getClient().delete(statisticalCodesUrl("/" + createdCode.getId().toString()), TENANT_ID,
      ResponseHandler.text(deleteCompleted));

    Response response = deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody().trim(),
      is("foreign_key_violation: Key (id)=(b06fa5fe-a267-4597-8e74-3b308bd4c932) "
        + "is still referenced from table \"holdings record\"."));
  }

  @Test
  public void canNotDeleteStatisticalCodeIfInUseByItem() throws Exception {
    final var statisticalCode = new StatisticalCodeBuilder()
      .withId(UUID.fromString("b06fa5fe-a267-4597-8e74-3b308bd4c932"))
      .withCode("stcone")
      .withName("STATISTICAL CODE 1");

    final var createdCode = statisticalCodeFixture.createSerialManagementCode(statisticalCode);

    UUID instanceId = UUID.randomUUID();

    instancesClient.create(smallAngryPlanet(instanceId));

    UUID holdingId = UUID.randomUUID();

    final var holdingToCreate = new HoldingRequestBuilder()
      .withId(holdingId)
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID);

    holdingsClient.create(holdingToCreate.create(), TENANT_ID, Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

    UUID itemId = UUID.randomUUID();

    List<UUID> codes = new ArrayList<>();
    codes.add(createdCode.getId());

    final var itemToCreate = new ItemRequestBuilder()
      .withId(itemId)
      .forHolding(holdingId)
      .withMaterialType(bookMaterialTypeId)
      .withPermanentLoanTypeId(canCirculateLoanTypeId)
      .withStatisticalCodeIds(codes);

    itemsClient.create(itemToCreate);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    getClient().delete(statisticalCodesUrl("/" + createdCode.getId().toString()), TENANT_ID,
      ResponseHandler.text(deleteCompleted));

    Response response = deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody().trim(),
      is("foreign_key_violation: Key (id)=(b06fa5fe-a267-4597-8e74-3b308bd4c932) "
        + "is still referenced from table \"item\"."));
  }

  private JsonObject smallAngryPlanet(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();

    return createInstanceRequest(id, "TEST", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject withStatisticalCode(IndividualResource code, JsonObject obj) {
    JsonArray codes = new JsonArray();
    codes.add(code.getId().toString());
    return obj.put("statisticalCodeIds", codes);
  }
}
