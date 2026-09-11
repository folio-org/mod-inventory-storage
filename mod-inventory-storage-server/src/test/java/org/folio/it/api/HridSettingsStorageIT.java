package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class HridSettingsStorageIT extends BaseIntegrationTest {

  private static final HridSettings INITIAL_HRID_SETTINGS = new HridSettings()
    .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
    .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
    .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));

  private static final HridSettings INITIAL_HRID_SETTINGS_WITHOUT_LEADING_ZEROES = new HridSettings()
    .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
    .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
    .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
    .withCommonRetainLeadingZeroes(false);

  private HridManager hridManager;

  /**
   * Resets the hrid_settings row and the three underlying Postgres sequences before each test:
   * since {@code hrid_settings} is a migration-seeded table (excluded from
   * {@link BaseIntegrationTest}'s per-class table truncation), nothing else resets it between tests.
   */
  @BeforeEach
  void resetHridSettings(Vertx vertx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    hridManager = new HridManager(postgresClient);

    await(hridManager.updateHridSettings(INITIAL_HRID_SETTINGS));
    await(postgresClient.selectSingle("select setval('hrid_instances_seq',1,FALSE)"));
    await(postgresClient.selectSingle("select setval('hrid_holdings_seq',1,FALSE)"));
    await(postgresClient.selectSingle("select setval('hrid_items_seq',1,FALSE)"));
  }

  @Test
  @DisplayName("should retrieve the hrid settings")
  void shouldRetrieveHridSettings() {
    var actual = getHridSettings();

    assertThat(actual.getInstances()).isNotNull();
    assertThat(actual.getInstances().getPrefix()).isEqualTo("in");
    assertThat(actual.getInstances().getStartNumber()).isEqualTo(1L);

    assertThat(actual.getHoldings()).isNotNull();
    assertThat(actual.getHoldings().getPrefix()).isEqualTo("ho");
    assertThat(actual.getHoldings().getStartNumber()).isEqualTo(1L);

    assertThat(actual.getItems()).isNotNull();
    assertThat(actual.getItems().getPrefix()).isEqualTo("it");
    assertThat(actual.getItems().getStartNumber()).isEqualTo(1L);

    assertThat(actual.getCommonRetainLeadingZeroes()).isTrue();
  }

  @Test
  @DisplayName("should return 500 when retrieving hrid settings with an unrecognized tenant")
  void shouldReturn500_whenRetrievingHridSettingsWithBadTenant() {
    var response = await(doGet(client, ResourcePaths.HRID_SETTINGS, "BAD"));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("should update the hrid settings")
  void shouldUpdateHridSettings() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    var putResponse = updateHridSettings(newHridSettings);
    assertThat(putResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertHridSettingsMatch(getHridSettings(), newHridSettings);
  }

  @Test
  @DisplayName("should return 500 when updating hrid settings with an unrecognized tenant")
  void shouldReturn500_whenUpdatingHridSettingsWithBadTenant() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    var response =
      await(doPut(client, ResourcePaths.HRID_SETTINGS, "BAD", pojo2JsonObject(newHridSettings)));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("should not change the id when updating hrid settings")
  void shouldNotChangeId_whenUpdatingHridSettings() {
    var originalHridSettings = getHridSettings();
    var uuid = UUID.randomUUID().toString();

    var newHridSettings = new HridSettings()
      .withId(uuid)
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    var putResponse = updateHridSettings(newHridSettings);
    assertThat(putResponse.status()).isEqualTo(SC_NO_CONTENT);

    var actualHridSettings = getHridSettings();
    assertThat(actualHridSettings.getId()).isNotEqualTo(uuid).isEqualTo(originalHridSettings.getId());
    assertHridSettingsMatch(actualHridSettings, newHridSettings);
  }

  @Test
  @DisplayName("should get the next instance hrid")
  void shouldGetNextInstanceHrid() {
    assertThat(await(nextInstanceHrid())).isEqualTo("in00000000001");
  }

  @Test
  @DisplayName("should get the next instance hrid without leading zeroes when disabled")
  void shouldGetNextInstanceHrid_whenLeadingZeroesDisabled() {
    await(hridManager.updateHridSettings(INITIAL_HRID_SETTINGS_WITHOUT_LEADING_ZEROES));

    assertThat(await(nextInstanceHrid())).isEqualTo("in1");
  }

  @Test
  @DisplayName("should get the next instance hrid after setting the start number")
  void shouldGetNextInstanceHrid_afterSettingStartNumber() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(250L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextInstanceHrid())).isEqualTo("in00000000250");
  }

  @Test
  @DisplayName("should get the next instance hrid without leading zeroes after setting the start number")
  void shouldGetNextInstanceHrid_afterSettingStartNumberWithoutLeadingZeroes() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(250L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
      .withCommonRetainLeadingZeroes(false);
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextInstanceHrid())).isEqualTo("in250");
  }

  @Test
  @DisplayName("should get the next holding hrid")
  void shouldGetNextHoldingHrid() {
    assertThat(await(hridManager.populateHrid(new HoldingsRecord())).getHrid()).isEqualTo("ho00000000001");
  }

  @Test
  @DisplayName("should get the next holding hrid without leading zeroes when disabled")
  void shouldGetNextHoldingHrid_whenLeadingZeroesDisabled() {
    await(hridManager.updateHridSettings(INITIAL_HRID_SETTINGS_WITHOUT_LEADING_ZEROES));

    assertThat(await(nextHoldingsHrid())).isEqualTo("ho1");
  }

  @Test
  @DisplayName("should get the next holding hrid after setting the start number")
  void shouldGetNextHoldingHrid_afterSettingStartNumber() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(7890L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextHoldingsHrid())).isEqualTo("ho00000007890");
  }

  @Test
  @DisplayName("should get the next holding hrid without leading zeroes after setting the start number")
  void shouldGetNextHoldingHrid_afterSettingStartNumberWithoutLeadingZeroes() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(7890L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
      .withCommonRetainLeadingZeroes(false);
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextHoldingsHrid())).isEqualTo("ho7890");
  }

  @Test
  @DisplayName("should get the next item hrid")
  void shouldGetNextItemHrid() {
    assertThat(await(nextItemHrid())).isEqualTo("it00000000001");
  }

  @Test
  @DisplayName("should get the next item hrid after setting the start number")
  void shouldGetNextItemHrid_afterSettingStartNumber() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(87654321L));
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextItemHrid())).isEqualTo("it00087654321");
  }

  @Test
  @DisplayName("should get the next item hrid multiple times in sequence")
  void shouldGetNextItemHrid_multipleTimesInSequence() {
    assertThat(await(nextItemHrid())).isEqualTo("it00000000001");
    assertThat(await(nextItemHrid())).isEqualTo("it00000000002");
    assertThat(await(nextItemHrid())).isEqualTo("it00000000003");
    assertThat(await(nextItemHrid())).isEqualTo("it00000000004");
    assertThat(await(nextItemHrid())).isEqualTo("it00000000005");
  }

  @Test
  @DisplayName("should get the next item hrid with no prefix when none is configured")
  void shouldGetNextItemHrid_whenNoPrefixConfigured() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(100L))
      .withHoldings(new HridSetting().withStartNumber(200L))
      .withItems(new HridSetting().withStartNumber(300L));
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextItemHrid())).isEqualTo("00000000300");
  }

  @Test
  @DisplayName("should roll back the hrid settings when the update transaction fails")
  void shouldRollBackHridSettings_whenUpdateTransactionFails() {
    var originalHridSettings = await(hridManager.getHridSettings());
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(111L))
      .withHoldings(new HridSetting().withStartNumber(200L))
      .withItems(new HridSetting().withStartNumber(999_999_999_999L));

    var future = hridManager.updateHridSettings(newHridSettings);
    assertThatThrownBy(() -> await(future)).isInstanceOf(IllegalStateException.class);

    var currentHridSettings = await(hridManager.getHridSettings());
    assertThat(currentHridSettings.getId()).isEqualTo(originalHridSettings.getId());
    assertThat(currentHridSettings.getInstances().getPrefix())
      .isEqualTo(originalHridSettings.getInstances().getPrefix());
    assertThat(currentHridSettings.getInstances().getStartNumber())
      .isEqualTo(originalHridSettings.getInstances().getStartNumber());
    assertThat(currentHridSettings.getHoldings().getPrefix())
      .isEqualTo(originalHridSettings.getHoldings().getPrefix());
    assertThat(currentHridSettings.getHoldings().getStartNumber())
      .isEqualTo(originalHridSettings.getHoldings().getStartNumber());
    assertThat(currentHridSettings.getItems().getPrefix())
      .isEqualTo(originalHridSettings.getItems().getPrefix());
    assertThat(currentHridSettings.getItems().getStartNumber())
      .isEqualTo(originalHridSettings.getItems().getStartNumber());
  }

  @Test
  @DisplayName("should get the next hrids when the start number is a long")
  void shouldGetNextHrids_whenStartNumberIsLong() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(9_999_999_997L))
      .withHoldings(new HridSetting().withStartNumber(9_999_999_998L))
      .withItems(new HridSetting().withStartNumber(9_999_999_999L));
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextInstanceHrid())).isEqualTo("09999999997");
    assertThat(await(nextHoldingsHrid())).isEqualTo("09999999998");
    assertThat(await(nextItemHrid())).isEqualTo("09999999999");
  }

  @Test
  @DisplayName("should get the next hrids without leading zeroes when the start number is a long")
  void shouldGetNextHrids_whenStartNumberIsLongWithoutLeadingZeroes() {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(9_999_999_997L))
      .withHoldings(new HridSetting().withStartNumber(9_999_999_998L))
      .withItems(new HridSetting().withStartNumber(9_999_999_999L))
      .withCommonRetainLeadingZeroes(false);
    await(hridManager.updateHridSettings(newHridSettings));

    assertThat(await(nextInstanceHrid())).isEqualTo("9999999997");
    assertThat(await(nextHoldingsHrid())).isEqualTo("9999999998");
    assertThat(await(nextItemHrid())).isEqualTo("9999999999");
  }

  @MethodSource("invalidHridSettings")
  @ParameterizedTest(name = "{index}: should reject {6}.{7} = {8}")
  @DisplayName("should return 422 when an hrid settings field is invalid")
  void shouldReturn422_whenHridSettingsFieldIsInvalid(String instancePrefix, long instanceStartNumber,
                                                      String holdingPrefix, long holdingStartNumber,
                                                      String itemPrefix, long itemStartNumber, String keyPart,
                                                      String testField, String expectedValue) {
    var newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix(instancePrefix).withStartNumber(instanceStartNumber))
      .withHoldings(new HridSetting().withPrefix(holdingPrefix).withStartNumber(holdingStartNumber))
      .withItems(new HridSetting().withPrefix(itemPrefix).withStartNumber(itemStartNumber));

    var response = await(doPut(client, ResourcePaths.HRID_SETTINGS, pojo2JsonObject(newHridSettings)));

    assertValidationError(response, keyPart + '.' + testField, expectedValue);
  }

  private static Collection<Object[]> invalidHridSettings() {
    return Arrays.asList(new Object[][] {
      {"in", 999_999_999_999L, "ho", 1, "it", 1, "instances", "startNumber", "999999999999"},
      {"in", 1, "ho", 999_999_999_999L, "it", 1, "holdings", "startNumber", "999999999999"},
      {"in", 1, "ho", 1, "it", 999_999_999_999L, "items", "startNumber", "999999999999"},
      {"in", 0, "ho", 1, "it", 1, "instances", "startNumber", "0"},
      {"in", 1, "ho", 0, "it", 1, "holdings", "startNumber", "0"},
      {"in", 1, "ho", 1, "it", 0, "items", "startNumber", "0"},
      {"invalidprefix", 1, "ho", 1, "it", 1, "instances", "prefix", "invalidprefix"},
      {"in", 1, "invalidprefix", 1, "it", 1, "holdings", "prefix", "invalidprefix"},
      {"in", 1, "ho", 1, "invalidprefix", 1, "items", "prefix", "invalidprefix"},
      {"_invalid", 1, "ho", 1, "it", 1, "instances", "prefix", "_invalid"},
      {"in", 1, "_invalid", 1, "it", 1, "holdings", "prefix", "_invalid"},
      {"in", 1, "ho", 1, "_invalid", 1, "items", "prefix", "_invalid"}
    });
  }

  private Future<String> nextInstanceHrid() {
    return hridManager.populateHrid(new Instance()).map(Instance::getHrid);
  }

  private Future<String> nextHoldingsHrid() {
    return hridManager.populateHrid(new HoldingsRecord()).map(HoldingsRecord::getHrid);
  }

  private Future<String> nextItemHrid() {
    return hridManager.populateHrid(new Item()).map(Item::getHrid);
  }

  private TestResponse updateHridSettings(HridSettings hridSettings) {
    return await(doPut(client, ResourcePaths.HRID_SETTINGS, pojo2JsonObject(hridSettings)));
  }

  private HridSettings getHridSettings() {
    var response = await(doGet(client, ResourcePaths.HRID_SETTINGS));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody().mapTo(HridSettings.class);
  }

  private void assertHridSettingsMatch(HridSettings actual, HridSettings expected) {
    assertThat(actual.getInstances()).isNotNull();
    assertThat(actual.getInstances().getPrefix()).isEqualTo(expected.getInstances().getPrefix());
    assertThat(actual.getInstances().getStartNumber()).isEqualTo(expected.getInstances().getStartNumber());

    assertThat(actual.getHoldings()).isNotNull();
    assertThat(actual.getHoldings().getPrefix()).isEqualTo(expected.getHoldings().getPrefix());
    assertThat(actual.getHoldings().getStartNumber()).isEqualTo(expected.getHoldings().getStartNumber());

    assertThat(actual.getItems()).isNotNull();
    assertThat(actual.getItems().getPrefix()).isEqualTo(expected.getItems().getPrefix());
    assertThat(actual.getItems().getStartNumber()).isEqualTo(expected.getItems().getStartNumber());
  }

  private void assertValidationError(TestResponse response, String expectedKey, String expectedValue) {
    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);

    var errors = response.jsonBody().mapTo(Errors.class);
    assertThat(errors.getErrors()).isNotEmpty();
    var error = errors.getErrors().getFirst();
    assertThat(error.getMessage()).isNotNull();
    assertThat(error.getParameters()).isNotEmpty();
    var parameter = error.getParameters().getFirst();
    assertThat(parameter.getKey()).isEqualTo(expectedKey);
    assertThat(parameter.getValue()).isEqualTo(expectedValue);
  }
}
