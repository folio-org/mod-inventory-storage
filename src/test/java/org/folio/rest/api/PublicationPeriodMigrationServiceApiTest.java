package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.tenantOp;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Publication;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.support.IndividualResource;
import org.junit.Test;

public class PublicationPeriodMigrationServiceApiTest extends MigrationTestBase {
  private static final String PUBLICATION_PERIOD = "publicationPeriod";

  @Test
  public void shouldPopulateShelvingOrder() throws Exception {
    var instances = create101Instance();
    removePublicationPeriod(instances);

    tenantOp(TENANT_ID, getTenantAttributes());

    for (IndividualResource instance : instances) {
      var updatedInstance = instancesClient.getById(instance.getId());
      assertThat(updatedInstance.getJson()
        .getJsonObject(PUBLICATION_PERIOD), notNullValue());
    }
  }

  private JsonObject getTenantAttributes() {
    return JsonObject.mapFrom(new TenantAttributes()
      .withModuleFrom("20.2.1")
      .withModuleTo("20.3.1")
      .withParameters(List.of(
        new Parameter().withKey("loadSample").withValue("false"),
        new Parameter().withKey("loadReference").withValue("false"))));
  }

  private void removePublicationPeriod(List<IndividualResource> instances) throws Exception {
    for (IndividualResource instance : instances) {
      unsetJsonbProperty("instance", instance.getId(), PUBLICATION_PERIOD);

      assertThat(instancesClient.getById(instance.getId()).getJson()
        .getJsonObject(PUBLICATION_PERIOD), nullValue());
    }
  }

  private IndividualResource createInstance(int index) {
    var dateOfPublication = String.format("20%02d", index);
    var instance = new Instance().withId(UUID.randomUUID().toString())
      .withTitle("Resource " + index)
      .withSource("FOLIO")
      .withInstanceTypeId(UUID_INSTANCE_TYPE.toString())
      .withPublication(List.of(new Publication().withDateOfPublication(dateOfPublication)));

    return instancesClient.create(JsonObject.mapFrom(instance));
  }

  private List<IndividualResource> create101Instance() {
    return IntStream.range(0, 101)
      .mapToObj(this::createInstance)
      .collect(Collectors.toList());
  }
}
