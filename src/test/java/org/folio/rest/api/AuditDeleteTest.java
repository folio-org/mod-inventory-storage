package org.folio.rest.api;

import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class AuditDeleteTest extends TestBaseWithInventoryUtil {

  private static final String AUDIT_INSTANCE = "audit_instance";
  private static final String AUDIT_HOLDINGS_RECORD = "audit_holdings_record";
  private static final String AUDIT_ITEM = "audit_item";
  private static final String RECORD_ID_JSON_PATH = "/record/id";

  private static final int TIMEOUT_MILLIS = 1000;

  private static final PostgresClient POSTGRES_CLIENT =
    PostgresClientFactory.getInstance(getVertx().getOrCreateContext(), TENANT_ID);

  private UUID holdingsRecordId;

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    clearAuditTables();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    removeAllEvents();
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
    final JsonObject recordJsonObject = itemsClient.getAll().getFirst();
    final String itemId = recordJsonObject.getString("id");
    //when
    recordJsonObject.remove("yearCaption");
    itemsClient.replace(UUID.fromString(itemId), recordJsonObject);
    //then
    assertThat(getRecordsFromAuditTable(AUDIT_ITEM).size(), is(0));
    //when
    itemsClient.delete(UUID.fromString(itemId));
    //then
    assertThat(getRecordIdFromAuditTable(AUDIT_ITEM), is(itemId));
  }

  @Test
  public void testOnlyDeletedInstancesAreStoredInAuditTable()
    throws InterruptedException,
    TimeoutException,
    ExecutionException {

    //given
    final JsonObject recordJsonObject = instancesClient.getAll().getFirst();
    UUID instanceId = UUID.fromString(recordJsonObject.getString("id"));
    //when
    recordJsonObject.remove("notes");
    instancesClient.replace(instanceId, recordJsonObject);
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
  public void testOnlyDeletedHoldingsAreStoredInAuditTable()
    throws InterruptedException,
    TimeoutException,
    ExecutionException {

    //given
    final JsonObject recordJsonObject = holdingsClient.getAll().getFirst();
    //when
    recordJsonObject.put("permanentLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    recordJsonObject.put("sourceId", getPreparedHoldingSourceId().toString());
    recordJsonObject.remove("holdingsItems");
    recordJsonObject.remove("bareHoldingsItems");
    updateHoldingRecord(holdingsRecordId, recordJsonObject);
    //then
    assertThat(getRecordsFromAuditTable(AUDIT_HOLDINGS_RECORD).size(), is(0));
    //when
    holdingsClient.delete(holdingsRecordId);
    //then
    assertThat(getRecordIdFromAuditTable(AUDIT_HOLDINGS_RECORD), is(holdingsRecordId.toString()));
  }

  private Object getRecordIdFromAuditTable(String tableName)
    throws InterruptedException,
    TimeoutException,
    ExecutionException {

    final Row row = getRecordsFromAuditTable(tableName).iterator().next();
    final JsonPointer jsonPointer = JsonPointer.from(RECORD_ID_JSON_PATH);
    return jsonPointer.queryJson(row.getValue(1));
  }

  private RowSet<Row> getRecordsFromAuditTable(String tableName)
    throws InterruptedException,
    TimeoutException,
    ExecutionException {

    final CompletableFuture<RowSet<Row>> result = new CompletableFuture<>();
    POSTGRES_CLIENT.select(getAuditSql(tableName), h ->
      result.complete(h.result()));
    return result.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  private String getAuditSql(String table) {
    return "SELECT * FROM " + table;
  }

  private void clearAuditTables() {
    CompletableFuture<Row> future = new CompletableFuture<>();
    final String sql = Stream.of(AUDIT_INSTANCE, AUDIT_HOLDINGS_RECORD, AUDIT_ITEM)
      .map(s -> "DELETE FROM " + s).collect(Collectors.joining(";"));

    POSTGRES_CLIENT.selectSingle(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(handler.result());
    });
  }
}
