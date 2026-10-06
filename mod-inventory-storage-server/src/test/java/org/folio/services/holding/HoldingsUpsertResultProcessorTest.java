package org.folio.services.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsUpsertResultProcessorTest {

  private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Test
  @DisplayName("should return empty maps when the row set is empty")
  void shouldReturnEmptyMaps_whenRowSetIsEmpty() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(rowSetOf());

    assertThat(result.getLeft()).isEmpty();
    assertThat(result.getRight()).isEmpty();
  }

  @Test
  @DisplayName("should skip the holdings entry when old content is the null sentinel")
  void shouldSkipHoldingsEntry_whenOldContentIsNullSentinel() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(
      rowSetOf(row(ID, "null", null)));

    assertThat(result.getLeft()).isEmpty();
  }

  @Test
  @DisplayName("should parse and store the old holdings record when content is valid json")
  void shouldParseAndStoreOldHoldingsRecord_whenContentIsValidJson() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(
      rowSetOf(row(ID, "{\"id\":\"" + ID + "\"}", null)));

    assertThat(result.getLeft()).containsOnlyKeys(ID.toString());
    assertThat(result.getLeft().get(ID.toString()).getId()).isEqualTo(ID.toString());
  }

  @Test
  @DisplayName("should skip the holdings entry when content is invalid json")
  void shouldSkipHoldingsEntry_whenContentIsInvalidJson() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(
      rowSetOf(row(ID, "not-json", null)));

    assertThat(result.getLeft()).isEmpty();
  }

  @Test
  @DisplayName("should keep the first parsed holdings record when the same id repeats")
  void shouldKeepFirstParsedHoldingsRecord_whenSameIdRepeats() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(rowSetOf(
      row(ID, "{\"id\":\"" + ID + "\",\"hrid\":\"first\"}", null),
      row(ID, "{\"id\":\"" + ID + "\",\"hrid\":\"second\"}", null)));

    assertThat(result.getLeft().get(ID.toString()).getHrid()).isEqualTo("first");
  }

  @Test
  @DisplayName("should not add an item entry when old item content is null")
  void shouldNotAddItemEntry_whenOldItemContentIsNull() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(
      rowSetOf(row(ID, "null", null)));

    assertThat(result.getRight()).isEmpty();
  }

  @Test
  @DisplayName("should parse and store the old item record when content is valid json")
  void shouldParseAndStoreOldItemRecord_whenContentIsValidJson() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(
      rowSetOf(row(ID, "null", "{\"id\":\"item-1\"}")));

    assertThat(result.getRight()).containsOnlyKeys(ID.toString());
    assertThat(result.getRight().get(ID.toString())).extracting("id").containsExactly("item-1");
  }

  @Test
  @DisplayName("should append multiple items under the same holdings id")
  void shouldAppendMultipleItems_underSameHoldingsId() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(rowSetOf(
      row(ID, "null", "{\"id\":\"item-1\"}"),
      row(ID, "null", "{\"id\":\"item-2\"}")));

    assertThat(result.getRight().get(ID.toString())).extracting("id")
      .containsExactly("item-1", "item-2");
  }

  @Test
  @DisplayName("should keep item lists separate per holdings id")
  void shouldKeepItemListsSeparate_perHoldingsId() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(rowSetOf(
      row(ID, "null", "{\"id\":\"item-1\"}"),
      row(OTHER_ID, "null", "{\"id\":\"item-2\"}")));

    assertThat(result.getRight()).containsOnlyKeys(ID.toString(), OTHER_ID.toString());
  }

  @Test
  @DisplayName("should skip the item entry when content is invalid json")
  void shouldSkipItemEntry_whenContentIsInvalidJson() {
    var result = HoldingsUpsertResultProcessor.processUpsertResultSet(
      rowSetOf(row(ID, "null", "not-json")));

    assertThat(result.getRight()).isEmpty();
  }

  @Test
  @DisplayName("should throw when instantiated directly")
  void shouldThrow_whenInstantiatedDirectly() throws NoSuchMethodException {
    var constructor = HoldingsUpsertResultProcessor.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    assertThatThrownBy(constructor::newInstance)
      .isInstanceOf(InvocationTargetException.class)
      .cause().isInstanceOf(UnsupportedOperationException.class);
  }

  @SuppressWarnings("unchecked")
  private static RowSet<Row> rowSetOf(Row... rows) {
    var iterator = mock(RowIterator.class);
    var hasNextAnswers = new Boolean[rows.length + 1];
    Arrays.fill(hasNextAnswers, 0, rows.length, true);
    hasNextAnswers[rows.length] = false;
    when(iterator.hasNext()).thenReturn(hasNextAnswers[0],
      Arrays.copyOfRange(hasNextAnswers, 1, hasNextAnswers.length));
    if (rows.length > 0) {
      when(iterator.next()).thenReturn(rows[0], Arrays.copyOfRange(rows, 1, rows.length));
    }

    var rowSet = mock(RowSet.class);
    when(rowSet.iterator()).thenReturn(iterator);
    return rowSet;
  }

  private static Row row(UUID id, String oldHoldingsContent, String oldItemContent) {
    var row = mock(Row.class);
    when(row.getUUID(0)).thenReturn(id);
    when(row.getString(1)).thenReturn(oldHoldingsContent);
    when(row.getString(2)).thenReturn(oldItemContent);
    return row;
  }
}
