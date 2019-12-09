package org.folio.rest.api;


import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.folio.rest.api.ItemStorageTest.nod;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveCallNumberComponentsTest extends TestBaseWithInventoryUtil {
  private static final String HOLDINGS_CALL_NUMBER_TYPE = UUID.randomUUID().toString();
  private static final String HOLDINGS_CALL_NUMBER_TYPE_SECOND = UUID.randomUUID().toString();
  private static final String ITEM_LEVEL_CALL_NUMBER_TYPE = UUID.randomUUID().toString();
  private static final String ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND = UUID.randomUUID().toString();

  @BeforeClass
  public static void createCallNumberTypes() throws Exception {
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
  @Parameters(method = "createPropertiesParams")
  public void canCalculateEffectiveCallNumberPropertyOnCreate(
    Pair<String, String> holdingsProperties,
    Pair<String, String> itemProperties, String effectivePropertyName) throws Exception {

    final String holdingsPropertyName = holdingsProperties.getKey();
    final String holdingsPropertyValue = holdingsProperties.getValue();
    final String itemPropertyName = itemProperties.getKey();
    final String itemPropertyValue = itemProperties.getValue();
    final String effectiveValue = StringUtils.firstNonBlank(itemPropertyValue, holdingsPropertyValue);

    IndividualResource holdings =
      createHoldingsWithPropertySetAndInstance(holdingsPropertyName, holdingsPropertyValue);

    IndividualResource createdItem = itemsClient.create(
      nod(null, holdings.getId())
        .put(itemPropertyName, itemPropertyValue)
    );
    assertThat(createdItem.getJson().getString(itemPropertyName), is(itemPropertyValue));

    Response getResponse = itemsClient.getById(createdItem.getId());
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject effectiveCallNumberComponents = getResponse.getJson().getJsonObject("effectiveCallNumberComponents");
    assertNotNull(effectiveCallNumberComponents);
    assertThat(effectiveCallNumberComponents.getString(effectivePropertyName), is(effectiveValue));
  }

  @Test
  @Parameters(method = "updatePropertiesParams")
  public void canCalculateEffectiveCallNumberPropertyOnUpdate(
    Triple<String, String, String> holdingsProperties,
    Triple<String, String, String> itemProperties, String effectivePropertyName) throws Exception {

    final String holdingsPropertyName = holdingsProperties.getLeft();
    final String holdingsPropertyInitValue = holdingsProperties.getMiddle();
    final String holdingsPropertyTargetValue = holdingsProperties.getRight();

    final String itemPropertyName = itemProperties.getLeft();
    final String itemPropertyInitValue = itemProperties.getMiddle();
    final String itemPropertyTargetValue = itemProperties.getRight();

    final String initEffectiveValue = StringUtils.firstNonBlank(itemPropertyInitValue, holdingsPropertyInitValue);
    final String targetEffectiveValue = StringUtils.firstNonBlank(itemPropertyTargetValue, holdingsPropertyTargetValue);

    IndividualResource holdings =
      createHoldingsWithPropertySetAndInstance(holdingsPropertyName, holdingsPropertyInitValue);

    IndividualResource createdItem = itemsClient.create(
      nod(null, holdings.getId())
        .put(itemPropertyName, itemPropertyInitValue)
    );

    JsonObject effectiveCallNumberComponents = createdItem.getJson()
      .getJsonObject("effectiveCallNumberComponents");
    assertNotNull(effectiveCallNumberComponents);
    assertThat(effectiveCallNumberComponents.getString(effectivePropertyName),
      is(initEffectiveValue));

    holdingsClient.replace(holdings.getId(),
      holdings.copyJson()
        .put(holdingsPropertyName, holdingsPropertyTargetValue)
    );

    itemsClient.replace(createdItem.getId(),
      createdItem.copyJson()
        .put(itemPropertyName, itemPropertyTargetValue)
    );

    JsonObject updatedEffectiveCallNumberComponents = itemsClient
      .getById(createdItem.getId()).getJson()
      .getJsonObject("effectiveCallNumberComponents");

    assertNotNull(updatedEffectiveCallNumberComponents);
    assertThat(updatedEffectiveCallNumberComponents.getString(effectivePropertyName),
      is(targetEffectiveValue));
  }

  @SuppressWarnings("unused")
  private List<Object[]> createPropertiesParams() {
    List<Object[]> testCases = new ArrayList<>();

    testCases.addAll(createPropertyTestCase("callNumber", "callNumber"));
    testCases.addAll(createPropertyTestCase("callNumberSuffix", "suffix"));
    testCases.addAll(createPropertyTestCase("callNumberPrefix", "prefix"));
    testCases.addAll(createPropertyTestCase(
      "callNumberTypeId", HOLDINGS_CALL_NUMBER_TYPE,
      ITEM_LEVEL_CALL_NUMBER_TYPE, "typeId"
    ));

    return testCases;
  }

  private List<Object[]> createPropertyTestCase(
    String holdingsPropertyName, String effectivePropertyName) {

    return createPropertyTestCase(holdingsPropertyName,
      "hr" + capitalize(holdingsPropertyName),
      "it" + capitalize(holdingsPropertyName),
      effectivePropertyName
    );
  }

  private List<Object[]> createPropertyTestCase(
    String holdingsPropertyName, String holdingsPropertyValue,
    String itemPropertyValue, String effectivePropertyName) {

    final String itemPropertyName = "itemLevel" + capitalize(holdingsPropertyName);

    return Arrays.asList(
      new Object[]{
        new ImmutablePair<>(holdingsPropertyName, holdingsPropertyValue),
        new ImmutablePair<>(itemPropertyName, null),
        effectivePropertyName
      },
      new Object[]{
        new ImmutablePair<>(holdingsPropertyName, null),
        new ImmutablePair<>(itemPropertyName, itemPropertyValue),
        effectivePropertyName
      },
      new Object[]{
        new ImmutablePair<>(holdingsPropertyName, holdingsPropertyValue),
        new ImmutablePair<>(itemPropertyName, itemPropertyValue),
        effectivePropertyName
      },
      new Object[]{
        new ImmutablePair<>(holdingsPropertyName, null),
        new ImmutablePair<>(itemPropertyName, null),
        effectivePropertyName
      }
    );
  }

  @SuppressWarnings("unused")
  private List<Object[]> updatePropertiesParams() {
    List<Object[]> testCases = new ArrayList<>();

    testCases.addAll(updatePropertyTestCase("callNumber", "callNumber"));
    testCases.addAll(updatePropertyTestCase("callNumberSuffix", "suffix"));
    testCases.addAll(updatePropertyTestCase("callNumberPrefix", "prefix"));
    testCases.addAll(updatePropertyTestCase(
      "callNumberTypeId", HOLDINGS_CALL_NUMBER_TYPE, HOLDINGS_CALL_NUMBER_TYPE_SECOND,
      ITEM_LEVEL_CALL_NUMBER_TYPE, ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND, "typeId"
    ));

    return testCases;
  }

  private List<Object[]> updatePropertyTestCase(
    String holdingsPropertyName, String holdingsInitValue, String holdingsTargetValue,
    String itemInitValue, String itemTargetValue, String effectivePropertyName) {

    final String itemPropertyName = "itemLevel" + capitalize(holdingsPropertyName);

    return Arrays.asList(
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, holdingsInitValue, holdingsTargetValue),
        new ImmutableTriple<>(itemPropertyName, itemInitValue, itemTargetValue),
        effectivePropertyName
      },
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, holdingsInitValue, null),
        new ImmutableTriple<>(itemPropertyName, itemInitValue, itemTargetValue),
        effectivePropertyName
      },
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, holdingsInitValue, holdingsTargetValue),
        new ImmutableTriple<>(itemPropertyName, itemInitValue, null),
        effectivePropertyName
      },
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, holdingsInitValue, null),
        new ImmutableTriple<>(itemPropertyName, itemInitValue, null),
        effectivePropertyName
      },
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, holdingsInitValue, null),
        new ImmutableTriple<>(itemPropertyName, itemInitValue, itemInitValue),
        effectivePropertyName
      },
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, holdingsInitValue, holdingsInitValue),
        new ImmutableTriple<>(itemPropertyName, itemInitValue, null),
        effectivePropertyName
      },
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, holdingsInitValue, holdingsTargetValue),
        new ImmutableTriple<>(itemPropertyName, null, null),
        effectivePropertyName
      },
      new Object[]{
        new ImmutableTriple<>(holdingsPropertyName, null, holdingsTargetValue),
        new ImmutableTriple<>(itemPropertyName, itemInitValue, null),
        effectivePropertyName
      }
    );
  }

  private List<Object[]> updatePropertyTestCase(
    String holdingsPropertyName, String effectivePropertyName) {

    return updatePropertyTestCase(holdingsPropertyName,
      "hr" + capitalize(holdingsPropertyName),
      "hrUPDATED" + capitalize(holdingsPropertyName),
      "it" + capitalize(holdingsPropertyName),
      "itUPDATED" + capitalize(holdingsPropertyName),
      effectivePropertyName
    );
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
