package org.folio.rest.api;

import static org.folio.services.consortium.entities.Settings.INVENTORY_OPTIMIZE_UPDATES_ENABLED;
import static org.folio.utility.ModuleUtility.prepareTenant;
import static org.folio.utility.ModuleUtility.removeTenant;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.CONSORTIUM_MEMBER_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.HashMap;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.SettingUpdateRequest;
import org.folio.rest.support.Response;
import org.folio.rest.support.messages.SettingEventMessageChecks;
import org.folio.services.domainevent.SettingEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class SettingStorageTest extends TestBaseWithInventoryUtil {

  private final SettingEventMessageChecks settingEventMessageChecks =
    new SettingEventMessageChecks(KAFKA_CONSUMER);

  @SneakyThrows
  @BeforeClass
  public static void beforeClass() {
    // Prepare consortium tenants for testing
    prepareTenant(CONSORTIUM_CENTRAL_TENANT, false);
    prepareTenant(CONSORTIUM_MEMBER_TENANT, false);

    mockUserTenantsForNonConsortiumMember();
    mockUserTenantsForConsortiumMember(CONSORTIUM_CENTRAL_TENANT);
    mockUserTenantsForConsortiumMember(CONSORTIUM_MEMBER_TENANT);
    mockConsortiumTenants();
  }

  @SneakyThrows
  @AfterClass
  public static void afterClass() {
    removeTenant(CONSORTIUM_CENTRAL_TENANT);
    removeTenant(CONSORTIUM_MEMBER_TENANT);
  }

  @Before
  public void beforeEach() {
    KAFKA_CONSUMER.discardAllMessages();
  }

  @Test
  @SneakyThrows
  public void canGetSettingByKey() {
    var response = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());

    // Assert
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject setting = response.getJson();
    assertThat(setting.getString("key"), is(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue()));
    assertThat(setting.getString("value"), is(notNullValue()));
    assertThat(setting.getString("type"), is("BOOLEAN"));
  }

  @Test
  @SneakyThrows
  public void cannotGetNonExistentSetting() {
    // Arrange
    var nonExistentKey = "NON_EXISTENT_SETTING_KEY";

    // Act
    var response = getSettingByKey(nonExistentKey);

    // Assert
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  @SneakyThrows
  public void canUpdateSettingValue() {
    // Get the setting ID first
    var initialResponse = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());
    var settingId = initialResponse.getJson().getString("id");

    // Update setting to false - this should publish a setting event
    var updateResponse = updateSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), false);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    // Verify setting event was published
    var settingEvent = new SettingEvent(settingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), false, TENANT_ID);
    settingEventMessageChecks.settingEventPublished(settingEvent);

    var getResponse = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());

    // Assert that the setting value has been updated with the value equal to false
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("value"), is("false"));

    // Clear messages before next update
    KAFKA_CONSUMER.discardAllMessages();

    var newUpdateResponse = updateSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), true);
    assertThat(newUpdateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    // Verify setting event was published for the second update
    var settingEvent2 = new SettingEvent(settingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), true, TENANT_ID);
    settingEventMessageChecks.settingEventPublished(settingEvent2);

    var getUpdateResponse = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());

    // Assert that the setting value has been updated with the new value equal to true
    assertThat(getUpdateResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getUpdateResponse.getJson().getString("value"), is("true"));
  }

  @Test
  @SneakyThrows
  public void cannotUpdateSettingWithInvalidType() {
    // Act
    var settingRequest = new JsonObject(JsonObject.mapFrom(new SettingUpdateRequest()
      .withValue("not a boolean")).encode());
    var headers = new HashMap<String, String>();
    headers.put(XOkapiHeaders.TENANT, TENANT_ID);
    headers.put(XOkapiHeaders.URL, mockServer.baseUrl());
    var updateResponse = settingsClient.attemptToUpdate(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(),
      settingRequest, TENANT_ID, headers);

    // Assert
    assertThat(updateResponse.getStatusCode(), is(422));
  }

  @Test
  @SneakyThrows
  public void canRetrieveSettingWithCorrectFields() {
    // Act
    var response = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());

    // Assert
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    var setting = response.getJson();

    assertThat(setting.containsKey("id"), is(true));
    assertThat(setting.containsKey("key"), is(true));
    assertThat(setting.containsKey("value"), is(true));
    assertThat(setting.containsKey("type"), is(true));
    assertThat(setting.containsKey("centralManaged"), is(true));
    assertThat(setting.containsKey("description"), is(true));
  }

  @Test
  @SneakyThrows
  public void settingTypeIsConsistent() {
    // Act
    var response1 = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());
    var response2 = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());

    // Assert - Type should remain the same across multiple calls
    var setting1 = response1.getJson();
    var setting2 = response2.getJson();

    assertThat(setting1.getString("type"), is(equalTo(setting2.getString("type"))));
  }

  @Test
  @SneakyThrows
  public void canUpdateSettingAndPublishEventForNonConsortiumTenant() {
    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();
    var initialResponse = getSettingByKey(key);
    var settingId = initialResponse.getJson().getString("id");

    // Update setting
    var updateResponse = updateSettingByKey(key, true);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    // Verify setting event was published to Kafka
    var settingEvent = new SettingEvent(settingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), true, TENANT_ID);
    settingEventMessageChecks.settingEventPublished(settingEvent);

    // Verify setting was updated
    var getResponse = getSettingByKey(key);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("value"), is("true"));
  }

  @Test
  @SneakyThrows
  @SuppressWarnings("checkstyle:MethodLength")
  public void canUpdateSettingMultipleTimes() {
    // This test verifies that multiple setting updates work correctly
    // and each update publishes a setting event to Kafka

    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();

    // Get the setting ID
    var initialResponse = getSettingByKey(key);
    var settingId = initialResponse.getJson().getString("id");

    // First update - set to false
    var response1 = updateSettingByKey(key, false);
    assertThat(response1.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    // Verify event was published
    var settingEvent1 = new SettingEvent(settingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), false, TENANT_ID);
    settingEventMessageChecks.settingEventPublished(settingEvent1);

    var getValue1 = getSettingByKey(key);
    assertThat(getValue1.getJson().getString("value"), is("false"));

    // Clear messages before next update
    KAFKA_CONSUMER.discardAllMessages();

    // Second update - set to true
    var response2 = updateSettingByKey(key, true);
    assertThat(response2.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    // Verify event was published
    var settingEvent2 = new SettingEvent(settingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), true, TENANT_ID);
    settingEventMessageChecks.settingEventPublished(settingEvent2);

    var getValue2 = getSettingByKey(key);
    assertThat(getValue2.getJson().getString("value"), is("true"));

    // Clear messages before next update
    KAFKA_CONSUMER.discardAllMessages();

    // Third update - set back to false
    var response3 = updateSettingByKey(key, false);
    assertThat(response3.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    // Verify event was published
    var settingEvent3 = new SettingEvent(settingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), false, TENANT_ID);
    settingEventMessageChecks.settingEventPublished(settingEvent3);

    var getValue3 = getSettingByKey(key);
    assertThat(getValue3.getJson().getString("value"), is("false"));

    // Restore original state
    updateSettingByKey(key, true);
  }

  @Test
  @SneakyThrows
  public void cannotUpdateNonExistentSetting() {
    var settingRequest = new JsonObject(JsonObject.mapFrom(new SettingUpdateRequest()
      .withValue(true)).encode());
    var headers = new HashMap<String, String>();
    headers.put(XOkapiHeaders.TENANT, TENANT_ID);
    headers.put(XOkapiHeaders.URL, mockServer.baseUrl());

    var response = settingsClient.attemptToUpdate("non.existent.setting.key",
      settingRequest, TENANT_ID, headers);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  @SneakyThrows
  public void updateSettingWithNullValueShouldFail() {
    var settingRequest = new JsonObject().putNull("value");
    var headers = new HashMap<String, String>();
    headers.put(XOkapiHeaders.TENANT, TENANT_ID);
    headers.put(XOkapiHeaders.URL, mockServer.baseUrl());

    var response = settingsClient.attemptToUpdate(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(),
      settingRequest, TENANT_ID, headers);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  @SneakyThrows
  public void settingIdShouldRemainConstantAfterUpdate() {
    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();

    // Get setting before update
    var beforeUpdate = getSettingByKey(key);
    var idBeforeUpdate = beforeUpdate.getJson().getString("id");

    // Update setting
    updateSettingByKey(key, !Boolean.parseBoolean(beforeUpdate.getJson().getString("value")));

    // Verify setting event was published
    settingEventMessageChecks.settingEventPublished(idBeforeUpdate);

    // Get setting after update
    var afterUpdate = getSettingByKey(key);
    var idAfterUpdate = afterUpdate.getJson().getString("id");

    // ID should remain the same
    assertThat(idAfterUpdate, is(idBeforeUpdate));
  }

  @Test
  @SneakyThrows
  public void canUpdateSettingAndPublishEventForConsortiumCentralTenant() {
    // This test verifies that when a setting is updated by the central tenant,
    // a setting event is published to Kafka

    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();

    // Get the setting for central tenant
    var initialResponse = getSettingByKeyForTenant(key, CONSORTIUM_CENTRAL_TENANT);
    assertThat(initialResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    var settingId = initialResponse.getJson().getString("id");
    var initialValue = initialResponse.getJson().getString("value");

    // Update setting for central tenant
    var newValue = !"true".equals(initialValue);
    var updateResponse = updateSettingByKeyForTenant(key, newValue, CONSORTIUM_CENTRAL_TENANT);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    // Verify setting event was published to Kafka with all fields
    settingEventMessageChecks.settingEventPublished(settingId);

    // Verify setting was updated
    var getResponse = getSettingByKeyForTenant(key, CONSORTIUM_CENTRAL_TENANT);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("value"), is(String.valueOf(newValue)));

    // Restore original value
    var settingEvent = new SettingEvent(settingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), newValue,
      CONSORTIUM_CENTRAL_TENANT);
    settingEventMessageChecks.settingEventPublished(settingEvent);
  }

  @Test
  @SneakyThrows
  public void cannotUpdateSettingFromConsortiumMemberTenant() {
    // This test verifies that when a setting is centrally managed,
    // a member tenant cannot update it directly (only central tenant can)

    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();

    // Get the setting for member tenant
    var initialResponse = getSettingByKeyForTenant(key, CONSORTIUM_MEMBER_TENANT);
    assertThat(initialResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    var initialValue = initialResponse.getJson().getString("value");

    // Attempt to update setting for member tenant - should fail with HTTP 400
    var newValue = !"true".equals(initialValue);
    var updateResponse = updateSettingByKeyForTenant(key, newValue, CONSORTIUM_MEMBER_TENANT);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    // Verify setting was NOT updated
    var getResponse = getSettingByKeyForTenant(key, CONSORTIUM_MEMBER_TENANT);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("value"), is(initialValue));
  }

  @Test
  @SneakyThrows
  public void settingEventContainsCorrectTenantIdForCentralTenant() {
    // This test verifies that the setting event contains the correct tenant ID
    // when updated by the central tenant

    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();

    // Get the setting for central tenant
    var initialResponse = getSettingByKeyForTenant(key, CONSORTIUM_CENTRAL_TENANT);
    var settingId = initialResponse.getJson().getString("id");
    var initialValue = initialResponse.getJson().getString("value");

    // Update setting for central tenant
    var newValue = !"true".equals(initialValue);
    updateSettingByKeyForTenant(key, newValue, CONSORTIUM_CENTRAL_TENANT);

    // Verify setting event was published with correct tenant ID
    settingEventMessageChecks.settingEventPublishedForTenant(settingId, CONSORTIUM_CENTRAL_TENANT);
  }

  @Test
  @SneakyThrows
  public void settingEventContainsCorrectTenantIdForMemberTenant() {
    // This test verifies that when the central tenant updates a centrally managed setting,
    // the setting event propagated to member tenant contains the correct member tenant ID

    var key = INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue();

    // Get the setting for member tenant to get its ID
    var memberResponse = getSettingByKeyForTenant(key, CONSORTIUM_MEMBER_TENANT);
    var memberSettingId = memberResponse.getJson().getString("id");

    // Get the setting for central tenant
    var centralResponse = getSettingByKeyForTenant(key, CONSORTIUM_CENTRAL_TENANT);
    var initialValue = centralResponse.getJson().getString("value");
    var centralSettingId = centralResponse.getJson().getString("id");

    // Update setting from CENTRAL tenant - this should propagate to member tenant
    var newValue = !"true".equals(initialValue);
    updateSettingByKeyForTenant(key, newValue, CONSORTIUM_CENTRAL_TENANT);

    // Verify setting event was published with member tenant ID
    // The central tenant update propagates to member tenant, publishing an event for member
    var settingEventForMember = new SettingEvent(memberSettingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(),
      newValue, CONSORTIUM_MEMBER_TENANT);
    var settingEventForCentral = new SettingEvent(centralSettingId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(),
      newValue, CONSORTIUM_CENTRAL_TENANT);

    settingEventMessageChecks.settingEventPublished(settingEventForMember);
    settingEventMessageChecks.settingEventPublished(settingEventForCentral);
  }

  private Response getSettingByKey(String key) {
    return settingsClient.getByIdIfPresent(key);
  }

  private Response getSettingByKeyForTenant(String key, String tenantId) {
    return settingsClient.getByIdIfPresent(key, tenantId);
  }

  private Response updateSettingByKeyForTenant(String key, boolean value, String tenantId) {
    var settingRequest = new JsonObject(JsonObject.mapFrom(new SettingUpdateRequest()
      .withValue(value)).encode());

    var headers = new HashMap<String, String>();
    headers.put(XOkapiHeaders.TENANT, tenantId);
    headers.put(XOkapiHeaders.URL, mockServer.baseUrl());
    return settingsClient.attemptToUpdate(key, settingRequest, tenantId, headers);
  }
}
