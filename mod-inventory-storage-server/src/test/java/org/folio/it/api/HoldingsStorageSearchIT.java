package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.InstanceStorageFixtures.createInstance;

import io.vertx.core.json.JsonArray;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsStorageSearchIT extends HoldingsStorageTestBase {

  @Test
  @DisplayName("should search by the discoverySuppress property")
  void shouldSearchByDiscoverySuppressProperty() {
    var instanceId = createInstanceRecord();
    var suppressed = createHolding(holdingRequest(instanceId).withDiscoverySuppress(true));
    var notSuppressed = createHolding(holdingRequest(instanceId).withDiscoverySuppress(false));
    var notSuppressedDefault = createHolding(holdingRequest(instanceId));

    var suppressedResults = searchForHoldings("discoverySuppress==true");
    var notSuppressedResults = searchForHoldings("cql.allRecords=1 not discoverySuppress==true");

    assertThat(idsOf(suppressedResults)).containsExactly(suppressed.getString("id"));
    assertThat(idsOf(notSuppressedResults))
      .containsExactlyInAnyOrder(notSuppressed.getString("id"), notSuppressedDefault.getString("id"));
  }

  @Test
  @DisplayName("should filter by the full call number")
  void shouldFilterByFullCallNumber() {
    var instanceId = createInstanceRecord();
    var wholeCallNumber = createHolding(holdingRequest(instanceId)
      .withCallNumberPrefix("prefix").withCallNumber("callNumber").withCallNumberSuffix("suffix"));
    createHolding(holdingRequest(instanceId).withCallNumberPrefix("prefix").withCallNumber("callNumber"));
    createHolding(holdingRequest(instanceId)
      .withCallNumberPrefix("prefix").withCallNumber("differentCallNumber").withCallNumberSuffix("suffix"));

    var found = searchForHoldings("fullCallNumber==\"prefix callNumber suffix\"");

    assertThat(idsOf(found)).containsExactly(wholeCallNumber.getString("id"));
  }

  @Test
  @DisplayName("should filter by the call number and suffix")
  void shouldFilterByCallNumberAndSuffix() {
    var instanceId = createInstanceRecord();
    var wholeCallNumber = createHolding(holdingRequest(instanceId)
      .withCallNumberPrefix("prefix").withCallNumber("callNumber").withCallNumberSuffix("suffix"));
    createHolding(holdingRequest(instanceId).withCallNumberPrefix("prefix").withCallNumber("callNumber"));
    var noPrefix = createHolding(holdingRequest(instanceId)
      .withCallNumber("callNumber").withCallNumberSuffix("suffix"));

    var found = searchForHoldings("callNumberAndSuffix==\"callNumber suffix\"");

    assertThat(idsOf(found)).containsExactlyInAnyOrder(wholeCallNumber.getString("id"), noPrefix.getString("id"));
  }

  @Test
  @DisplayName("should find a holding by call number when there is a suffix")
  void shouldFindHolding_byCallNumberWithSuffix() {
    var instanceId = createInstanceRecord();
    var first = createHolding(holdingRequest(instanceId).withCallNumber("GE77 .F73 2014"));
    var second = createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 2014").withCallNumberSuffix("Curriculum Materials Collection"));
    createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 ").withCallNumberSuffix("2014 Curriculum Materials Collection"));

    var found = searchByCallNumberEyeReadable("GE77 .F73 2014");

    assertThat(found).containsExactlyInAnyOrder(first.getString("id"), second.getString("id"));
  }

  @Test
  @DisplayName("should apply explicit right truncation")
  void shouldApplyExplicitRightTruncation() {
    var instanceId = createInstanceRecord();
    var first = createHolding(holdingRequest(instanceId).withCallNumber("GE77 .F73 2014"));
    var second = createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 2014").withCallNumberSuffix("Curriculum Materials Collection"));
    var third = createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 ").withCallNumberSuffix("2014 Curriculum Materials Collection"));
    createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F74 ").withCallNumberSuffix("2014 Curriculum Materials Collection"));

    var found = searchByCallNumberEyeReadable("GE77 .F73*");

    assertThat(found).containsExactlyInAnyOrder(
      first.getString("id"), second.getString("id"), third.getString("id"));
  }

  @Test
  @DisplayName("should filter by an instance property")
  void shouldFilterByInstanceProperty() {
    var planetInstanceId = createInstance(client, "the long way to a small angry planet", instanceTypeId);
    var uprootedInstanceId = createInstance(client, "uprooted", instanceTypeId);
    var planetHolding = createHolding(holdingRequest(planetInstanceId));
    var uprootedHolding = createHolding(holdingRequest(uprootedInstanceId));

    var foundPlanet = searchForHoldings("instance.title = planet");
    assertThat(idsOf(foundPlanet)).containsExactly(planetHolding.getString("id"));

    var foundUprooted = searchForHoldings("instance.title = uprooted");
    assertThat(idsOf(foundUprooted)).containsExactly(uprootedHolding.getString("id"));
  }

  @Test
  @DisplayName("should return 422 when an additional call number is missing its call number")
  void shouldReturn422_whenAdditionalCallNumberMissingCallNumber() {
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(new EffectiveCallNumberComponents())));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should create a holding with minimal additional call numbers")
  void shouldCreateHolding_withMinimalAdditionalCallNumbers() {
    var additionalCallNumber = new EffectiveCallNumberComponents().withCallNumber("123456789");
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(additionalCallNumber)));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should create a holding with additional call numbers")
  void shouldCreateHolding_withAdditionalCallNumbers() {
    var additionalCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("123456789").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(additionalCallNumber)));

    var holding = createHolding(request);

    var stored = holding.getJsonArray("additionalCallNumbers").getJsonObject(0);
    assertThat(stored.getString("callNumber")).isEqualTo("123456789");
    assertThat(stored.getString("prefix")).isEqualTo("A");
    assertThat(stored.getString("suffix")).isEqualTo("Z");
    assertThat(stored.getString("typeId")).isEqualTo(lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should create a holding with empty additional call numbers")
  void shouldCreateHolding_withEmptyAdditionalCallNumbers() {
    var request = holdingRequest(createInstanceRecord()).create().put("additionalCallNumbers", new JsonArray());

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should delete an additional call number from a holding")
  void shouldDeleteAdditionalCallNumberFromHolding() {
    var additionalCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("123456789").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(additionalCallNumber)));
    var holding = createHolding(request);

    holding.remove("additionalCallNumbers");
    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should update a holding's additional call numbers")
  void shouldUpdateHoldingsAdditionalCallNumbers() {
    var firstCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("123456789").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(firstCallNumber)));
    var holding = createHolding(request);

    var secondCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("secondCallNumber").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    holding.getJsonArray("additionalCallNumbers").add(pojo2JsonObject(secondCallNumber));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getJsonArray("additionalCallNumbers")).hasSize(2);
    assertThat(updated.getJsonArray("additionalCallNumbers").getJsonObject(1).getString("callNumber"))
      .isEqualTo("secondCallNumber");
  }
}
