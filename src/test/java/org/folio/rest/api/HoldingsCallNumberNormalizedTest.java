package org.folio.rest.api;

import static org.folio.rest.api.InstanceStorageTest.smallAngryPlanet;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.BeforeClass;

public class HoldingsCallNumberNormalizedTest extends CallNumberNormalizedTestBase {

  @BeforeClass
  public static void createRecords() throws Exception {
    final IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    for (String[] callNumberComponents : callNumberData()) {
      holdingsClient.create(new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumberPrefix(callNumberComponents[0])
        .withCallNumber(callNumberComponents[1])
        .withCallNumberSuffix(callNumberComponents[2]));
    }
  }

  @Override
  protected List<String> searchByCallNumberNormalized(String callNumber) throws Exception {
    final List<IndividualResource> holdings = holdingsClient.getMany(
      "fullCallNumberNormalized=\"%1$s\" OR callNumberAndSuffixNormalized=\"%1$s\"", callNumber);

    return holdings.stream()
      .map(IndividualResource::getJson)
      .map(json -> Stream.of(json.getString("callNumberPrefix"),
        json.getString("callNumber"), json.getString("callNumberSuffix"))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" ")))
      .collect(Collectors.toList());
  }
}
