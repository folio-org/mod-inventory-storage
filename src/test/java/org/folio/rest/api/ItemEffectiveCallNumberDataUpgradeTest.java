package org.folio.rest.api;

import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.util.ResourceUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveCallNumberDataUpgradeTest extends TestBaseWithInventoryUtil {
  private static final String HOLDINGS_CALL_NUMBER_TYPE = UUID.randomUUID().toString();
  private static final String ITEM_LEVEL_CALL_NUMBER_TYPE = UUID.randomUUID().toString();
  private static final String POPULATE_EFFECTIVE_CALL_NUMBER_SQL = ResourceUtil
    .asString("templates/db_scripts/populateEffectiveCallNumberComponentsForExistingItems.sql")
    .replace("${myuniversity}_${mymodule}", "test_mod_inventory_storage");
  private static final Vertx VERTX = Vertx.vertx();
  private static final UUID INSTANCE_ID = UUID.randomUUID();
  private final ObjectMapper mapper = new ObjectMapper();

  @SneakyThrows
  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();

    instancesClient.create(instance(INSTANCE_ID));

    callNumberTypesClient.deleteIfPresent(HOLDINGS_CALL_NUMBER_TYPE);
    callNumberTypesClient.deleteIfPresent(ITEM_LEVEL_CALL_NUMBER_TYPE);

    callNumberTypesClient.create(new JsonObject()
      .put("id", HOLDINGS_CALL_NUMBER_TYPE)
      .put("name", "Holdings call number type")
      .put("source", "folio")
    );
    callNumberTypesClient.create(new JsonObject()
      .put("id", ITEM_LEVEL_CALL_NUMBER_TYPE)
      .put("name", "Item level call number type")
      .put("source", "folio")
    );
  }

  @Test
  public void canInitializeEffectiveCallNumber() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumber(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    EffectiveCallNumberComponents components = new EffectiveCallNumberComponents();
    item.setEffectiveCallNumberComponents(components);

    String query = String.format(
      "INSERT INTO test_mod_inventory_storage.item (id, jsonb) values ('%s','%s');",
      item.getId(), mapper.writeValueAsString(item));
    runSql(query);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is(nullValue()));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("testCallNumber"));
  }

  @Test
  public void canInitializeEffectiveCallNumberAfterHoldingsChange() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumber(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("testCallNumber"));

    // Update holdings directly without updating item
    String template = "UPDATE test_mod_inventory_storage.holdings_record "
      + "SET jsonb = jsonb_set(jsonb, '{callNumber}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumber", holding);
    runSql(query);

    assertThat(getHoldings(holding).getCallNumber(), is("updatedCallNumber"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("testCallNumber"));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    assertThat(getHoldings(holding).getCallNumber(), is("updatedCallNumber"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("updatedCallNumber"));
  }

  @Test
  public void canInitializeEffectiveCallNumberToItemLevelWhenPresent() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumber(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(getItem(
        item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("testCallNumber"));

    // Update item directly without updating item effective call number
    String template = "UPDATE test_mod_inventory_storage.item "
      + "SET jsonb = jsonb_set(jsonb, '{itemLevelCallNumber}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumber", item.getId());
    runSql(query);

    Item updatedItem = getItem(item.getId());

    assertThat(updatedItem.getItemLevelCallNumber(), is("updatedCallNumber"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("testCallNumber"));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    Item populatedItem = getItem(item.getId());

    assertThat(populatedItem.getItemLevelCallNumber(), is("updatedCallNumber"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("updatedCallNumber"));
  }

  @Test
  public void canInitializeEffectiveCallNumberPrefix() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumberPrefix(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    EffectiveCallNumberComponents components = new EffectiveCallNumberComponents();
    item.setEffectiveCallNumberComponents(components);

    String query = String.format("INSERT INTO test_mod_inventory_storage.item (id, jsonb) values ('%s','%s');",
      item.getId(), mapper.writeValueAsString(item));
    runSql(query);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is(nullValue()));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("testCallNumberPrefix"));
  }

  @Test
  public void canInitializeEffectiveCallNumberPrefixAfterHoldingsChange() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumberPrefix(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("testCallNumberPrefix"));

    // Update holdings directly without updating item
    String template = "UPDATE test_mod_inventory_storage.holdings_record "
      + "SET jsonb = jsonb_set(jsonb, '{callNumberPrefix}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumberPrefix", holding);
    runSql(query);

    assertThat(getHoldings(holding).getCallNumberPrefix(), is("updatedCallNumberPrefix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("testCallNumberPrefix"));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    assertThat(getHoldings(holding).getCallNumberPrefix(), is("updatedCallNumberPrefix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("updatedCallNumberPrefix"));
  }

  @Test
  public void canInitializeEffectiveCallNumberPrefixToItemLevelWhenPresent() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumberPrefix(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("testCallNumberPrefix"));

    // Update item directly without updating item effective call number prefix
    String template = "UPDATE test_mod_inventory_storage.item "
      + "SET jsonb = jsonb_set(jsonb, '{itemLevelCallNumberPrefix}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumberPrefix", item.getId());
    runSql(query);

    Item updatedItem = getItem(item.getId());

    assertThat(updatedItem.getItemLevelCallNumberPrefix(), is("updatedCallNumberPrefix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("testCallNumberPrefix"));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    Item populatedItem = getItem(item.getId());

    assertThat(populatedItem.getItemLevelCallNumberPrefix(), is("updatedCallNumberPrefix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("updatedCallNumberPrefix"));
  }

  @Test
  public void canInitializeEffectiveCallNumberSuffix() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumberSuffix(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    EffectiveCallNumberComponents components = new EffectiveCallNumberComponents();
    item.setEffectiveCallNumberComponents(components);

    String query = String.format("INSERT INTO test_mod_inventory_storage.item (id, jsonb) values ('%s','%s');",
      item.getId(), mapper.writeValueAsString(item));
    runSql(query);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is(nullValue()));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("testCallNumberSuffix"));
  }

  @Test
  public void canInitializeEffectiveCallNumberSuffixAfterHoldingsChange() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumberSuffix(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("testCallNumberSuffix"));

    // Update holdings directly without updating item
    String template = "UPDATE test_mod_inventory_storage.holdings_record "
      + "SET jsonb = jsonb_set(jsonb, '{callNumberSuffix}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumberSuffix", holding);
    runSql(query);

    assertThat(getHoldings(holding).getCallNumberSuffix(), is("updatedCallNumberSuffix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("testCallNumberSuffix"));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    assertThat(getHoldings(holding).getCallNumberSuffix(), is("updatedCallNumberSuffix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("updatedCallNumberSuffix"));
  }

  @Test
  public void canInitializeEffectiveCallNumberSuffixToItemLevelWhenPresent() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumberSuffix(MAIN_LIBRARY_LOCATION_ID);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("testCallNumberSuffix"));

    // Update item directly without updating item effective call number suffix
    String template = "UPDATE test_mod_inventory_storage.item "
      + "SET jsonb = jsonb_set(jsonb, '{itemLevelCallNumberSuffix}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumberSuffix", item.getId());
    runSql(query);

    Item updatedItem = getItem(item.getId());

    assertThat(updatedItem.getItemLevelCallNumberSuffix(), is("updatedCallNumberSuffix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("testCallNumberSuffix"));

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    Item populatedItem = getItem(item.getId());

    assertThat(populatedItem.getItemLevelCallNumberSuffix(), is("updatedCallNumberSuffix"));
    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("updatedCallNumberSuffix"));
  }

  @Test
  @Parameters(method = "initializeCallNumberTypeParams")
  public void canInitializeEffectiveCallNumberTypeId(String itemLevelCallNumberType,
                                                     String holdingsRecordCallNumberType) throws Exception {

    final String expectedTypeId = StringUtils
      .firstNonBlank(itemLevelCallNumberType, holdingsRecordCallNumberType);

    UUID holding = createInstanceAndHoldingWithBuilder(MAIN_LIBRARY_LOCATION_ID,
      builder -> builder.withCallNumberTypeId(holdingsRecordCallNumberType));

    String itemId = createItem(new ItemRequestBuilder()
      .forHolding(holding)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .withItemLevelCallNumberTypeId(itemLevelCallNumberType)
    ).getId().toString();

    removeEffectiveCallNumberComponents(itemId);

    runSql(POPULATE_EFFECTIVE_CALL_NUMBER_SQL);

    Item populatedItem = getItem(itemId);

    assertNotNull(populatedItem.getEffectiveCallNumberComponents());
    assertThat(populatedItem
      .getEffectiveCallNumberComponents().getTypeId(), is(expectedTypeId)
    );
  }

  @SuppressWarnings("unused")
  private String[][] initializeCallNumberTypeParams() {
    return new String[][] {
      {ITEM_LEVEL_CALL_NUMBER_TYPE, HOLDINGS_CALL_NUMBER_TYPE},
      {ITEM_LEVEL_CALL_NUMBER_TYPE, null},
      {null, HOLDINGS_CALL_NUMBER_TYPE},
      {null, null},
      };
  }

  private void removeEffectiveCallNumberComponents(String itemId) throws Exception {
    runSql(String.format(
      "UPDATE %s_mod_inventory_storage.item SET jsonb = jsonb - 'effectiveCallNumberComponents' WHERE id = '%s'",
      TENANT_ID,
      itemId
    ));

    assertNull(getItem(itemId).getEffectiveCallNumberComponents());
  }

  private Item getItem(String id) throws Exception {
    return itemsClient.getById(UUID.fromString(id)).getJson().mapTo(Item.class);
  }

  private HoldingsRecord getHoldings(UUID id) throws Exception {
    JsonObject json = holdingsClient.getById(id).getJson();
    json.remove("holdingsItems");
    json.remove("bareHoldingsItems");
    return json.mapTo(HoldingsRecord.class);
  }

  private void runSql(String sql) {
    CompletableFuture<Void> future = new CompletableFuture<>();

    PostgresClient.getInstance(VERTX).execute(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(null);
    });

    try {
      future.get(TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
