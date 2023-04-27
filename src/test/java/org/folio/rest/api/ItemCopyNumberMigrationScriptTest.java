package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.IndividualResource;
import org.junit.Before;
import org.junit.Test;

public class ItemCopyNumberMigrationScriptTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("migrateItemCopyNumberToSingleValue.sql");

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    removeAllEvents();
  }

  @Test
  public void canMigrateCopyNumbersToSingleValue() throws Exception {
    List<IndividualResource> threeItems = createItems(3);
    UUID[] ids = threeItems.stream().map(IndividualResource::getId).toArray(UUID[]::new);

    setCopyNumbersArray(ids);

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    assertCopyNumber(ids[0], "cp0");
    assertCopyNumber(ids[1], "cp1");
    assertCopyNumber(ids[2], "cp2");
  }

  @Test
  public void emptyCopyNumbersArrayIsRemoved() throws Exception {
    List<IndividualResource> fourItems = createItems(4);
    UUID[] itemIdsWithoutCopyNumbers = {fourItems.get(0).getId(), fourItems.get(2).getId()};
    UUID[] itemIdsWithCopyNumbers = {fourItems.get(1).getId(), fourItems.get(3).getId()};

    setEmptyCopyNumbersArray(itemIdsWithoutCopyNumbers[0]);
    setEmptyCopyNumbersArray(itemIdsWithoutCopyNumbers[1]);

    setCopyNumbersArray(itemIdsWithCopyNumbers);

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    assertCopyNumber(itemIdsWithCopyNumbers[0], "cp0");
    assertCopyNumber(itemIdsWithCopyNumbers[1], "cp1");
    assertNoCopyNumber(itemIdsWithoutCopyNumbers[0]);
    assertNoCopyNumber(itemIdsWithoutCopyNumbers[1]);
  }

  @Test
  public void nullCopyNumbersArrayIsRemoved() throws Exception {
    List<IndividualResource> fourItems = createItems(4);
    UUID[] itemIdsWithNullCopyNumbers = {fourItems.get(0).getId(), fourItems.get(2).getId()};
    UUID[] itemIdsWithCopyNumbers = {fourItems.get(1).getId(), fourItems.get(3).getId()};

    setNullCopyNumbersArray(itemIdsWithNullCopyNumbers[0]);
    setNullCopyNumbersArray(itemIdsWithNullCopyNumbers[1]);

    setCopyNumbersArray(itemIdsWithCopyNumbers);

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    assertNoCopyNumber(itemIdsWithNullCopyNumbers[0]);
    assertNoCopyNumber(itemIdsWithNullCopyNumbers[1]);
    assertCopyNumber(itemIdsWithCopyNumbers[0], "cp0");
    assertCopyNumber(itemIdsWithCopyNumbers[1], "cp1");
  }

  @Test
  public void shouldTakeFirstElementFromCopyNumbersArray() throws Exception {
    List<IndividualResource> sixItems = createItems(6);

    final UUID[] itemIdsWithoutCopyNumbers = {sixItems.get(0).getId(), sixItems.get(5).getId()};
    final UUID[] itemIdsWithCopyNumbers = {sixItems.get(1).getId(), sixItems.get(3).getId()};
    final UUID[] itemIdsWithTwoComponentCopyNumbers = {sixItems.get(2).getId(), sixItems.get(4).getId()};

    setEmptyCopyNumbersArray(itemIdsWithoutCopyNumbers[0]);
    setEmptyCopyNumbersArray(itemIdsWithoutCopyNumbers[1]);

    setCopyNumbersArrayWithTwoValues(itemIdsWithTwoComponentCopyNumbers[0], "cp6");
    setCopyNumbersArrayWithTwoValues(itemIdsWithTwoComponentCopyNumbers[1], "cp7");

    setCopyNumbersArray(itemIdsWithCopyNumbers);

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    assertCopyNumber(itemIdsWithCopyNumbers[0], "cp0");
    assertCopyNumber(itemIdsWithCopyNumbers[1], "cp1");
    assertCopyNumber(itemIdsWithTwoComponentCopyNumbers[0], "cp6");
    assertCopyNumber(itemIdsWithTwoComponentCopyNumbers[1], "cp7");

    assertNoCopyNumber(itemIdsWithoutCopyNumbers[0]);
    assertNoCopyNumber(itemIdsWithoutCopyNumbers[1]);
  }

  private IndividualResource createItem() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject itemToCreate = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("barcode", new Random().nextLong())
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID);

    return itemsClient.create(itemToCreate);
  }

  private List<IndividualResource> createItems(int count) throws Exception {
    final List<IndividualResource> allItems = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      allItems.add(createItem());
    }
    return allItems;
  }

  private void setCopyNumbersArray(UUID... ids) throws Exception {
    for (int currentIdIndex = 0; currentIdIndex < ids.length; currentIdIndex++) {
      final UUID id = ids[currentIdIndex];
      final String copyNumber = "cp" + currentIdIndex;

      updateJsonbProperty("item", id, "copyNumbers",
        String.format("ARRAY['%s']", copyNumber)
      );
    }
  }

  private void setEmptyCopyNumbersArray(UUID id) throws Exception {
    updateJsonbProperty("item", id, "copyNumbers",
      "ARRAY[]::TEXT[]"
    );
  }

  private void setNullCopyNumbersArray(UUID id) throws Exception {
    final CompletableFuture<Void> result = new CompletableFuture<>();
    final JsonObject item = itemsClient.getById(id).getJson().copy()
      .put("copyNumbers", null);

    PostgresClient.getInstance(getVertx(), TENANT_ID)
      .update("item", item, id.toString(), reply -> {
        if (reply.succeeded()) {
          result.complete(null);
        } else {
          result.completeExceptionally(reply.cause());
        }
      });

    result.get(10, TimeUnit.SECONDS);
  }

  private void setCopyNumbersArrayWithTwoValues(UUID id, String copyNumber) throws Exception {
    final String copyNumberToSet = String.format("'%1$s', '%1$sCopy2'", copyNumber);

    updateJsonbProperty("item", id, "copyNumbers",
      String.format("ARRAY[%s]", copyNumberToSet)
    );
  }

  private void assertCopyNumber(UUID itemId, String expectedCopyNumber) throws Exception {
    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat(expectedCopyNumber, notNullValue());
    assertThat(item.getString("copyNumber"), is(expectedCopyNumber));
    assertFalse(item.containsKey("copyNumbers"));
  }

  private void assertNoCopyNumber(UUID itemId) throws Exception {
    JsonObject item = itemsClient.getById(itemId).getJson();

    assertThat(item, notNullValue());
    assertFalse(item.containsKey("copyNumbers"));
    assertFalse(item.containsKey("copyNumber"));
  }
}
