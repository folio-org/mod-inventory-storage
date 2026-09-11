package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.CONSORTIUM_MEMBER_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import org.folio.it.BaseIntegrationTest;
import org.folio.services.domainevent.SettingEvent;
import org.folio.support.ResourcePaths;
import org.folio.support.extension.EnableTenant;
import org.folio.support.messages.SettingEventMessageChecks;
import org.folio.utility.RestUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code /inventory-settings/{key}} endpoint, focusing on the
 * {@code inventory.optimize-updates.enabled} setting: plain CRUD, validation, and how a
 * centrally-managed setting propagates from a consortium's central tenant to its members.
 */
@EnableTenant(tenants = {TENANT_ID, CONSORTIUM_CENTRAL_TENANT, CONSORTIUM_MEMBER_TENANT})
class SettingStorageIT extends BaseIntegrationTest {

  private static final String SETTING_KEY = "inventory.optimize-updates.enabled";
  private static final String NON_EXISTENT_KEY = "non.existent.setting.key";
  private static final String SETTING_PATH = ResourcePaths.INVENTORY_SETTINGS + "/" + SETTING_KEY;
  private static final String ID_FIELD = "id";
  private static final String KEY_FIELD = "key";
  private static final String VALUE_FIELD = "value";
  private static final String TYPE_FIELD = "type";
  private static final String CENTRAL_MANAGED_FIELD = "centralManaged";
  private static final String DESCRIPTION_FIELD = "description";
  private static final String BOOLEAN_TYPE = "BOOLEAN";
  // the true production default, seeded once by Liquibase (08-create-settings-table.xml) into
  // every tenant schema - settings is excluded from per-class truncation
  // (BaseIntegrationTest.MIGRATION_SEEDED_TABLES), so every test here must restore this exact
  // value afterward, or it leaks into whichever *IT class shares the JVM and runs next.
  private static final boolean DEFAULT_SETTING_VALUE = false;

  private final SettingEventMessageChecks settingEventMessageChecks = new SettingEventMessageChecks(KAFKA_CONSUMER);

  @BeforeEach
  void mockConsortiumSetup() {
    mockUserTenantsForConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    mockUserTenantsForConsortiumMember(CONSORTIUM_MEMBER_TENANT);
    mockConsortiumTenants();
  }

  @AfterEach
  void restoreSettingValue() {
    // Not just fire-and-forget: SettingsService.isOptimizeUpdatesEnabled() (read by every other
    // *IT class that toggles this setting, e.g. InstanceStorageIT) is served from SettingCache, a
    // 24h-TTL cache that is only ever refreshed reactively - by SettingUpdateKafkaHandler
    // consuming the SettingEvent this PATCH publishes - never invalidated synchronously by the
    // PATCH itself. If we returned as soon as the PATCH responds, a class that runs right after
    // this one and immediately reads the cached value could race that async refresh and observe
    // a stale value. Waiting here for the restore event to actually appear (the same signal
    // SettingUpdateKafkaHandler consumes from the same topic) closes that window.
    //
    // TENANT_ID and CONSORTIUM_CENTRAL_TENANT each hold their own row in their own tenant schema;
    // restoring the central tenant's row also propagates to the member tenant (see
    // shouldPublishEventForMemberTenant_whenCentralTenantUpdates), so a direct member-tenant
    // restore isn't needed (and isn't possible - a member tenant can't PATCH this setting).
    restoreAndAwait(TENANT_ID);
    restoreAndAwait(CONSORTIUM_CENTRAL_TENANT);
  }

  private void restoreAndAwait(String tenantId) {
    var settingId = await(doGet(client, SETTING_PATH, tenantId)).jsonBody().getString(ID_FIELD);
    // a matching event may already sit in the buffer from earlier in this same test (e.g. an
    // interim false->true->false toggle) - discard first so the await below can only be
    // satisfied by the event this restore itself publishes, not a stale leftover.
    KAFKA_CONSUMER.discardAllMessages();
    var response = await(doPatch(client, SETTING_PATH, tenantId, updateRequest(DEFAULT_SETTING_VALUE)));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    settingEventMessageChecks.settingEventPublished(
      new SettingEvent(settingId, SETTING_KEY, DEFAULT_SETTING_VALUE, tenantId));
  }

  @Test
  @DisplayName("should return the setting when the key exists")
  void shouldReturnSetting_whenKeyExists() {
    var response = await(doGet(client, SETTING_PATH));

    assertThat(response.status()).isEqualTo(SC_OK);
    var setting = response.jsonBody();
    assertThat(setting.getString(KEY_FIELD)).isEqualTo(SETTING_KEY);
    assertThat(setting.getString(VALUE_FIELD)).isNotNull();
    assertThat(setting.getString(TYPE_FIELD)).isEqualTo(BOOLEAN_TYPE);
  }

  @Test
  @DisplayName("should return 404 when getting a setting that does not exist")
  void shouldReturn404_whenGettingNonExistentSetting() {
    var response = await(doGet(client, ResourcePaths.INVENTORY_SETTINGS + "/" + NON_EXISTENT_KEY));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should return the setting with all expected fields")
  void shouldReturnSetting_withAllExpectedFields() {
    var response = await(doGet(client, SETTING_PATH));

    assertThat(response.status()).isEqualTo(SC_OK);
    var setting = response.jsonBody();
    assertThat(setting.getMap()).containsKeys(
      ID_FIELD, KEY_FIELD, VALUE_FIELD, TYPE_FIELD, CENTRAL_MANAGED_FIELD, DESCRIPTION_FIELD);
  }

  @Test
  @DisplayName("should return a consistent type across multiple gets")
  void shouldReturnConsistentType_acrossMultipleGets() {
    var first = await(doGet(client, SETTING_PATH)).jsonBody();
    var second = await(doGet(client, SETTING_PATH)).jsonBody();

    assertThat(first.getString(TYPE_FIELD)).isEqualTo(second.getString(TYPE_FIELD));
  }

  @Test
  @DisplayName("should update the setting value and publish an event, in both directions")
  void shouldUpdateSettingValue_andPublishEvent() {
    var settingId = await(doGet(client, SETTING_PATH)).jsonBody().getString(ID_FIELD);

    assertThat(await(doPatch(client, SETTING_PATH, updateRequest(false))).status()).isEqualTo(SC_NO_CONTENT);
    settingEventMessageChecks.settingEventPublished(new SettingEvent(settingId, SETTING_KEY, false, TENANT_ID));
    assertThat(await(doGet(client, SETTING_PATH)).jsonBody().getString(VALUE_FIELD)).isEqualTo("false");

    KAFKA_CONSUMER.discardAllMessages();

    assertThat(await(doPatch(client, SETTING_PATH, updateRequest(true))).status()).isEqualTo(SC_NO_CONTENT);
    settingEventMessageChecks.settingEventPublished(new SettingEvent(settingId, SETTING_KEY, true, TENANT_ID));
    assertThat(await(doGet(client, SETTING_PATH)).jsonBody().getString(VALUE_FIELD)).isEqualTo("true");
  }

  @Test
  @DisplayName("should update the setting multiple times, publishing an event each time")
  void shouldUpdateSettingMultipleTimes_publishingEventEachTime() {
    var settingId = await(doGet(client, SETTING_PATH)).jsonBody().getString(ID_FIELD);

    updateAndVerify(settingId, false);
    updateAndVerify(settingId, true);
    updateAndVerify(settingId, false);
  }

  private void updateAndVerify(String settingId, boolean value) {
    assertThat(await(doPatch(client, SETTING_PATH, updateRequest(value))).status()).isEqualTo(SC_NO_CONTENT);
    settingEventMessageChecks.settingEventPublished(new SettingEvent(settingId, SETTING_KEY, value, TENANT_ID));
    assertThat(await(doGet(client, SETTING_PATH)).jsonBody().getString(VALUE_FIELD)).isEqualTo(String.valueOf(value));
    KAFKA_CONSUMER.discardAllMessages();
  }

  @Test
  @DisplayName("should keep the same id after an update")
  void shouldKeepSameId_afterUpdate() {
    var before = await(doGet(client, SETTING_PATH)).jsonBody();
    var idBefore = before.getString(ID_FIELD);

    await(doPatch(client, SETTING_PATH, updateRequest(!Boolean.parseBoolean(before.getString(VALUE_FIELD)))));
    settingEventMessageChecks.settingEventPublished(idBefore);

    var idAfter = await(doGet(client, SETTING_PATH)).jsonBody().getString(ID_FIELD);
    assertThat(idAfter).isEqualTo(idBefore);
  }

  @Test
  @DisplayName("should return 422 when updating with a non-boolean value")
  void shouldReturn422_whenUpdatingWithInvalidType() {
    var response =
      await(doPatch(client, SETTING_PATH, new JsonObject().put(VALUE_FIELD, "not a boolean")));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating with a null value")
  void shouldReturn422_whenUpdatingWithNullValue() {
    var response = await(doPatch(client, SETTING_PATH, new JsonObject().putNull(VALUE_FIELD)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 404 when updating a setting that does not exist")
  void shouldReturn404_whenUpdatingNonExistentSetting() {
    var response = await(doPatch(client, ResourcePaths.INVENTORY_SETTINGS + "/" + NON_EXISTENT_KEY,
      updateRequest(true)));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should update the setting and publish an event for a non-consortium tenant")
  void shouldUpdateSettingAndPublishEvent_forNonConsortiumTenant() {
    var settingId = await(doGet(client, SETTING_PATH)).jsonBody().getString(ID_FIELD);

    assertThat(await(doPatch(client, SETTING_PATH, updateRequest(true))).status()).isEqualTo(SC_NO_CONTENT);
    settingEventMessageChecks.settingEventPublished(new SettingEvent(settingId, SETTING_KEY, true, TENANT_ID));
    assertThat(await(doGet(client, SETTING_PATH)).jsonBody().getString(VALUE_FIELD)).isEqualTo("true");
  }

  @Test
  @DisplayName("should update the setting and publish an event for the consortium central tenant")
  void shouldUpdateSettingAndPublishEvent_forConsortiumCentralTenant() {
    var initial = await(doGet(client, SETTING_PATH, RestUtility.CONSORTIUM_CENTRAL_TENANT));
    assertThat(initial.status()).isEqualTo(SC_OK);
    var settingId = initial.jsonBody().getString(ID_FIELD);
    var newValue = !"true".equals(initial.jsonBody().getString(VALUE_FIELD));

    var updateResponse = await(
      doPatch(client, SETTING_PATH, RestUtility.CONSORTIUM_CENTRAL_TENANT, updateRequest(newValue)));
    assertThat(updateResponse.status()).isEqualTo(SC_NO_CONTENT);
    settingEventMessageChecks.settingEventPublished(settingId);

    var getResponse = await(doGet(client, SETTING_PATH, RestUtility.CONSORTIUM_CENTRAL_TENANT));
    assertThat(getResponse.status()).isEqualTo(SC_OK);
    assertThat(getResponse.jsonBody().getString(VALUE_FIELD)).isEqualTo(String.valueOf(newValue));
  }

  @Test
  @DisplayName("should return 400 when a consortium member tenant tries to update a centrally-managed setting")
  void shouldReturn400_whenUpdatingFromConsortiumMemberTenant() {
    var initial = await(doGet(client, SETTING_PATH, RestUtility.CONSORTIUM_MEMBER_TENANT));
    assertThat(initial.status()).isEqualTo(SC_OK);
    var initialValue = initial.jsonBody().getString(VALUE_FIELD);
    var newValue = !"true".equals(initialValue);

    var updateResponse =
      await(doPatch(client, SETTING_PATH, RestUtility.CONSORTIUM_MEMBER_TENANT, updateRequest(newValue)));
    assertThat(updateResponse.status()).isEqualTo(SC_BAD_REQUEST);

    var getResponse = await(doGet(client, SETTING_PATH, RestUtility.CONSORTIUM_MEMBER_TENANT));
    assertThat(getResponse.status()).isEqualTo(SC_OK);
    assertThat(getResponse.jsonBody().getString(VALUE_FIELD)).isEqualTo(initialValue);
  }

  @Test
  @DisplayName("should publish an event with the central tenant id when the central tenant updates")
  void shouldPublishEvent_withCentralTenantId_whenCentralTenantUpdates() {
    var initial = await(doGet(client, SETTING_PATH, RestUtility.CONSORTIUM_CENTRAL_TENANT)).jsonBody();
    var settingId = initial.getString(ID_FIELD);
    var newValue = !"true".equals(initial.getString(VALUE_FIELD));

    var updateResponse = await(
      doPatch(client, SETTING_PATH, RestUtility.CONSORTIUM_CENTRAL_TENANT, updateRequest(newValue)));
    assertThat(updateResponse.status()).isEqualTo(SC_NO_CONTENT);

    settingEventMessageChecks.settingEventPublishedForTenant(settingId, CONSORTIUM_CENTRAL_TENANT);
  }

  @Test
  @DisplayName("should publish an event for the member tenant when the central tenant updates")
  void shouldPublishEventForMemberTenant_whenCentralTenantUpdates() {
    var memberSettingId = await(doGet(client, SETTING_PATH, RestUtility.CONSORTIUM_MEMBER_TENANT))
      .jsonBody().getString(ID_FIELD);
    var central = await(doGet(client, SETTING_PATH, RestUtility.CONSORTIUM_CENTRAL_TENANT)).jsonBody();
    var centralSettingId = central.getString(ID_FIELD);
    var newValue = !"true".equals(central.getString(VALUE_FIELD));

    var updateResponse = await(
      doPatch(client, SETTING_PATH, RestUtility.CONSORTIUM_CENTRAL_TENANT, updateRequest(newValue)));
    assertThat(updateResponse.status()).isEqualTo(SC_NO_CONTENT);

    // the central tenant's update propagates to the member tenant, publishing an event for each
    settingEventMessageChecks.settingEventPublished(
      new SettingEvent(memberSettingId, SETTING_KEY, newValue, CONSORTIUM_MEMBER_TENANT));
    settingEventMessageChecks.settingEventPublished(
      new SettingEvent(centralSettingId, SETTING_KEY, newValue, CONSORTIUM_CENTRAL_TENANT));
  }

  private static JsonObject updateRequest(boolean value) {
    return new JsonObject().put(VALUE_FIELD, value);
  }
}
