package org.folio.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractRepositoryTest {

  private static final String TABLE_NAME = "settings";
  private static final String TENANT_ID = "diku";
  private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final String ID_STRING = ID.toString();

  private static final Map<String, String> HEADERS = Map.of("X-Okapi-Tenant", TENANT_ID);

  private @Mock PostgresClient postgresClient;
  private @Mock Conn conn;
  private @Mock RowSet<Row> rowSet;
  private TestRepository repository;

  @BeforeEach
  void setUp() {
    repository = new TestRepository(postgresClient);
  }

  @Test
  @DisplayName("should delegate to postgresClient when saving")
  void shouldDelegateToPostgresClient_whenSaving() {
    var setting = new Setting().withId(ID);
    when(postgresClient.save(TABLE_NAME, "id-1", setting)).thenReturn(Future.succeededFuture("id-1"));

    var result = repository.save("id-1", setting, tenantId(HEADERS));

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

    var result = repository.get(criterion, tenantId(HEADERS));

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
    when(postgresClient.getById(TABLE_NAME, ID_STRING, Setting.class)).thenReturn(Future.succeededFuture(setting));

    var result = repository.getById(ID_STRING, tenantId(HEADERS));

    assertThat(result.result()).isEqualTo(setting);
  }

  @Test
  @DisplayName("should delegate to postgresClient when fetching by a collection of ids")
  void shouldDelegateToPostgresClient_whenFetchingByCollectionOfIds() {
    var setting = new Setting().withId(ID);
    var expected = Map.of(ID_STRING, setting);
    stubGetByIds(expected);

    var result = repository.getByIds(List.of(ID_STRING), tenantId(HEADERS));

    assertThat(result.result()).isEqualTo(expected);
  }

  @Test
  @DisplayName("should dedupe ids before delegating when fetching by a collection mapped to ids")
  void shouldDedupeIds_whenFetchingByCollectionMappedToIds() {
    var settingA = new Setting().withKey("a-key").withId(ID);
    var settingB = new Setting().withKey("b-key").withId(ID);
    stubGetByIdsCapturingIds(Map.of(ID_STRING, settingA));

    var result = repository.getByIds(List.of(settingA, settingB), setting -> setting.getId().toString(),
      tenantId(HEADERS));

    assertThat(result.result()).containsExactly(Map.entry(ID_STRING, settingA));
  }

  @Test
  @DisplayName("should fail with invalid uuid when fetching by an id that is not a uuid")
  void shouldFailWithInvalidUuid_whenFetchingByIdThatIsNotUuid() {
    var result = repository.getById("id-1", tenantId(HEADERS));

    assertThat(result.cause()).isInstanceOf(ValidationException.class);
    verifyNoInteractions(postgresClient);
  }

  @Test
  @DisplayName("should fail with invalid uuid when fetching by a collection with an id that is not a uuid")
  void shouldFailWithInvalidUuid_whenFetchingByCollectionWithIdThatIsNotUuid() {
    var result = repository.getByIds(List.of(ID_STRING, "id-1"), tenantId(HEADERS));

    assertThat(result.cause()).isInstanceOf(ValidationException.class);
    verifyNoInteractions(postgresClient);
  }

  @Test
  @DisplayName("should return true when a row is found")
  void shouldReturnTrue_whenRowIsFound() {
    stubExists(1);

    var result = repository.exists(ID_STRING, tenantId(HEADERS));

    assertThat(result.result()).isTrue();
  }

  @Test
  @DisplayName("should return false when no row is found")
  void shouldReturnFalse_whenNoRowIsFound() {
    stubExists(0);

    var result = repository.exists(ID_STRING, tenantId(HEADERS));

    assertThat(result.result()).isFalse();
  }

  @Test
  @DisplayName("should delegate to postgresClient when updating by id")
  void shouldDelegateToPostgresClient_whenUpdatingById() {
    var setting = new Setting().withId(ID);
    when(postgresClient.update(TABLE_NAME, setting, "id-1")).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.update("id-1", setting, tenantId(HEADERS));

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  @DisplayName("should delegate to the connection when updating with a connection")
  void shouldDelegateToConnection_whenUpdatingWithConnection() {
    var setting = new Setting().withId(ID);
    when(conn.update(TABLE_NAME, setting, "id-1")).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.update("id-1", setting, conn);

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  @DisplayName("should delegate to the connection when upserting a batch")
  void shouldDelegateToConnection_whenUpsertingBatch() {
    var records = List.of(new Setting().withId(ID));
    when(conn.upsertBatch(TABLE_NAME, records)).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.upsertBatch(records, conn);

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  @DisplayName("should delegate to the connection when updating a batch")
  void shouldDelegateToConnection_whenUpdatingBatch() {
    var records = List.of(new Setting().withId(ID));
    when(conn.updateBatch(TABLE_NAME, records)).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.updateBatch(records, conn);

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  @DisplayName("should apply the builder then persist the result when fetching and updating")
  void shouldApplyBuilderThenPersistResult_whenFetchingAndUpdating() {
    var existing = new Setting().withId(ID).withValue("false");
    var updated = new Setting().withId(ID).withValue("true");
    when(conn.getByIdForUpdate(TABLE_NAME, ID_STRING, Setting.class)).thenReturn(Future.succeededFuture(existing));
    when(conn.update(TABLE_NAME, updated, ID_STRING)).thenReturn(Future.succeededFuture(rowSet));
    stubWithTrans();

    var result = repository.fetchAndUpdate(ID_STRING, setting -> setting.withValue("true"), tenantId(HEADERS));

    assertThat(result.result()).isEqualTo(updated);
    verify(conn).update(TABLE_NAME, updated, ID_STRING);
  }

  @Test
  @DisplayName("should fail with invalid uuid when checking existence of an id that is not a uuid")
  void shouldFailWithInvalidUuid_whenCheckingExistenceOfIdThatIsNotUuid() {
    var result = repository.exists("id-1", tenantId(HEADERS));

    assertThat(result.cause()).isInstanceOf(ValidationException.class);
    verifyNoInteractions(postgresClient);
  }

  @Test
  @DisplayName("should fail with invalid uuid when fetching and updating an id that is not a uuid")
  void shouldFailWithInvalidUuid_whenFetchingAndUpdatingIdThatIsNotUuid() {
    var result = repository.fetchAndUpdate("id-1", setting -> setting, tenantId(HEADERS));

    assertThat(result.cause()).isInstanceOf(ValidationException.class);
    verifyNoInteractions(postgresClient);
  }

  @Test
  @DisplayName("should fail with not found when fetching and updating a record that does not exist")
  void shouldFailWithNotFound_whenFetchingAndUpdatingMissingRecord() {
    when(conn.getByIdForUpdate(TABLE_NAME, ID_STRING, Setting.class)).thenReturn(Future.succeededFuture(null));
    stubWithTrans();

    var result = repository.fetchAndUpdate(ID_STRING, setting -> setting, tenantId(HEADERS));

    assertThat(result.cause()).isInstanceOf(org.folio.rest.exceptions.NotFoundException.class);
  }

  @Test
  @DisplayName("should fail with bad request when streaming with an invalid cql")
  void shouldFailWithBadRequest_whenStreamingWithInvalidCql() {
    var result = repository.stream(Setting.class, "invalid cql ((", 0, 10, tenantId(HEADERS));

    assertThat(result.cause()).isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("should delegate to postgresClient when deleting all records")
  void shouldDelegateToPostgresClient_whenDeletingAllRecords() {
    when(postgresClient.delete(eq(TABLE_NAME), any(Criterion.class))).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.deleteAll(tenantId(HEADERS));

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  @DisplayName("should delegate to postgresClient when deleting by id")
  void shouldDelegateToPostgresClient_whenDeletingById() {
    when(rowSet.rowCount()).thenReturn(1);
    when(postgresClient.delete(TABLE_NAME, "id-1")).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.deleteById("id-1", tenantId(HEADERS));

    assertThat(result.result()).isTrue();
    verify(postgresClient, never()).delete(eq(TABLE_NAME), any(Criterion.class));
  }

  @Test
  @DisplayName("should prefix the converted tenant schema when given an explicit table name")
  void shouldPrefixConvertedTenantSchema_whenGivenExplicitTableName() {
    assertThat(repository.getFullTableName("other_table", tenantId(HEADERS)))
      .isEqualTo(PostgresClient.convertToPsqlStandard(TENANT_ID) + ".other_table");
  }

  @Test
  @DisplayName("should use its own table name when none is given")
  void shouldUseOwnTableName_whenNoneIsGiven() {
    assertThat(repository.getFullTableName(tenantId(HEADERS)))
      .isEqualTo(PostgresClient.convertToPsqlStandard(TENANT_ID) + "." + TABLE_NAME);
  }

  @Test
  @DisplayName("should use only the additional cql when the cql is blank")
  void shouldUseOnlyAdditionalCql_whenCqlIsBlank() {
    var wrapper = queryAndCaptureWrapper("", "key==foo");

    assertThat(wrapper.toString())
      .contains("jsonb->>'key'")
      .contains("'foo'")
      .doesNotContain("jsonb->>'value'");
  }

  @Test
  @DisplayName("should combine cql and additional cql when both are given")
  void shouldCombineCqlAndAdditionalCql_whenBothAreGiven() {
    var wrapper = queryAndCaptureWrapper("value==bar", "key==foo");

    assertThat(wrapper.toString())
      .contains("jsonb->>'value'").contains("'bar'")
      .contains("jsonb->>'key'").contains("'foo'");
  }

  @Test
  @DisplayName("should ignore the additional cql when it is null")
  void shouldIgnoreAdditionalCql_whenItIsNull() {
    var wrapper = queryAndCaptureWrapper("value==bar", null);

    assertThat(wrapper.toString())
      .contains("jsonb->>'value'")
      .doesNotContain("jsonb->>'key'");
  }

  @Test
  @DisplayName("should fail with bad request when the cql is invalid")
  void shouldFailWithBadRequest_whenCqlIsInvalid() {
    var result = repository.query("((", "key==foo", 0, 10, "exact", tenantId(HEADERS));

    assertThat(result.cause()).isInstanceOf(BadRequestException.class);
  }

  private CQLWrapper queryAndCaptureWrapper(String cql, String additionalCql) {
    when(postgresClient.get(eq(TABLE_NAME), eq(Setting.class), any(CQLWrapper.class), eq(true)))
      .thenReturn(Future.succeededFuture(new Results<>()));

    repository.query(cql, additionalCql, 0, 10, "exact", tenantId(HEADERS));

    var captor = ArgumentCaptor.forClass(CQLWrapper.class);
    verify(postgresClient).get(eq(TABLE_NAME), eq(Setting.class), captor.capture(), eq(true));
    return captor.getValue();
  }

  private void stubExists(int rowCount) {
    when(rowSet.rowCount()).thenReturn(rowCount);
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

  private void stubGetByIds(Map<String, Setting> toReturn) {
    doAnswer(invocation -> {
      Handler<AsyncResult<Map<String, Setting>>> handler = invocation.getArgument(3);
      handler.handle(Future.succeededFuture(toReturn));
      return null;
    }).when(postgresClient).getById(eq(TABLE_NAME), any(JsonArray.class), eq(Setting.class), any());
  }

  private void stubGetByIdsCapturingIds(Map<String, Setting> toReturn) {
    doAnswer(invocation -> {
      JsonArray ids = invocation.getArgument(1);
      assertThat(ids).hasSize(1).containsExactly(ID_STRING);
      Handler<AsyncResult<Map<String, Setting>>> handler = invocation.getArgument(3);
      handler.handle(Future.succeededFuture(toReturn));
      return null;
    }).when(postgresClient).getById(eq(TABLE_NAME), any(JsonArray.class), eq(Setting.class), any());
  }

  private static final class TestRepository extends AbstractRepository<Setting> {
    private final PostgresClient postgresClient;

    TestRepository(PostgresClient postgresClient) {
      super(Vertx.vertx().getOrCreateContext(), TABLE_NAME, Setting.class);
      this.postgresClient = postgresClient;
    }

    @Override
    protected PostgresClient postgresClient(String tenantId) {
      return postgresClient;
    }
  }
}
