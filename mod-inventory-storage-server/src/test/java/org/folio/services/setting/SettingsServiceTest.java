package org.folio.services.setting;

import static org.folio.services.consortium.entities.Settings.INVENTORY_OPTIMIZE_UPDATES_ENABLED;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.exceptions.SettingsValidationException;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.caches.SettingCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SettingsServiceTest {

  private static final String USER_ID = "00000000-0000-0000-0000-000000000000";

  private SettingCache cache;
  private SettingsService settingsService;
  private Map<String, String> okapiHeaders;
  private PostgresClient postgresClient;
  private MockedStatic<PgUtil> mockedPgUtil;

  @BeforeEach
  void setUp() {
    var vertx = mock(Vertx.class);
    postgresClient = mock(PostgresClient.class);
    cache = mock(SettingCache.class);
    var context = mock(Context.class);
    var httpClient = mock(HttpClientAgent.class);
    var consortiumDataCache = mock(ConsortiumDataCache.class);

    when(context.owner()).thenReturn(vertx);
    when(vertx.createHttpClient()).thenReturn(httpClient);
    when(context.get(ConsortiumDataCache.class.getName())).thenReturn(consortiumDataCache);
    when(context.get(SettingCache.class.getName())).thenReturn(cache);
    when(postgresClient.getTenantId()).thenReturn(TENANT_ID);

    mockedPgUtil = mockStatic(PgUtil.class);
    mockedPgUtil.when(() -> PgUtil.postgresClient(any(Context.class), any(Map.class)))
      .thenReturn(postgresClient);
    okapiHeaders = Map.of("X-Okapi-Tenant", TENANT_ID, "X-Okapi-User-Id", USER_ID);
    settingsService = new SettingsService(context, okapiHeaders);
  }

  @AfterEach
  void tearDown() {
    mockedPgUtil.close();
  }

  @Test
  void getSettingByKeyShouldReturnSettingWhenFound() {
    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();
    var expectedSetting = createTestSetting(key, "true", Setting.Type.BOOLEAN);
    setupQueryMock(expectedSetting);

    var result = settingsService.getSettingByKey(key);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(notNullValue()));
    assertThat(result.result().getKey(), is(key));
  }

  @Test
  void getSettingByKeyShouldFailWhenSettingNotFound() {
    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();
    setupQueryMock(null);

    var result = settingsService.getSettingByKey(key);

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(NotFoundException.class));
  }

  @Test
  void updateSettingShouldUpdateSettingSuccessfully() {
    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();
    var setting = createTestSetting(key, "false", Setting.Type.BOOLEAN);
    setupQueryMock(setting);

    var result = settingsService.updateSetting(key, true, okapiHeaders);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  void updateSettingShouldFailOnValidation() {
    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();
    var setting = createTestSetting(key, "any value", Setting.Type.INTEGER);
    setupQueryMock(setting);

    var result = settingsService.updateSetting(key, "not-an-integer", okapiHeaders);

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(SettingsValidationException.class));
  }

  @Test
  void isOptimizeUpdatesEnabledShouldReturnTrueWhenEnabled() {
    when(cache.get(anyString(), any())).thenReturn(Future.succeededFuture("true"));

    boolean result = settingsService.isOptimizeUpdatesEnabled(TENANT_ID);

    assertThat(result, is(true));
  }

  @Test
  void isOptimizeUpdatesEnabledShouldReturnFalseWhenDisabled() {
    when(cache.get(anyString(), any())).thenReturn(Future.succeededFuture("false"));

    boolean result = settingsService.isOptimizeUpdatesEnabled(TENANT_ID);

    assertThat(result, is(false));
  }

  private void setupQueryMock(Setting setting) {
    var selectRowSet = buildRowSet(setting);
    var updateRowSet = buildRowSet(setting);

    when(postgresClient.execute(anyString(), any(Tuple.class))).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.trim().toUpperCase().startsWith("SELECT")) {
        return Future.succeededFuture(selectRowSet);
      }
      return Future.succeededFuture(updateRowSet);
    });
  }

  private RowSet<Row> buildRowSet(Setting setting) {
    RowSet<Row> rowSet = mock(RowSet.class);
    RowIterator<Row> iterator = mock(RowIterator.class);
    when(rowSet.iterator()).thenReturn(iterator);

    if (setting != null) {
      var row = mock(Row.class);
      when(iterator.hasNext()).thenReturn(true, false);
      when(iterator.next()).thenReturn(row);
      when(rowSet.size()).thenReturn(1);
      when(row.getUUID("id")).thenReturn(setting.getId());
      when(row.getString("key")).thenReturn(setting.getKey());
      when(row.getString("value")).thenReturn(setting.getValue());
      when(row.getString("type")).thenReturn(setting.getType().name());
      when(row.getBoolean("central_managed")).thenReturn(setting.getCentralManaged());
      when(row.getString("description")).thenReturn(setting.getDescription());
      when(row.getUUID("created_by_user_id")).thenReturn(setting.getCreatedByUserId());
      when(row.getUUID("updated_by_user_id")).thenReturn(setting.getUpdatedByUserId());
    } else {
      when(iterator.hasNext()).thenReturn(false);
      when(rowSet.size()).thenReturn(0);
    }

    return rowSet;
  }

  private Setting createTestSetting(String key, String value, Setting.Type type) {
    return new Setting()
      .withId(UUID.randomUUID())
      .withKey(key)
      .withValue(value)
      .withType(type)
      .withCentralManaged(false)
      .withDescription("Test setting")
      .withCreatedByUserId(UUID.fromString(USER_ID))
      .withCreatedDate(new Date())
      .withUpdatedByUserId(UUID.fromString(USER_ID))
      .withUpdatedDate(new Date());
  }
}
