package org.folio.rest.api;

import static com.github.tomakehurst.wiremock.http.Response.Builder.like;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import io.vertx.core.json.JsonArray;
import lombok.SneakyThrows;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.consortium.entities.SharingStatus;

/**
 * WireMock response transformer for {@code POST /consortia/{id}/sharing/instances}: simulates a
 * consortium "share this instance" request completing by creating a matching instance at the
 * target tenant, so holdings/items created there against the shared instance id satisfy the
 * FK. Registered globally on {@link TestBaseWithInventoryUtil#mockServer} since several legacy
 * test classes exercise consortium shadow-instance creation, not just one.
 */
public class ConsortiumInstanceSharingTransformer extends ResponseTransformer {

  public static final String NAME = "consortium-instance-sharing-transformer";

  @SneakyThrows
  @Override
  public Response transform(Request request, Response response, FileSource fileSource, Parameters parameters) {
    var sharingInstance = Json.getObjectMapper().readValue(request.getBody(), SharingInstance.class);
    sharingInstance.setStatus(SharingStatus.COMPLETE);

    var instanceToShare = TestBaseWithInventoryUtil.createInstanceRequest(
      sharingInstance.getInstanceIdentifier(), "TEST", "a shared instance", new JsonArray(), new JsonArray(),
      TestBaseWithInventoryUtil.UUID_INSTANCE_TYPE, new JsonArray());
    TestBase.instancesClient.create(instanceToShare, sharingInstance.getTargetTenantId());

    return like(response).body(Json.getObjectMapper().writeValueAsString(sharingInstance)).build();
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }
}
