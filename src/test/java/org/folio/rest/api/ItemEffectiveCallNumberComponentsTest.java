package org.folio.rest.api;


import static org.folio.rest.api.ItemStorageTest.nod;
import static org.folio.rest.support.matchers.ItemMatchers.effectiveCallNumberComponents;
import static org.folio.rest.support.matchers.ItemMatchers.hasCallNumber;
import static org.folio.rest.support.matchers.ItemMatchers.hasPrefix;
import static org.folio.rest.support.matchers.ItemMatchers.hasSuffix;
import static org.folio.rest.support.matchers.ItemMatchers.hasTypeId;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

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

import io.vertx.core.json.JsonArray;
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
  public void canCalculateEffectiveCallNumberPropertyOnBatchCreate() throws Exception {
    final UUID firstHoldingsId = createInstanceAndHoldingWithBuilder(mainLibraryLocationId,
      builder -> builder.withCallNumber("firstHRCallNumber")
        .withCallNumberPrefix("firstHRPrefix")
        .withCallNumberSuffix("firstHRSuffix")
        .withCallNumberTypeId(HOLDINGS_CALL_NUMBER_TYPE));

    final UUID secondHoldingsId = createInstanceAndHoldingWithBuilder(mainLibraryLocationId,
      builder -> builder.withCallNumber("secondHRCallNumber")
        .withCallNumberPrefix("secondHRPrefix")
        .withCallNumberSuffix("secondHRSuffix")
        .withCallNumberTypeId(HOLDINGS_CALL_NUMBER_TYPE_SECOND));

    final UUID thirdHoldingsId = createInstanceAndHolding(mainLibraryLocationId);

    final JsonObject useFirstHoldingsComponents = nod(firstHoldingsId);
    final JsonObject useOwnCallNumber = nod(secondHoldingsId)
      .put("itemLevelCallNumber", "ownCallNumber");
    final JsonObject useFirstHoldingsAndOwnSuffix = nod(firstHoldingsId)
      .put("itemLevelCallNumberSuffix", "ownSuffix");
    final JsonObject useAllOwnComponentsSharedHoldings = nod(firstHoldingsId)
      .put("itemLevelCallNumber", "allOwnComponentsCN")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS")
      .put("itemLevelCallNumberPrefix", "allOwnComponentsCNP")
      .put("itemLevelCallNumberTypeId", ITEM_LEVEL_CALL_NUMBER_TYPE);
    final JsonObject useAllOwnComponents = nod(thirdHoldingsId)
      .put("itemLevelCallNumber", "allOwnComponentsCN2")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS2")
      .put("itemLevelCallNumberPrefix", "allOwnComponentsCNP2")
      .put("itemLevelCallNumberTypeId", ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND);

    itemsStorageSyncClient.createNoResponse(new JsonObject()
      .put("items", new JsonArray()
        .add(useFirstHoldingsComponents)
        .add(useOwnCallNumber)
        .add(useFirstHoldingsAndOwnSuffix)
        .add(useAllOwnComponentsSharedHoldings)
        .add(useAllOwnComponents)));

    assertThat(getById(useFirstHoldingsComponents), effectiveCallNumberComponents(allOf(
      hasCallNumber("firstHRCallNumber"),
      hasSuffix("firstHRSuffix"),
      hasPrefix("firstHRPrefix"),
      hasTypeId(HOLDINGS_CALL_NUMBER_TYPE)
    )));

    assertThat(getById(useOwnCallNumber), effectiveCallNumberComponents(allOf(
      hasCallNumber("ownCallNumber"),
      hasSuffix("secondHRSuffix"),
      hasPrefix("secondHRPrefix"),
      hasTypeId(HOLDINGS_CALL_NUMBER_TYPE_SECOND)
    )));

    assertThat(getById(useFirstHoldingsAndOwnSuffix), effectiveCallNumberComponents(allOf(
      hasCallNumber("firstHRCallNumber"),
      hasSuffix("ownSuffix"),
      hasPrefix("firstHRPrefix"),
      hasTypeId(HOLDINGS_CALL_NUMBER_TYPE)
    )));

    assertThat(getById(useAllOwnComponentsSharedHoldings), effectiveCallNumberComponents(allOf(
      hasCallNumber("allOwnComponentsCN"),
      hasSuffix("allOwnComponentsCNS"),
      hasPrefix("allOwnComponentsCNP"),
      hasTypeId(ITEM_LEVEL_CALL_NUMBER_TYPE)
    )));

    assertThat(getById(useAllOwnComponents), effectiveCallNumberComponents(allOf(
      hasCallNumber("allOwnComponentsCN2"),
      hasSuffix("allOwnComponentsCNS2"),
      hasPrefix("allOwnComponentsCNP2"),
      hasTypeId(ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND)
    )));
  }

  @Test
  public void shouldCalculatePropertyWhenHoldingsIsNotRetrieved() throws Exception {
    final UUID holdingsId = createInstanceAndHolding(mainLibraryLocationId);
    final JsonObject useAllOwnComponents = nod(holdingsId)
      .put("itemLevelCallNumber", "allOwnComponentsCN")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS")
      .put("itemLevelCallNumberPrefix", "allOwnComponentsCNP")
      .put("itemLevelCallNumberTypeId", ITEM_LEVEL_CALL_NUMBER_TYPE);

    itemsStorageSyncClient.createNoResponse(new JsonObject()
      .put("items", new JsonArray()
        .add(useAllOwnComponents)));

    assertThat(getById(useAllOwnComponents), effectiveCallNumberComponents(allOf(
      hasCallNumber("allOwnComponentsCN"),
      hasSuffix("allOwnComponentsCNS"),
      hasPrefix("allOwnComponentsCNP"),
      hasTypeId(ITEM_LEVEL_CALL_NUMBER_TYPE)
    )));
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

  private JsonObject getById(JsonObject origin) throws InterruptedException,
    MalformedURLException, TimeoutException, ExecutionException {

    return itemsClient.getByIdIfPresent(origin.getString("id")).getJson();
  }
}
