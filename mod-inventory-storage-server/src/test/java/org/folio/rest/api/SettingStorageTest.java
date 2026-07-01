package org.folio.rest.api;

import static org.folio.services.consortium.entities.Settings.INVENTORY_OPTIMIZE_UPDATES_ENABLED;
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
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class SettingStorageTest extends TestBaseWithInventoryUtil {

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
    var updateResponse = updateSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), false);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    var getResponse = getSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue());

    // Assert that the setting value has been updated with the value equal to false
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getString("value"), is("false"));

    var newUpdateResponse = updateSettingByKey(INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue(), true);
    assertThat(newUpdateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

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

  private Response getSettingByKey(String key) {
    return settingsClient.getByIdIfPresent(key);
  }
}
