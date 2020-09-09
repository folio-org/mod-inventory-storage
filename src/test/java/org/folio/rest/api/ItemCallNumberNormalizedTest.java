package org.folio.rest.api;

import static org.folio.rest.api.InstanceStorageTest.smallAngryPlanet;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.BeforeClass;

import io.vertx.core.json.JsonObject;

public class ItemCallNumberNormalizedTest extends CallNumberNormalizedTestBase {

  @BeforeClass
  public static void createRecords() throws Exception {
    final IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));
    final IndividualResource holdings = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId));

    for (String[] callNumberComponents : callNumberData()) {
      itemsClient.create(new ItemRequestBuilder()
        .forHolding(holdings.getId())
        .withPermanentLoanType(canCirculateLoanTypeId)
        .withMaterialType(bookMaterialTypeId)
        .withItemLevelCallNumberPrefix(callNumberComponents[0])
        .withItemLevelCallNumber(callNumberComponents[1])
        .withItemLevelCallNumberSuffix(callNumberComponents[2]));
    }
  }

  @Override
  protected List<String> searchByCallNumberNormalized(String callNumber) throws Exception {
    final List<IndividualResource> items = itemsClient.getMany(
      "fullCallNumberNormalized=\"%1$s\" OR callNumberAndSuffixNormalized=\"%1$s\"", callNumber);

    return items.stream()
      .map(IndividualResource::getJson)
      .map(json -> json.getJsonObject("effectiveCallNumberComponents", new JsonObject()))
      .map(json -> Stream.of(json.getString("prefix"), json.getString("callNumber"),
        json.getString("suffix"))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" ")))
      .collect(Collectors.toList());
  }
}
