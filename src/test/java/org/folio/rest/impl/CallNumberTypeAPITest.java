package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.UUID;
import junitparams.JUnitParamsRunner;
import org.folio.rest.api.TestBase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class CallNumberTypeAPITest extends TestBase {

  private static final String NAME_FIELD = "name";
  private static final String NAME_VALUE = "test type";
  private static final String SOURCE_FIELD = "source";
  private static final String SOURCE_VALUE = "system";

  private static UUID callNumberTypeId;

  @BeforeClass
  public static void setUp() {
    var requestBody = new JsonObject().put(NAME_FIELD, NAME_VALUE).put(SOURCE_FIELD, SOURCE_VALUE);
    var response = callNumberTypesClient.create(requestBody);
    callNumberTypeId = response.getId();
  }

  @Test
  public void shouldRespondWith400_whenAttemptToDeleteSystemType() {
    var response = callNumberTypesClient.attemptToDelete(callNumberTypeId);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    var callNumberTypeResponse = callNumberTypesClient.getById(callNumberTypeId);
    assertThat(callNumberTypeResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(callNumberTypeResponse.getJson().getString(NAME_FIELD), is(NAME_VALUE));
    assertThat(callNumberTypeResponse.getJson().getString(SOURCE_FIELD), is(SOURCE_VALUE));
  }

  @Test
  public void shouldRespondWith400_whenAttemptToUpdateSystemType() {
    var requestBody = new JsonObject().put(NAME_FIELD, "updated").put(SOURCE_FIELD, "local");
    var response = callNumberTypesClient.attemptToReplace(callNumberTypeId, requestBody);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));

    var callNumberTypeResponse = callNumberTypesClient.getById(callNumberTypeId);
    assertThat(callNumberTypeResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(callNumberTypeResponse.getJson().getString(NAME_FIELD), is(NAME_VALUE));
    assertThat(callNumberTypeResponse.getJson().getString(SOURCE_FIELD), is(SOURCE_VALUE));
  }
}
