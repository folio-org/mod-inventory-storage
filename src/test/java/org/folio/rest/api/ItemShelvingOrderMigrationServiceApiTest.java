package org.folio.rest.api;

import static org.folio.utility.ModuleUtility.tenantOp;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.support.IndividualResource;
import org.junit.Test;

public class ItemShelvingOrderMigrationServiceApiTest extends MigrationTestBase {
  private static final String EFFECTIVE_SHELVING_ORDER = "effectiveShelvingOrder";
  private final AtomicInteger nextPatch = new AtomicInteger(1);

  @Test
  public void shouldPopulateShelvingOrder() throws Exception {
    var items = create201Items();
    removeShelvingOrder(items);

    tenantOp(TENANT_ID, getTenantAttributes());

    for (IndividualResource item : items) {
      var updatedItem = itemsClient.getById(item.getId());
      assertThat(updatedItem.getJson().getString(EFFECTIVE_SHELVING_ORDER), notNullValue());
    }
  }

  @Test
  public void shouldStopProcessingWhenCannotConvertItem() throws Exception {
    var item = createItem(300);
    removeShelvingOrder(List.of(item));
    executeSql(
      "UPDATE " + getSchemaName() + ".item SET jsonb = jsonb || '{\"a\":\"b\"}'::jsonb WHERE id = '" + item.getId()
        + "'");

    var ta = getTenantAttributes();

    assertThat(assertThrows(Throwable.class, () -> tenantOp(TENANT_ID, ta)).getMessage(),
      containsString("Unrecognized field \"a\" (class org.folio.rest.jaxrs.model.Item), not marked as ignorable"));
  }

  private JsonObject getTenantAttributes() {
    return pojo2JsonObject(new TenantAttributes()
      .withModuleFrom("20.1.1")
      .withModuleTo("20.2." + nextPatch.incrementAndGet())
      .withParameters(List.of(
        new Parameter().withKey("loadSample").withValue("false"),
        new Parameter().withKey("loadReference").withValue("false"))));
  }

  private void removeShelvingOrder(List<IndividualResource> items) throws Exception {
    for (IndividualResource item : items) {
      unsetJsonbProperty("item", item.getId(), EFFECTIVE_SHELVING_ORDER);
      assertThat(itemsClient.getById(item.getId()).getJson()
        .getJsonObject(EFFECTIVE_SHELVING_ORDER), nullValue());
    }
  }

  /**
   * Create an item, the index is used for the barcode and the call number components.
   */
  private IndividualResource createItem(int index) {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject itemToCreate = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("status", new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingsRecordId.toString())
      .put("barcode", "100000" + index)
      .put("itemLevelCallNumber", "PS3623.R534 P37 2005 " + index)
      .put("itemLevelCallNumberSuffix", "cns" + index)
      .put("itemLevelCallNumberPrefix", "cnp" + index)
      .put("materialTypeId", journalMaterialTypeID)
      .put("permanentLoanTypeId", canCirculateLoanTypeID);

    return itemsClient.create(itemToCreate);
  }

  private List<IndividualResource> create201Items() {
    return IntStream.range(0, 201)
      .mapToObj(this::createItem)
      .toList();
  }
}
