package org.folio.rest.support.fixtures;

import static org.folio.rest.support.http.ResourceClient.forStatisticalCodeTypes;
import static org.folio.rest.support.http.ResourceClient.forStatisticalCodes;

import java.util.UUID;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.StatisticalCodeBuilder;
import org.folio.rest.support.http.ResourceClient;

public final class StatisticalCodeFixture {
  private final ResourceClient codeTypesClient;
  private final ResourceClient codesClient;

  public StatisticalCodeFixture(HttpClient httpClient) {
    codeTypesClient = forStatisticalCodeTypes(httpClient);
    codesClient = forStatisticalCodes(httpClient);
  }

  public IndividualResource createSerialManagementCode(StatisticalCodeBuilder builder) {
    return codesClient.create(builder
      .withStatisticalCodeTypeId(getSerialManagementStatisticalCodeType()));
  }

  public Response attemptCreateSerialManagementCode(StatisticalCodeBuilder builder) {
    final var json = builder
      .withStatisticalCodeTypeId(getSerialManagementStatisticalCodeType())
      .create();

    return codesClient.attemptToCreate(json);
  }

  public void removeTestStatisticalCodes() {
    codesClient.getMany("source==\"test\"").stream()
      .map(IndividualResource::getId)
      .forEach(codesClient::delete);
  }

  private UUID getSerialManagementStatisticalCodeType() {
    return codeTypesClient.getMany("name==\"%s\"", "SERM (Serial management)")
      .stream().findFirst()
      .map(IndividualResource::getId)
      .orElse(null);
  }
}
