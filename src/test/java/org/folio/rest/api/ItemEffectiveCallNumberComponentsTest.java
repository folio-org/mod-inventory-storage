package org.folio.rest.api;


import static org.folio.rest.api.ItemStorageTest.nod;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.api.testdata.ItemEffectiveCallNumberComponentsTestData;
import org.folio.rest.api.testdata.ItemEffectiveCallNumberComponentsTestData.CallNumberComponentPropertyNames;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveCallNumberComponentsTest extends TestBaseWithInventoryUtil {
  public static final String HOLDINGS_CALL_NUMBER_TYPE = UUID.randomUUID().toString();
  public static final String HOLDINGS_CALL_NUMBER_TYPE_SECOND = UUID.randomUUID().toString();
  public static final String ITEM_LEVEL_CALL_NUMBER_TYPE = UUID.randomUUID().toString();
  public static final String ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND = UUID.randomUUID().toString();

  @BeforeClass
  public static void createCallNumberTypes() throws Exception {
    callNumberTypesClient.deleteIfPresent(HOLDINGS_CALL_NUMBER_TYPE);
    callNumberTypesClient.deleteIfPresent(HOLDINGS_CALL_NUMBER_TYPE_SECOND);
    callNumberTypesClient.deleteIfPresent(ITEM_LEVEL_CALL_NUMBER_TYPE);
    callNumberTypesClient.deleteIfPresent(ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND);

    callNumberTypesClient.create(new JsonObject()
      .put("id", HOLDINGS_CALL_NUMBER_TYPE)
      .put("name", "Test Holdings call number type")
      .put("source", "folio")
    );
    callNumberTypesClient.create(new JsonObject()
      .put("id", HOLDINGS_CALL_NUMBER_TYPE_SECOND)
      .put("name", "Test Holdings call number type second")
      .put("source", "folio")
    );
    callNumberTypesClient.create(new JsonObject()
      .put("id", ITEM_LEVEL_CALL_NUMBER_TYPE)
      .put("name", "Test Item level call number type")
      .put("source", "folio")
    );
    callNumberTypesClient.create(new JsonObject()
      .put("id", ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND)
      .put("name", "Test Item level call number type second")
      .put("source", "folio")
    );
  }

  @Test
  @Parameters(
    source = ItemEffectiveCallNumberComponentsTestData.class,
    method = "createPropertiesParams"
  )
  @TestCaseName("[{index}]: {params}")
  public void canCalculateEffectiveCallNumberPropertyOnCreate(
    CallNumberComponentPropertyNames callNumberProperties,
    String holdingsPropertyValue, String itemPropertyValue) throws Exception {

    final String effectiveValue = StringUtils.firstNonBlank(itemPropertyValue, holdingsPropertyValue);
    IndividualResource holdings = createHoldingsWithPropertySetAndInstance(
      callNumberProperties.holdingsPropertyName, holdingsPropertyValue
    );

    IndividualResource createdItem = itemsClient.create(
      nod(null, holdings.getId())
        .put(callNumberProperties.itemPropertyName, itemPropertyValue)
    );
    assertThat(createdItem.getJson()
        .getString(callNumberProperties.itemPropertyName),
      is(itemPropertyValue)
    );

    Response getResponse = itemsClient.getById(createdItem.getId());
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject effectiveCallNumberComponents = getResponse.getJson()
      .getJsonObject("effectiveCallNumberComponents");

    assertNotNull(effectiveCallNumberComponents);
    assertThat(effectiveCallNumberComponents
        .getString(callNumberProperties.effectivePropertyName),
      is(effectiveValue)
    );
  }

  @Test
  @Parameters(
    source = ItemEffectiveCallNumberComponentsTestData.class,
    method = "updatePropertiesParams"
  )
  @TestCaseName("[{index}]: {params}")
  public void canCalculateEffectiveCallNumberPropertyOnUpdate(
    CallNumberComponentPropertyNames callNumberProperties,
    String holdingsInitValue, String holdingsTargetValue,
    String itemInitValue, String itemTargetValue) throws Exception {

    final String holdingsPropertyName = callNumberProperties.holdingsPropertyName;
    final String itemPropertyName = callNumberProperties.itemPropertyName;
    final String effectivePropertyName = callNumberProperties.effectivePropertyName;

    final String initEffectiveValue = StringUtils.firstNonBlank(itemInitValue, holdingsInitValue);
    final String targetEffectiveValue = StringUtils.firstNonBlank(itemTargetValue, holdingsTargetValue);

    IndividualResource holdings = createHoldingsWithPropertySetAndInstance(
      holdingsPropertyName, holdingsInitValue
    );

    IndividualResource createdItem = itemsClient.create(
      nod(null, holdings.getId())
        .put(itemPropertyName, itemInitValue)
    );

    JsonObject effectiveCallNumberComponents = createdItem.getJson()
      .getJsonObject("effectiveCallNumberComponents");

    assertNotNull(effectiveCallNumberComponents);
    assertThat(effectiveCallNumberComponents.getString(effectivePropertyName),
      is(initEffectiveValue));

    holdingsClient.replace(holdings.getId(),
      holdings.copyJson()
        .put(holdingsPropertyName, holdingsTargetValue)
    );

    itemsClient.replace(createdItem.getId(),
      createdItem.copyJson()
        .put(itemPropertyName, itemTargetValue)
    );

    JsonObject updatedEffectiveCallNumberComponents = itemsClient
      .getById(createdItem.getId()).getJson()
      .getJsonObject("effectiveCallNumberComponents");

    assertNotNull(updatedEffectiveCallNumberComponents);
    assertThat(updatedEffectiveCallNumberComponents.getString(effectivePropertyName),
      is(targetEffectiveValue));
  }

  private IndividualResource createHoldingsWithPropertySetAndInstance(
    String propertyName, String propertyValue)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource instance = instancesClient.create(instance(UUID.randomUUID()));

    IndividualResource holdings = holdingsClient.create(new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instance.getId())
      .withPermanentLocation(mainLibraryLocationId)
      .create()
      .put(propertyName, propertyValue)
    );
    assertThat(holdings.getJson().getString(propertyName), is(propertyValue));

    return holdings;
  }
}
