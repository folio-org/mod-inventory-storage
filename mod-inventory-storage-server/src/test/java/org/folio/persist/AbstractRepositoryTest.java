package org.folio.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractRepositoryTest {

  private static final String TABLE_NAME = "settings";
  private static final String TENANT_ID = "diku";
  private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private PostgresClient postgresClient;
  private Conn conn;
  private TestRepository repository;

  @BeforeEach
  void setUp() {
    postgresClient = mock(PostgresClient.class);
    conn = mock(Conn.class);
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);
    repository = new TestRepository(postgresClient);
  }

  @Test
  @DisplayName("should delegate to postgresClient when saving")
  void shouldDelegateToPostgresClient_whenSaving() {
    var setting = new Setting().withId(ID);
    when(postgresClient.save(TABLE_NAME, "id-1", setting)).thenReturn(Future.succeededFuture("id-1"));

    var result = repository.save("id-1", setting);

    assertThat(result.result()).isEqualTo("id-1");
    verify(postgresClient).save(TABLE_NAME, "id-1", setting);
  }

  @Test
  @DisplayName("should unwrap results when fetching by criterion")
  void shouldUnwrapResults_whenFetchingByCriterion() {
    var criterion = new Criterion();
    var settings = List.of(new Setting().withId(ID));
    var results = new Results<Setting>();
    results.setResults(settings);
    when(postgresClient.get(TABLE_NAME, Setting.class, criterion, false))
      .thenReturn(Future.succeededFuture(results));

    var result = repository.get(criterion);

    assertThat(result.result()).isEqualTo(settings);
  }

  @Test
  @DisplayName("should unwrap results when fetching by criterion with a connection")
  void shouldUnwrapResults_whenFetchingByCriterionWithConnection() {
    var criterion = new Criterion();
    var settings = List.of(new Setting().withId(ID));
    var results = new Results<Setting>();
    results.setResults(settings);
    when(conn.get(TABLE_NAME, Setting.class, criterion, false))
      .thenReturn(Future.succeededFuture(results));

    var result = repository.get(conn, criterion);

    assertThat(result.result()).isEqualTo(settings);
  }

  @Test
  @DisplayName("should delegate to postgresClient when fetching by id")
  void shouldDelegateToPostgresClient_whenFetchingById() {
    var setting = new Setting().withId(ID);
    when(postgresClient.getById(TABLE_NAME, "id-1", Setting.class)).thenReturn(Future.succeededFuture(setting));

    var result = repository.getById("id-1");

    assertThat(result.result()).isEqualTo(setting);
  }

  @Test
  @DisplayName("should delegate to postgresClient when fetching by a collection of ids")
  void shouldDelegateToPostgresClient_whenFetchingByCollectionOfIds() {
    var setting = new Setting().withId(ID);
    var expected = Map.of("id-1", setting);
    stubGetByIds(expected);

    var result = repository.getByIds(List.of("id-1"));

    assertThat(result.result()).isEqualTo(expected);
  }

  @Test
  @DisplayName("should dedupe ids before delegating when fetching by a collection mapped to ids")
  void shouldDedupeIds_whenFetchingByCollectionMappedToIds() {
    var settingA = new Setting().withKey("a-key").withId(ID);
    var settingB = new Setting().withKey("b-key").withId(ID);
    stubGetByIdsCapturingIds(Map.of("id-1", settingA));

    var result = repository.getByIds(List.of(settingA, settingB), setting -> setting.getId().toString());

    assertThat(result.result()).containsExactly(Map.entry("id-1", settingA));
  }

  @Test
  @DisplayName("should return true when a row is found")
  void shouldReturnTrue_whenRowIsFound() {
    stubExists(1);

    var result = repository.exists("id-1");

    assertThat(result.result()).isTrue();
  }

  @Test
  @DisplayName("should return false when no row is found")
  void shouldReturnFalse_whenNoRowIsFound() {
    stubExists(0);

    var result = repository.exists("id-1");

    assertThat(result.result()).isFalse();
  }

  @Test
  @DisplayName("should delegate to postgresClient when updating by id")
  void shouldDelegateToPostgresClient_whenUpdatingById() {
    var setting = new Setting().withId(ID);
    var rowSet = mock(RowSet.class);
    when(postgresClient.update(TABLE_NAME, setting, "id-1")).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.update("id-1", setting);

    assertThat(result.result()).isSameAs(rowSet);
  }

  @Test
  @DisplayName("should delegate to the connection when updating with a connection")
  void shouldDelegateToConnection_whenUpdatingWithConnection() {
    var setting = new Setting().withId(ID);
    var rowSet = mock(RowSet.class);
    when(conn.update(TABLE_NAME, setting, "id-1")).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.update(conn, "id-1", setting);

    assertThat(result.result()).isSameAs(rowSet);
  }

  @Test
  @DisplayName("should delegate to the connection when upserting a batch")
  void shouldDelegateToConnection_whenUpsertingBatch() {
    var records = List.of(new Setting().withId(ID));
    var rowSet = mock(RowSet.class);
    when(conn.upsertBatch(TABLE_NAME, records)).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.upsertBatch(records, conn);

    assertThat(result.result()).isSameAs(rowSet);
  }

  @Test
  @DisplayName("should delegate to the connection when updating a batch")
  void shouldDelegateToConnection_whenUpdatingBatch() {
    var records = List.of(new Setting().withId(ID));
    var rowSet = mock(RowSet.class);
    when(conn.updateBatch(TABLE_NAME, records)).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.updateBatch(records, conn);

    assertThat(result.result()).isSameAs(rowSet);
  }

  @Test
  @DisplayName("should apply the builder then persist the result when fetching and updating")
  void shouldApplyBuilderThenPersistResult_whenFetchingAndUpdating() {
    var existing = new Setting().withId(ID).withValue("false");
    var updated = new Setting().withId(ID).withValue("true");
    when(conn.getByIdForUpdate(TABLE_NAME, "id-1", Setting.class)).thenReturn(Future.succeededFuture(existing));
    when(conn.update(TABLE_NAME, updated, "id-1")).thenReturn(Future.succeededFuture(mock(RowSet.class)));
    stubWithTrans();

    var result = repository.fetchAndUpdate("id-1", setting -> setting.withValue("true"));

    assertThat(result.result()).isEqualTo(updated);
    verify(conn).update(TABLE_NAME, updated, "id-1");
  }

  @Test
  @DisplayName("should delegate to postgresClient when deleting all records")
  void shouldDelegateToPostgresClient_whenDeletingAllRecords() {
    var rowSet = mock(RowSet.class);
    when(postgresClient.delete(eq(TABLE_NAME), any(Criterion.class))).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.deleteAll();

    assertThat(result.result()).isSameAs(rowSet);
  }

  @Test
  @DisplayName("should delegate to postgresClient when deleting by id")
  void shouldDelegateToPostgresClient_whenDeletingById() {
    var rowSet = mock(RowSet.class);
    when(postgresClient.delete(TABLE_NAME, "id-1")).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.deleteById("id-1");

    assertThat(result.result()).isSameAs(rowSet);
    verify(postgresClient, never()).delete(eq(TABLE_NAME), any(Criterion.class));
  }

  @Test
  @DisplayName("should prefix the converted tenant schema when given an explicit table name")
  void shouldPrefixConvertedTenantSchema_whenGivenExplicitTableName() {
    assertThat(repository.getFullTableName("other_table"))
      .isEqualTo(PostgresClient.convertToPsqlStandard(TENANT_ID) + ".other_table");
  }

  @Test
  @DisplayName("should use its own table name when none is given")
  void shouldUseOwnTableName_whenNoneIsGiven() {
    assertThat(repository.getFullTableName())
      .isEqualTo(PostgresClient.convertToPsqlStandard(TENANT_ID) + "." + TABLE_NAME);
  }

  @Test
  @DisplayName("should use only the specific cql when the generic cql is blank")
  void shouldUseOnlySpecificCql_whenGenericCqlIsBlank() throws Exception {
    var wrapper = repository.getFetchCqlWrapper("", 0, 10, "exact", "key==foo");

    assertThat(wrapper.toString())
      .contains("jsonb->>'key'")
      .contains("'foo'")
      .doesNotContain("jsonb->>'value'");
  }

  @Test
  @DisplayName("should combine generic and specific cql when the generic cql is not blank")
  void shouldCombineGenericAndSpecificCql_whenGenericCqlIsNotBlank() throws Exception {
    var wrapper = repository.getFetchCqlWrapper("value==bar", 0, 10, "exact", "key==foo");

    assertThat(wrapper.toString())
      .contains("jsonb->>'value'").contains("'bar'")
      .contains("jsonb->>'key'").contains("'foo'");
  }

  private void stubExists(int rowCount) {
    var rowSet = mock(RowSet.class);
    when(rowSet.rowCount()).thenReturn(rowCount);
    when(postgresClient.getSchemaName()).thenReturn("diku_mod_inventory_storage");
    when(postgresClient.execute(
      eq("select 1 from diku_mod_inventory_storage.settings where id = $1 limit 1"), any(Tuple.class)))
      .thenReturn(Future.succeededFuture(rowSet));
  }

  private void stubWithTrans() {
    when(postgresClient.withTrans(any())).thenAnswer(invocation -> {
      Function<Conn, Future<Setting>> fn = invocation.getArgument(0);
      return fn.apply(conn);
    });
  }

  @SuppressWarnings("unchecked")
  private void stubGetByIds(Map<String, Setting> toReturn) {
    doAnswer(invocation -> {
      Handler<AsyncResult<Map<String, Setting>>> handler = invocation.getArgument(3);
      handler.handle(Future.succeededFuture(toReturn));
      return null;
    }).when(postgresClient).getById(eq(TABLE_NAME), any(JsonArray.class), eq(Setting.class), any());
  }

  @SuppressWarnings("unchecked")
  private void stubGetByIdsCapturingIds(Map<String, Setting> toReturn) {
    doAnswer(invocation -> {
      JsonArray ids = invocation.getArgument(1);
      assertThat(ids).hasSize(1).containsExactly(ID.toString());
      Handler<AsyncResult<Map<String, Setting>>> handler = invocation.getArgument(3);
      handler.handle(Future.succeededFuture(toReturn));
      return null;
    }).when(postgresClient).getById(eq(TABLE_NAME), any(JsonArray.class), eq(Setting.class), any());
  }

  private static final class TestRepository extends AbstractRepository<Setting> {
    private TestRepository(PostgresClient postgresClient) {
      super(postgresClient, TABLE_NAME, Setting.class);
    }
  }
}
