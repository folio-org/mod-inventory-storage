package org.folio.rest.impl;

import static org.folio.rest.impl.HoldingsStorageFixtures.createHolding;
import static org.folio.rest.impl.HoldingsStorageFixtures.createItem;
import static org.folio.rest.impl.HoldingsStorageFixtures.createLoanType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.sqlclient.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Only a DELETE on instances/holdings/items should create a row in the corresponding audit
 * table; an UPDATE should not.
 */
class AuditDeleteIT extends BaseIntegrationTest {

  private static final String AUDIT_INSTANCE = "audit_instance";
  private static final String AUDIT_HOLDINGS_RECORD = "audit_holdings_record";
  private static final String AUDIT_ITEM = "audit_item";
  private static final String RECORD_ID_JSON_PATH = "/record/id";

  private String instanceId;
  private String holdingId;
  private String materialTypeId;
  private String loanTypeId;

  @BeforeEach
  void createFixtures() {
    clearAuditTables();

    var instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    instanceId = createInstance(client, "an instance", instanceTypeId);
    holdingId = createHolding(client, instanceId, createLocation(client));
  }

  private static void clearAuditTables() {
    runQuery("DELETE FROM " + AUDIT_INSTANCE);
    runQuery("DELETE FROM " + AUDIT_HOLDINGS_RECORD);
    runQuery("DELETE FROM " + AUDIT_ITEM);
  }

  @Test
  void shouldStoreOnlyDeletedItemsInAuditTable() {
    var itemId = createItem(client, holdingId, materialTypeId, loanTypeId);

    var item = get(doGet(client, ResourcePaths.ITEMS + "/" + itemId)).jsonBody();
    item.remove("yearCaption");
    assertEquals(204, get(doPut(client, ResourcePaths.ITEMS + "/" + itemId, item)).status());

    assertEquals(0, countAuditRecords(AUDIT_ITEM));

    assertEquals(204, get(doDelete(client, ResourcePaths.ITEMS + "/" + itemId)).status());

    assertEquals(itemId, singleAuditRecordId(AUDIT_ITEM));
  }

  @Test
  void shouldStoreOnlyDeletedInstancesInAuditTable() {
    var instance = get(doGet(client, ResourcePaths.INSTANCES + "/" + instanceId)).jsonBody();
    instance.remove("notes");
    assertEquals(204, get(doPut(client, ResourcePaths.INSTANCES + "/" + instanceId, instance)).status());

    assertEquals(0, countAuditRecords(AUDIT_INSTANCE));

    assertEquals(204, get(doDelete(client, ResourcePaths.HOLDINGS + "/" + holdingId)).status());
    assertEquals(204, get(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId)).status());

    assertEquals(instanceId, singleAuditRecordId(AUDIT_INSTANCE));
  }

  @Test
  void shouldStoreOnlyDeletedHoldingsInAuditTable() {
    var newLocationId = createLocation(client);
    var holding = get(doGet(client, ResourcePaths.HOLDINGS + "/" + holdingId)).jsonBody();
    holding.put("permanentLocationId", newLocationId);
    assertEquals(204, get(doPut(client, ResourcePaths.HOLDINGS + "/" + holdingId, holding)).status());

    assertEquals(0, countAuditRecords(AUDIT_HOLDINGS_RECORD));

    assertEquals(204, get(doDelete(client, ResourcePaths.HOLDINGS + "/" + holdingId)).status());

    assertEquals(holdingId, singleAuditRecordId(AUDIT_HOLDINGS_RECORD));
  }

  private static int countAuditRecords(String table) {
    return runQuery("SELECT * FROM " + table).size();
  }

  private static String singleAuditRecordId(String table) {
    Row row = runQuery("SELECT * FROM " + table).iterator().next();
    return (String) JsonPointer.from(RECORD_ID_JSON_PATH).queryJson(row.getValue(1));
  }
}
