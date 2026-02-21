package org.folio.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemRepositoryTest {

  private ItemRepository itemRepository;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    var postgresClient = mock(PostgresClient.class);
    when(postgresClient.getTenantId()).thenReturn("test_tenant");
    var context = Vertx.vertx().getOrCreateContext();
    var okapiHeaders = Map.of("X-Okapi-Tenant", "test_tenant");

    try (var pgUtilMock = mockStatic(PgUtil.class)) {
      pgUtilMock.when(() -> PgUtil.postgresClient(any(Context.class), any(Map.class)))
        .thenReturn(postgresClient);
      itemRepository = new ItemRepository(context, okapiHeaders);
    }
  }

  @ParameterizedTest
  @CsvSource({"1,1", "5,5", "10,10"})
  void updateItems_shouldSuccessfullyUpdateItems(int itemCount, int expected) {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var items = createItemPatchRequests(itemCount);
    var mockRowSet = createMockRowSet(itemCount);

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.executeBatch(anyList())).thenReturn(Future.succeededFuture(mockRowSet));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.succeeded());
    assertNotNull(result.result());
    assertEquals(expected, result.result().size());

    for (var updatedItem : result.result()) {
      assertNotNull(updatedItem.getId());
      assertNotNull(updatedItem.getBarcode());
      assertTrue(updatedItem.getBarcode().startsWith("barcode-"));
    }

    verifySql(pgConnection);
    verify(preparedQuery).executeBatch(argThat(tuples -> tuples.size() == itemCount));
  }

  @Test
  void updateItems_shouldGenerateCorrectSqlAndTuples() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var items = createItemPatchRequests(2);
    var mockRowSet = createMockRowSet(2);

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.executeBatch(anyList())).thenReturn(Future.succeededFuture(mockRowSet));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.succeeded());

    // Verify the SQL was called and contains expected elements
    verifySql(pgConnection);
    verify(preparedQuery).executeBatch(argThat(tuples -> tuples != null && tuples.size() == 2));
  }

  @Test
  void updateItems_shouldFailWhenExceedingMaxEntities() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var items = createItemPatchRequests(10001);

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.failed());
    assertInstanceOf(ValidationException.class, result.cause());

    var validationException = (ValidationException) result.cause();
    assertNotNull(validationException.getErrors());
    assertFalse(validationException.getErrors().getErrors().isEmpty());
    assertTrue(validationException.getErrors().getErrors().getFirst().getMessage()
      .contains("Expected a maximum of 10000"));
  }

  @Test
  void updateItems_shouldHandleDatabaseError() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var items = createItemPatchRequests(5);
    var dbError = new RuntimeException("Database connection failed");

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.executeBatch(anyList())).thenReturn(Future.failedFuture(dbError));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.failed());
    assertEquals(dbError, result.cause());
  }

  @Test
  void updateItems_shouldHandleUnexpectedExceptionInUpdateProcess() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var items = createItemPatchRequests(3);

    // Trigger exception during preparedQuery call
    when(pgConnection.preparedQuery(anyString())).thenThrow(new RuntimeException("Unexpected error"));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.failed());
    assertInstanceOf(RuntimeException.class, result.cause());
    assertEquals("Unexpected error", result.cause().getMessage());
  }

  @Test
  void executeBatchUpdate_shouldProcessRowSetChaining() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var items = createItemPatchRequests(3);
    var mockRowSet = createMockRowSetWithChaining();

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.executeBatch(anyList())).thenReturn(Future.succeededFuture(mockRowSet));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.succeeded());
    assertEquals(3, result.result().size());

    // Verify all items from chained rowsets are collected
    var barcodes = result.result().stream()
      .map(Item::getBarcode)
      .toList();
    assertTrue(barcodes.contains("chained-0"));
    assertTrue(barcodes.contains("chained-1"));
  }

  @Test
  void executeBatchUpdate_shouldHandleEmptyRowSet() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var items = createItemPatchRequests(2);
    var emptyRowSet = createEmptyMockRowSet();

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.executeBatch(anyList())).thenReturn(Future.succeededFuture(emptyRowSet));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.succeeded());
    assertTrue(result.result().isEmpty());
  }

  @Test
  void updateItems_shouldHandleItemsWithComplexData() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var items = createComplexItemPatchRequests();
    var mockRowSet = createMockRowSetWithComplexItems();

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.executeBatch(anyList())).thenReturn(Future.succeededFuture(mockRowSet));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.succeeded());
    assertEquals(2, result.result().size());

    var firstItem = result.result().getFirst();
    assertNotNull(firstItem.getEffectiveLocationId());
    assertNotNull(firstItem.getMaterialTypeId());
  }

  @Test
  void updateItems_shouldHandleJsonDeserializationInRowSet() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var items = createItemPatchRequests(1);

    // Create a rowset with valid JSON that will be deserialized
    var validJson = new JsonObject()
      .put("id", items.getFirst().getId())
      .put("barcode", "test-barcode")
      .put("status", new JsonObject().put("name", "Available"))
      .put("materialTypeId", UUID.randomUUID().toString())
      .put("permanentLoanTypeId", UUID.randomUUID().toString())
      .encode();

    var mockRowSet = createMockRowSetWithJson(validJson);

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.executeBatch(anyList())).thenReturn(Future.succeededFuture(mockRowSet));

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.succeeded());
    assertEquals(1, result.result().size());
    assertEquals("test-barcode", result.result().getFirst().getBarcode());
  }

  @Test
  void updateItems_shouldNotCallDatabaseWhenExceedingMaxEntities() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var items = createItemPatchRequests(10001);

    // When
    var result = itemRepository.updateItems(pgConnection, items);

    // Then
    assertTrue(result.failed());
    verify(pgConnection, times(0)).preparedQuery(anyString());
  }

  @SuppressWarnings("unchecked")
  private PreparedQuery<RowSet<Row>> getRowSetPreparedQuery() {
    return (PreparedQuery<RowSet<Row>>) mock(PreparedQuery.class);
  }

  @Test
  void patchItem_shouldSuccessfullyPatchItem() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var patchRequest = createItemPatchRequest();
    var mockRowSet = createMockRowSet(1);

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.execute(any())).thenReturn(Future.succeededFuture(mockRowSet));

    // When
    var result = itemRepository.patchItem(pgConnection, patchRequest);

    // Then
    assertTrue(result.succeeded());
    assertNotNull(result.result());
    assertEquals(1, result.result().size());

    for (var updatedItem : result.result()) {
      assertNotNull(updatedItem.getId());
      assertNotNull(updatedItem.getBarcode());
      assertTrue(updatedItem.getBarcode().startsWith("barcode-"));
    }

    verifyPatchSql(pgConnection);
    verify(preparedQuery).execute(any(Tuple.class));
  }

  private ItemPatchRequest createItemPatchRequest() {
    return new ItemPatchRequest()
      .withId(UUID.randomUUID().toString())
      .withAdditionalProperty("barcode", "barcode-1")
      .withAdditionalProperty("status", Map.of("name", "Available"));
  }

  @Test
  void patchItem_shouldHandleDatabaseError() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var preparedQuery = getRowSetPreparedQuery();
    var patchRequest = createItemPatchRequest();
    var dbError = new RuntimeException("Database connection failed");

    when(pgConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
    when(preparedQuery.execute(any(Tuple.class))).thenReturn(Future.failedFuture(dbError));

    // When
    var result = itemRepository.patchItem(pgConnection, patchRequest);

    // Then
    assertTrue(result.failed());
    assertEquals(dbError, result.cause());
  }

  @Test
  void patchItem_shouldHandleUnexpectedExceptionInUpdateProcess() {
    // Given
    var pgConnection = mock(PgConnection.class);
    var patchRequest = createItemPatchRequest();

    // Trigger exception during preparedQuery call
    when(pgConnection.preparedQuery(anyString())).thenThrow(new RuntimeException("Unexpected error"));

    // When
    var result = itemRepository.patchItem(pgConnection, patchRequest);

    // Then
    assertTrue(result.failed());
    assertInstanceOf(RuntimeException.class, result.cause());
    assertEquals("Unexpected error", result.cause().getMessage());
  }

  private List<ItemPatchRequest> createItemPatchRequests(int count) {
    var items = new ArrayList<ItemPatchRequest>();
    for (int i = 0; i < count; i++) {
      var itemPatch = new ItemPatchRequest()
        .withId(UUID.randomUUID().toString())
        .withAdditionalProperty("barcode", "barcode-" + i)
        .withAdditionalProperty("status", Map.of("name", "Available"));
      items.add(itemPatch);
    }

    return items;
  }

  private List<ItemPatchRequest> createComplexItemPatchRequests() {
    var items = new ArrayList<ItemPatchRequest>();

    items.add(new ItemPatchRequest()
      .withId(UUID.randomUUID().toString())
      .withAdditionalProperty("barcode", "complex-001")
      .withAdditionalProperty("status", Map.of("name", "Available"))
      .withAdditionalProperty("effectiveLocationId", UUID.randomUUID().toString())
      .withAdditionalProperty("materialTypeId", UUID.randomUUID().toString())
      .withAdditionalProperty("permanentLoanTypeId", UUID.randomUUID().toString()));

    items.add(new ItemPatchRequest()
      .withId(UUID.randomUUID().toString())
      .withAdditionalProperty("barcode", "complex-002")
      .withAdditionalProperty("status", Map.of("name", "Checked out"))
      .withAdditionalProperty("holdingsRecordId", UUID.randomUUID().toString()));

    return items;
  }

  @SuppressWarnings("unchecked")
  private RowSet<Row> createMockRowSet(int itemCount) {
    var mockRowSet = (RowSet<Row>) mock(RowSet.class);
    var rows = new ArrayList<Row>();

    for (int i = 0; i < itemCount; i++) {
      var itemJson = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("barcode", "barcode-" + i)
        .put("status", new JsonObject().put("name", "Available"));

      var mockRow = mock(Row.class);
      when(mockRow.getString(0)).thenReturn(itemJson.encode());
      rows.add(mockRow);
    }

    when(mockRowSet.iterator()).thenReturn(createRowIterator(rows));
    when(mockRowSet.next()).thenReturn(null);

    return mockRowSet;
  }

  @SuppressWarnings("unchecked")
  private RowSet<Row> createMockRowSetWithChaining() {
    var rows1 = createChainedRows(0, 2);
    final var mockRowSet1 = (RowSet<Row>) mock(RowSet.class);
    when(mockRowSet1.iterator()).thenReturn(createRowIterator(rows1));

    var rows2 = createChainedRows(2, 1);
    final var mockRowSet2 = (RowSet<Row>) mock(RowSet.class);
    when(mockRowSet2.iterator()).thenReturn(createRowIterator(rows2));
    when(mockRowSet2.next()).thenReturn(null);

    when(mockRowSet1.next()).thenReturn(mockRowSet2);

    return mockRowSet1;
  }

  private List<Row> createChainedRows(int startIndex, int count) {
    var rows = new ArrayList<Row>();
    for (int i = startIndex; i < startIndex + count; i++) {
      var itemJson = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("barcode", "chained-" + i)
        .put("status", new JsonObject().put("name", "Available"));
      var mockRow = mock(Row.class);
      when(mockRow.getString(0)).thenReturn(itemJson.encode());
      rows.add(mockRow);
    }

    return rows;
  }

  @SuppressWarnings("unchecked")
  private RowSet<Row> createMockRowSetWithComplexItems() {
    var rows = new ArrayList<Row>();

    var item1Json = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("barcode", "complex-001")
      .put("status", new JsonObject().put("name", "Available"))
      .put("effectiveLocationId", UUID.randomUUID().toString())
      .put("materialTypeId", UUID.randomUUID().toString());

    var item2Json = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("barcode", "complex-002")
      .put("status", new JsonObject().put("name", "Checked out"))
      .put("holdingsRecordId", UUID.randomUUID().toString());

    var mockRow1 = mock(Row.class);
    when(mockRow1.getString(0)).thenReturn(item1Json.encode());
    rows.add(mockRow1);

    var mockRow2 = mock(Row.class);
    when(mockRow2.getString(0)).thenReturn(item2Json.encode());
    rows.add(mockRow2);

    final var mockRowSet = (RowSet<Row>) mock(RowSet.class);
    when(mockRowSet.iterator()).thenReturn(createRowIterator(rows));
    when(mockRowSet.next()).thenReturn(null);

    return mockRowSet;
  }

  @SuppressWarnings("unchecked")
  private RowSet<Row> createMockRowSetWithJson(String json) {
    var mockRowSet = (RowSet<Row>) mock(RowSet.class);
    var mockRow = mock(Row.class);
    when(mockRow.getString(0)).thenReturn(json);

    when(mockRowSet.iterator()).thenReturn(createRowIterator(List.of(mockRow)));
    when(mockRowSet.next()).thenReturn(null);

    return mockRowSet;
  }

  @SuppressWarnings("unchecked")
  private RowSet<Row> createEmptyMockRowSet() {
    var mockRowSet = (RowSet<Row>) mock(RowSet.class);
    when(mockRowSet.iterator()).thenReturn(createRowIterator(new ArrayList<>()));
    when(mockRowSet.next()).thenReturn(null);

    return mockRowSet;
  }

  private RowIterator<Row> createRowIterator(List<Row> rows) {
    return new RowIterator<>() {
      private final Iterator<Row> iterator = rows.iterator();

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public Row next() {
        return iterator.next();
      }
    };
  }

  private void verifySql(PgConnection pgConnection) {
    verify(pgConnection).preparedQuery(argThat(sql ->
      sql != null
      && sql.contains("UPDATE")
      && sql.contains(".item")
      && sql.contains("SET jsonb = jsonb || $1")
      && sql.contains("WHERE id = $2")
      && sql.contains("RETURNING jsonb::text")));
  }

  private void verifyPatchSql(PgConnection pgConnection) {
    verify(pgConnection).preparedQuery(argThat(sql ->
      sql != null
        && sql.contains("UPDATE")
        && sql.contains(".item")
        && sql.contains("SET jsonb = apply_jsonb_patch(jsonb, $1)")
        && sql.contains("WHERE id = $2")
        && sql.contains("RETURNING jsonb::text")));
  }
}
