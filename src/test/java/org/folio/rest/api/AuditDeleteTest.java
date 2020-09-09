package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.*;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.tools.utils.TenantTool;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@RunWith(VertxUnitRunner.class)
public class AuditDeleteTest extends TestBaseWithInventoryUtil {

  private static final String AUDIT_INSTANCE = "audit_instance";
  private static final String AUDIT_HOLDINGS_RECORD = "audit_holdings_record";
  private static final String AUDIT_ITEM = "audit_item";
  private static final String RECORD_ID_JSON_PATH = "/record/id";

  private static final int TIMEOUT_MILLIS = 1000;

  private final static PostgresClient postgresClient =
    PostgresClient.getInstance(
      getVertx(), TenantTool.calculateTenantId(TENANT_ID));

  private UUID holdingsRecordId;

  @Before
  public void setUp() throws InterruptedException, ExecutionException,
    MalformedURLException, TimeoutException {

    clearAuditTables();
    holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
  }

  @BeforeClass
  public static void beforeClass() {
    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void testOnlyDeletedItemsAreStoredInAuditTable() throws Exception {
    //given
    createItem(new ItemRequestBuilder()
      .forHolding(holdingsRecordId)
      .withMaterialType(bookMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withBarcode("766043059304")
      .create());
    final JsonObject record = itemsClient.getAll().get(0);
    final String itemId = record.getString("id");
    //when
    record.remove("yearCaption");
    itemsClient.replace(UUID.fromString(itemId), record);
    //then
    assertThat(getRecordsFromAuditTable(AUDIT_ITEM).size(), is(0));
    //when
    itemsClient.delete(UUID.fromString(itemId));
    //then
    assertThat(getRecordIdFromAuditTable(AUDIT_ITEM), is(itemId));
  }

  @Test
  public void testOnlyDeletedInstancesAreStoredInAuditTable() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {
    //given
    final JsonObject record = instancesClient.getAll().get(0);
    UUID instanceId = UUID.fromString(record.getString("id"));
    //when
    record.remove("notes");
    instancesClient.replace(instanceId, record);
    //then
    assertThat(getRecordsFromAuditTable(AUDIT_INSTANCE).size(), is(0));
    //when
    holdingsClient.deleteAll();
    instancesClient.delete(instanceId);
    //then
    final Object recordIdFromAuditTable = getRecordIdFromAuditTable(AUDIT_INSTANCE);
    assertThat(recordIdFromAuditTable, is(instanceId.toString()));
  }

  @Test
  public void testOnlyDeletedHoldingsAreStoredInAuditTable() throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {
    //given
    final JsonObject record = holdingsClient.getAll().get(0);
    //when
    record.put("permanentLocationId", annexLibraryLocationId.toString());
    holdingsClient.replace(holdingsRecordId, record);
    //then
    assertThat(getRecordsFromAuditTable(AUDIT_HOLDINGS_RECORD).size(), is(0));
    //when
    holdingsClient.delete(holdingsRecordId);
    //then
    assertThat(getRecordIdFromAuditTable(AUDIT_HOLDINGS_RECORD), is(holdingsRecordId.toString()));
  }

  private Object getRecordIdFromAuditTable(String tableName)
    throws InterruptedException, TimeoutException, ExecutionException {

    final Row row = getRecordsFromAuditTable(tableName).iterator().next();
    final JsonPointer jsonPointer = JsonPointer.from(RECORD_ID_JSON_PATH);
    return jsonPointer.queryJson(row.getValue(1));
  }

  private RowSet<Row> getRecordsFromAuditTable(String tableName)
    throws InterruptedException, TimeoutException, ExecutionException {

    final CompletableFuture<RowSet<Row>> result = new CompletableFuture<>();
    postgresClient.select(getAuditSQL(tableName), h ->
      result.complete(h.result()));
    return result.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  private String getAuditSQL(String table) {
    return "SELECT * FROM " + table;
  }

  private void clearAuditTables() {
    CompletableFuture<Row> future = new CompletableFuture<>();
    final String sql = Stream.of(AUDIT_INSTANCE, AUDIT_HOLDINGS_RECORD, AUDIT_ITEM).
      map(s-> "DELETE FROM "+s).collect(Collectors.joining(";"));

    postgresClient.selectSingle(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(handler.result());
    });
  }
}
