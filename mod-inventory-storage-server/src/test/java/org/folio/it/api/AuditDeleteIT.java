package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHolding;
import static org.folio.it.HoldingsStorageFixtures.createItem;
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;

import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.sqlclient.Row;
import org.folio.it.BaseIntegrationTest;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  @DisplayName("should store an item in the audit table only when it is deleted, not updated")
  void shouldStoreOnlyDeletedItemsInAuditTable() {
    var itemId = createItem(client, holdingId, materialTypeId, loanTypeId);

    var item = await(doGet(client, ResourcePaths.ITEMS + "/" + itemId)).jsonBody();
    item.remove("yearCaption");
    var updateResponse = await(doPut(client, ResourcePaths.ITEMS + "/" + itemId, item));
    assertThat(updateResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertThat(countAuditRecords(AUDIT_ITEM)).isZero();

    var deleteResponse = await(doDelete(client, ResourcePaths.ITEMS + "/" + itemId));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertThat(singleAuditRecordId(AUDIT_ITEM)).isEqualTo(itemId);
  }

  @Test
  @DisplayName("should store an instance in the audit table only when it is deleted, not updated")
  void shouldStoreOnlyDeletedInstancesInAuditTable() {
    var instance = await(doGet(client, ResourcePaths.INSTANCES + "/" + instanceId)).jsonBody();
    instance.remove("notes");
    var putResponse = await(doPut(client, ResourcePaths.INSTANCES + "/" + instanceId, instance));
    assertThat(putResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertThat(countAuditRecords(AUDIT_INSTANCE)).isZero();

    var deleteHoldingResponse = await(doDelete(client, ResourcePaths.HOLDINGS + "/" + holdingId));
    assertThat(deleteHoldingResponse.status()).isEqualTo(SC_NO_CONTENT);
    var deleteInstanceResponse = await(doDelete(client, ResourcePaths.INSTANCES + "/" + instanceId));
    assertThat(deleteInstanceResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertThat(singleAuditRecordId(AUDIT_INSTANCE)).isEqualTo(instanceId);
  }

  @Test
  @DisplayName("should store a holding in the audit table only when it is deleted, not updated")
  void shouldStoreOnlyDeletedHoldingsInAuditTable() {
    var newLocationId = createLocation(client);
    var holding = await(doGet(client, ResourcePaths.HOLDINGS + "/" + holdingId)).jsonBody();
    holding.put("permanentLocationId", newLocationId);
    var putResponse = await(doPut(client, ResourcePaths.HOLDINGS + "/" + holdingId, holding));
    assertThat(putResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertThat(countAuditRecords(AUDIT_HOLDINGS_RECORD)).isZero();

    var deleteResponse = await(doDelete(client, ResourcePaths.HOLDINGS + "/" + holdingId));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertThat(singleAuditRecordId(AUDIT_HOLDINGS_RECORD)).isEqualTo(holdingId);
  }

  private static int countAuditRecords(String table) {
    return runQuery("SELECT * FROM " + table).size();
  }

  private static String singleAuditRecordId(String table) {
    Row row = runQuery("SELECT * FROM " + table).iterator().next();
    return (String) JsonPointer.from(RECORD_ID_JSON_PATH).queryJson(row.getValue(1));
  }
}
