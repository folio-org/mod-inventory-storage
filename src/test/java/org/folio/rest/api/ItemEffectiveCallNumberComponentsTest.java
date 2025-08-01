package org.folio.rest.api;

import static org.folio.rest.api.ItemStorageTest.nodWithNoBarcode;
import static org.folio.rest.support.matchers.ItemMatchers.effectiveCallNumberComponents;
import static org.folio.rest.support.matchers.ItemMatchers.hasCallNumber;
import static org.folio.rest.support.matchers.ItemMatchers.hasPrefix;
import static org.folio.rest.support.matchers.ItemMatchers.hasSuffix;
import static org.folio.rest.support.matchers.ItemMatchers.hasTypeId;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.apache.commons.lang3.StringUtils;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.api.testdata.ItemEffectiveCallNumberComponentsTestData;
import org.folio.rest.api.testdata.ItemEffectiveCallNumberComponentsTestData.CallNumberComponentPropertyNames;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.messages.ItemEventMessageChecks;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveCallNumberComponentsTest extends TestBaseWithInventoryUtil {
  public static final String DEWEY_CALL_NUMBER_TYPE = "03dd64d0-5626-4ecd-8ece-4531e0069f35";
  public static final String NLM_CALL_NUMBER_TYPE = "054d460d-d6b9-4469-9e37-7a78a2266655";
  public static final String LC_CALL_NUMBER_TYPE = "95467209-6d7b-468b-94df-0f5d7ad2747d";
  public static final String MOYS_CALL_NUMBER_TYPE = "828ae637-dfa3-4265-a1af-5279c436edff";

  private final ItemEventMessageChecks itemMessageChecks
    = new ItemEventMessageChecks(KAFKA_CONSUMER, mockServer.baseUrl());

  @BeforeClass
  public static void createCallNumberTypes() {
    TestBase.beforeAll();
  }

  @Test
  @Parameters(
    source = ItemEffectiveCallNumberComponentsTestData.class,
    method = "createPropertiesParams"
  )
  @TestCaseName("[{index}]: {params}")
  public void canCalculateEffectiveCallNumberPropertyOnCreate(
    CallNumberComponentPropertyNames callNumberProperties,
    String holdingsPropertyValue, String itemPropertyValue) {

    final String effectiveValue = StringUtils.firstNonBlank(itemPropertyValue, holdingsPropertyValue);
    IndividualResource holdings = createHoldingsWithPropertySetAndInstance(
      callNumberProperties.holdingsPropertyName, holdingsPropertyValue
    );

    IndividualResource createdItem = itemsClient.create(
      nodWithNoBarcode(holdings.getId())
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
  public void canCalculateEffectiveCallNumberPropertyOnBatchCreate() {
    final UUID firstHoldingsId = createInstanceAndHoldingWithBuilder(MAIN_LIBRARY_LOCATION_ID,
      builder -> builder.withCallNumber("firstHRCallNumber")
        .withCallNumberPrefix("firstHRPrefix")
        .withCallNumberSuffix("firstHRSuffix")
        .withCallNumberTypeId(DEWEY_CALL_NUMBER_TYPE));

    final UUID secondHoldingsId = createInstanceAndHoldingWithBuilder(MAIN_LIBRARY_LOCATION_ID,
      builder -> builder.withCallNumber("secondHRCallNumber")
        .withCallNumberPrefix("secondHRPrefix")
        .withCallNumberSuffix("secondHRSuffix")
        .withCallNumberTypeId(NLM_CALL_NUMBER_TYPE));

    final UUID thirdHoldingsId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    final JsonObject useFirstHoldingsComponents = nodWithNoBarcode(firstHoldingsId);
    final JsonObject useOwnCallNumber = nodWithNoBarcode(secondHoldingsId)
      .put("itemLevelCallNumber", "ownCallNumber");
    final JsonObject useFirstHoldingsAndOwnSuffix = nodWithNoBarcode(firstHoldingsId)
      .put("itemLevelCallNumberSuffix", "ownSuffix");
    final JsonObject useAllOwnComponentsSharedHoldings = nodWithNoBarcode(firstHoldingsId)
      .put("itemLevelCallNumber", "allOwnComponentsCN")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS")
      .put("itemLevelCallNumberPrefix", "allOwnComponentsCNP")
      .put("itemLevelCallNumberTypeId", LC_CALL_NUMBER_TYPE);
    final JsonObject useAllOwnComponents = nodWithNoBarcode(thirdHoldingsId)
      .put("itemLevelCallNumber", "allOwnComponentsCN2")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS2")
      .put("itemLevelCallNumberPrefix", "allOwnComponentsCNP2")
      .put("itemLevelCallNumberTypeId", MOYS_CALL_NUMBER_TYPE);

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
      hasTypeId(DEWEY_CALL_NUMBER_TYPE)
    )));

    assertThat(getById(useOwnCallNumber), effectiveCallNumberComponents(allOf(
      hasCallNumber("ownCallNumber"),
      hasSuffix("secondHRSuffix"),
      hasPrefix("secondHRPrefix"),
      hasTypeId(NLM_CALL_NUMBER_TYPE)
    )));

    assertThat(getById(useFirstHoldingsAndOwnSuffix), effectiveCallNumberComponents(allOf(
      hasCallNumber("firstHRCallNumber"),
      hasSuffix("ownSuffix"),
      hasPrefix("firstHRPrefix"),
      hasTypeId(DEWEY_CALL_NUMBER_TYPE)
    )));

    assertThat(getById(useAllOwnComponentsSharedHoldings), effectiveCallNumberComponents(allOf(
      hasCallNumber("allOwnComponentsCN"),
      hasSuffix("allOwnComponentsCNS"),
      hasPrefix("allOwnComponentsCNP"),
      hasTypeId(LC_CALL_NUMBER_TYPE)
    )));

    assertThat(getById(useAllOwnComponents), effectiveCallNumberComponents(allOf(
      hasCallNumber("allOwnComponentsCN2"),
      hasSuffix("allOwnComponentsCNS2"),
      hasPrefix("allOwnComponentsCNP2"),
      hasTypeId(MOYS_CALL_NUMBER_TYPE)
    )));
  }

  @Test
  public void shouldCalculatePropertyWhenHoldingsIsNotRetrieved() {
    final UUID holdingsId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    final JsonObject useAllOwnComponents = nodWithNoBarcode(holdingsId)
      .put("itemLevelCallNumber", "allOwnComponentsCN")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS")
      .put("itemLevelCallNumberPrefix", "allOwnComponentsCNP")
      .put("itemLevelCallNumberTypeId", LC_CALL_NUMBER_TYPE);

    itemsStorageSyncClient.createNoResponse(new JsonObject()
      .put("items", new JsonArray()
        .add(useAllOwnComponents)));

    assertThat(getById(useAllOwnComponents), effectiveCallNumberComponents(allOf(
      hasCallNumber("allOwnComponentsCN"),
      hasSuffix("allOwnComponentsCNS"),
      hasPrefix("allOwnComponentsCNP"),
      hasTypeId(LC_CALL_NUMBER_TYPE)
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
    String itemInitValue, String itemTargetValue) {

    final String holdingsPropertyName = callNumberProperties.holdingsPropertyName;
    final String itemPropertyName = callNumberProperties.itemPropertyName;
    final String effectivePropertyName = callNumberProperties.effectivePropertyName;

    final String initEffectiveValue = StringUtils.firstNonBlank(itemInitValue, holdingsInitValue);
    final String targetEffectiveValue = StringUtils.firstNonBlank(itemTargetValue, holdingsTargetValue);

    IndividualResource holdings = createHoldingsWithPropertySetAndInstance(
      holdingsPropertyName, holdingsInitValue
    );

    IndividualResource createdItem = itemsClient.create(
      nodWithNoBarcode(holdings.getId())
        .put(itemPropertyName, itemInitValue)
    );

    JsonObject effectiveCallNumberComponents = createdItem.getJson()
      .getJsonObject("effectiveCallNumberComponents");

    assertNotNull(effectiveCallNumberComponents);
    assertThat(effectiveCallNumberComponents.getString(effectivePropertyName),
      is(initEffectiveValue));

    updateHoldingRecord(holdings.getId(),
      getHoldingsById(holdings.getJson())
        .put("discoverySuppress", false) //ensure holdings has some updated field so update is not skipped
        .put(holdingsPropertyName, holdingsTargetValue)
    );

    var itemAfterHoldingsUpdate = getById(createdItem.getJson());

    itemMessageChecks.updatedMessagePublished(createdItem.getJson(), itemAfterHoldingsUpdate);

    if (!Objects.equals(itemInitValue, itemTargetValue)) {
      itemsClient.replace(createdItem.getId(), itemAfterHoldingsUpdate.copy()
        .put(itemPropertyName, itemTargetValue), Map.of(XOkapiHeaders.URL, mockServer.baseUrl()));

      itemMessageChecks.updatedMessagePublished(itemAfterHoldingsUpdate,
        itemsClient.getById(createdItem.getId()).getJson());
    }

    final JsonObject updatedItem = itemsClient.getById(createdItem.getId()).getJson();
    final JsonObject updatedEffectiveCallNumberComponents = updatedItem
      .getJsonObject("effectiveCallNumberComponents");

    assertNotNull(updatedEffectiveCallNumberComponents);
    assertThat(updatedEffectiveCallNumberComponents.getString(effectivePropertyName),
      is(targetEffectiveValue));
  }

  private IndividualResource createHoldingsWithPropertySetAndInstance(
    String propertyName, String propertyValue) {

    IndividualResource instance = instancesClient.create(instance(UUID.randomUUID()));

    JsonObject holdingToCreate = new HoldingRequestBuilder()
      .withId(UUID.randomUUID())
      .forInstance(instance.getId())
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .withDiscoverySuppress(true)
      .create()
      .put(propertyName, propertyValue);
    IndividualResource holdings = createHoldingRecord(holdingToCreate);
    assertThat(holdings.getJson().getString(propertyName), is(propertyValue));

    return holdings;
  }

  private JsonObject getById(JsonObject origin) {
    return itemsClient.getByIdIfPresent(origin.getString("id")).getJson();
  }

  private JsonObject getHoldingsById(JsonObject origin) {
    return holdingsClient.getByIdIfPresent(origin.getString("id")).getJson();
  }
}
