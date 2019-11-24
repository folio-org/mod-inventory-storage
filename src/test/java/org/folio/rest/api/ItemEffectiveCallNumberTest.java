package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import junitparams.JUnitParamsRunner;

@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveCallNumberTest extends TestBaseWithInventoryUtil {
  private static Vertx vertx = Vertx.vertx();
  private static UUID instanceId = UUID.randomUUID();
  private static final String POPULATE_EFFECTIVE_CALL_NUMBER_SQL = ResourceUtil
      .asString("templates/db_scripts/populateEffectiveCallNumberComponentsForExistingItems.sql")
      .replace("${myuniversity}_${mymodule}", "test_tenant_mod_inventory_storage");

  private ObjectMapper mapper = new ObjectMapper();

  @BeforeClass
  public static void createInstance() throws Exception {
    // Create once to be used by the many parameterized unit test in
    // canCalculateEffectiveLocationOnIHoldingUpdate(PermTemp, PermTemp, PermTemp)
    // canCalculateEffectiveLocationOnItemUpdate(PermTemp, PermTemp, PermTemp)
    instancesClient.create(instance(instanceId));
  }

  @Test
  public void canInitializeEffectiveCallNumber() throws Exception {
    UUID holding = createInstanceAndHoldingWithCallNumber(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    EffectiveCallNumberComponents components = new EffectiveCallNumberComponents();
    item.setEffectiveCallNumberComponents(components);

    String query = String.format(
      "INSERT INTO test_tenant_mod_inventory_storage.item (id, jsonb) values ('%s','%s');",
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
    UUID holding = createInstanceAndHoldingWithCallNumber(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("testCallNumber"));

    // Update holdings directly without updating item
    String template = "UPDATE test_tenant_mod_inventory_storage.holdings_record SET jsonb = jsonb_set(jsonb, '{callNumber}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumber", holding.toString());
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
    UUID holding = createInstanceAndHoldingWithCallNumber(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(getItem(
      item.getId()).getEffectiveCallNumberComponents().getCallNumber(),
      is("testCallNumber"));

    // Update item directly without updating item effective call number
    String template = "UPDATE test_tenant_mod_inventory_storage.item SET jsonb = jsonb_set(jsonb, '{itemLevelCallNumber}', '\"%s\"') WHERE id = '%s';";
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
    UUID holding = createInstanceAndHoldingWithCallNumberPrefix(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    EffectiveCallNumberComponents components = new EffectiveCallNumberComponents();
    item.setEffectiveCallNumberComponents(components);

    String query = String.format("INSERT INTO test_tenant_mod_inventory_storage.item (id, jsonb) values ('%s','%s');",
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
    UUID holding = createInstanceAndHoldingWithCallNumberPrefix(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("testCallNumberPrefix"));

    // Update holdings directly without updating item
    String template = "UPDATE test_tenant_mod_inventory_storage.holdings_record SET jsonb = jsonb_set(jsonb, '{callNumberPrefix}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumberPrefix", holding.toString());
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
    UUID holding = createInstanceAndHoldingWithCallNumberPrefix(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getPrefix(),
      is("testCallNumberPrefix"));

    // Update item directly without updating item effective call number prefix
    String template = "UPDATE test_tenant_mod_inventory_storage.item SET jsonb = jsonb_set(jsonb, '{itemLevelCallNumberPrefix}', '\"%s\"') WHERE id = '%s';";
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
    UUID holding = createInstanceAndHoldingWithCallNumberSuffix(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    EffectiveCallNumberComponents components = new EffectiveCallNumberComponents();
    item.setEffectiveCallNumberComponents(components);

    String query = String.format("INSERT INTO test_tenant_mod_inventory_storage.item (id, jsonb) values ('%s','%s');",
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
    UUID holding = createInstanceAndHoldingWithCallNumberSuffix(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("testCallNumberSuffix"));

    // Update holdings directly without updating item
    String template = "UPDATE test_tenant_mod_inventory_storage.holdings_record SET jsonb = jsonb_set(jsonb, '{callNumberSuffix}', '\"%s\"') WHERE id = '%s';";
    String query = String.format(template, "updatedCallNumberSuffix", holding.toString());
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
    UUID holding = createInstanceAndHoldingWithCallNumberSuffix(mainLibraryLocationId);
    Item item = buildItem(holding, null, null);
    createItem(item);

    assertThat(
      getItem(item.getId()).getEffectiveCallNumberComponents().getSuffix(),
      is("testCallNumberSuffix"));

    // Update item directly without updating item effective call number suffix
    String template = "UPDATE test_tenant_mod_inventory_storage.item SET jsonb = jsonb_set(jsonb, '{itemLevelCallNumberSuffix}', '\"%s\"') WHERE id = '%s';";
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

  private Item getItem(String id) throws Exception {
    return itemsClient.getById(UUID.fromString(id)).getJson().mapTo(Item.class);
  }

  private HoldingsRecord getHoldings(UUID id) throws Exception {
    return holdingsClient.getById(id).getJson().mapTo(HoldingsRecord.class);
  }

  private void runSql(String sql) {
    CompletableFuture<Void> future = new CompletableFuture<>();

    PostgresClient.getInstance(vertx).execute(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(null);
    });

    try {
      future.get(1, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
