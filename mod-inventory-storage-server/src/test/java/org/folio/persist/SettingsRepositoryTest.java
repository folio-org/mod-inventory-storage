package org.folio.persist;

import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SettingsRepositoryTest {

  private static final String TEST_KEY = "test_key";
  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";

  private SettingsRepository repository;
  private PostgresClient postgresClient;
  private MockedStatic<PgUtil> mockedPgUtil;

  @BeforeEach
  void setUp() {
    postgresClient = mock(PostgresClient.class);
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);
    mockedPgUtil = mockStatic(PgUtil.class);
    mockedPgUtil.when(() -> PgUtil.postgresClient(any(Context.class), any(Map.class)))
      .thenReturn(postgresClient);

    Vertx vertx = Vertx.vertx();
    Context context = vertx.getOrCreateContext();
    repository = new SettingsRepository(context, Map.of("X-Okapi-Tenant", TENANT_ID));
  }

  @AfterEach
  void tearDown() {
    mockedPgUtil.close();
  }

  @Test
  void findByKey_returnsSetting_whenFound() {
    var setting = createSetting();
    Row row = mock(Row.class);
    when(row.getUUID("id")).thenReturn(setting.getId());
    when(row.getString("key")).thenReturn(setting.getKey());
    when(row.getString("value")).thenReturn(setting.getValue());
    when(row.getString("type")).thenReturn(setting.getType().name());
    when(row.getBoolean("central_managed")).thenReturn(setting.getCentralManaged());
    when(row.getString("description")).thenReturn(setting.getDescription());
    when(row.getUUID("created_by_user_id")).thenReturn(setting.getCreatedByUserId());
    when(row.getLocalDateTime("created_date")).thenReturn(LocalDateTime.now());
    when(row.getUUID("updated_by_user_id")).thenReturn(setting.getUpdatedByUserId());
    when(row.getLocalDateTime("updated_date")).thenReturn(LocalDateTime.now());

    RowIterator<Row> iterator = mock(RowIterator.class);
    when(iterator.hasNext()).thenReturn(true, false);
    when(iterator.next()).thenReturn(row);

    RowSet<Row> rowSet = mock(RowSet.class);
    when(rowSet.iterator()).thenReturn(iterator);

    when(postgresClient.execute(anyString(), any(Tuple.class))).thenReturn(Future.succeededFuture(rowSet));
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);

    var result = repository.findByKey(TEST_KEY);

    assertTrue(result.succeeded());
    assertNotNull(result.result());
    assertEquals(TEST_KEY, result.result().getKey());
  }

  @Test
  void findByKey_returnsNull_whenNotFound() {
    RowIterator<Row> iterator = mock(RowIterator.class);
    when(iterator.hasNext()).thenReturn(false);

    RowSet<Row> rowSet = mock(RowSet.class);
    when(rowSet.iterator()).thenReturn(iterator);

    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(Future.succeededFuture(rowSet));

    var result = repository.findByKey(TEST_KEY);

    assertTrue(result.succeeded());
    assertNull(result.result());
  }

  @Test
  void update_returnsUpdatedSetting() {
    var setting = createSetting();
    setting.setValue("true");
    setting.setUpdatedByUserId(UUID.randomUUID());

    var row = mock(Row.class);
    when(row.getUUID("id")).thenReturn(setting.getId());
    when(row.getString("key")).thenReturn(setting.getKey());
    when(row.getString("value")).thenReturn(setting.getValue());
    when(row.getString("type")).thenReturn(setting.getType().name());
    when(row.getBoolean("central_managed")).thenReturn(setting.getCentralManaged());
    when(row.getString("description")).thenReturn(setting.getDescription());
    when(row.getUUID("created_by_user_id")).thenReturn(setting.getCreatedByUserId());
    when(row.getLocalDateTime("created_date")).thenReturn(LocalDateTime.now());
    when(row.getUUID("updated_by_user_id")).thenReturn(setting.getUpdatedByUserId());
    when(row.getLocalDateTime("updated_date")).thenReturn(LocalDateTime.now());

    RowIterator<Row> iterator = mock(RowIterator.class);
    when(iterator.hasNext()).thenReturn(true, false);
    when(iterator.next()).thenReturn(row);

    RowSet<Row> rowSet = mock(RowSet.class);
    when(rowSet.iterator()).thenReturn(iterator);
    when(postgresClient.execute(anyString(), any(Tuple.class))).thenReturn(Future.succeededFuture(rowSet));

    var result = repository.update(setting);

    assertTrue(result.succeeded());
    assertEquals("true", result.result().getValue());
  }

  @Test
  void findByKey_nullKey_throwsException() {
    assertThrows(RuntimeException.class,
      () -> repository.findByKey(null));
  }

  @Test
  void update_nullSetting_throwsException() {
    assertThrows(RuntimeException.class,
      () -> repository.update(new Setting()));
  }

  private Setting createSetting() {
    return new Setting()
      .withId(UUID.randomUUID())
      .withKey(TEST_KEY)
      .withValue("false")
      .withType(Setting.Type.BOOLEAN)
      .withCentralManaged(false)
      .withDescription("test")
      .withCreatedByUserId(UUID.fromString(USER_ID))
      .withCreatedDate(new Date())
      .withUpdatedByUserId(UUID.fromString(USER_ID))
      .withUpdatedDate(new Date());
  }
}
