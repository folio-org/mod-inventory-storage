package org.folio.services.consortium;

import io.vertx.core.Future;
import java.util.Map;
import java.util.Optional;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.consortium.entities.SharingInstance;

public interface ConsortiumService {
  /**
   * Runs job at mod-consortia that creates shadow instance for local tenant.
   *
   * @param instanceId                 - id of shadow instance to create.
   * @param consortiumData             - consortium data that consists of centralTenantId and consortiumId.
   * @param headers                    - the headers for creating shadow instance.
   *
   * @return - future of sharingInstance
   */
  Future<SharingInstance> createShadowInstance(String instanceId, ConsortiumData consortiumData,
                                               Map<String, String> headers);

  /**
   * Retrieves centralTenantId and consortiumId.
   *
   * @param headers                    - the headers for getting consortium data.
   *
   * @return - future of ConsortiumData
   */
  Future<Optional<ConsortiumData>> getConsortiumData(Map<String, String> headers);

  /**
   * Starts sharing instance process.
   *
   * @param consortiumId               - Consortium id for running sharing process.
   * @param sharingInstance            - Sharing Instance entity that configures sourceTenantId,
   *                                     targetTenantId and instanceIdentifier.
   * @param headers                    - the headers for sharing instance.
   *
   * @return - future of sharingInstance
   */
  Future<SharingInstance> shareInstance(String consortiumId, SharingInstance sharingInstance,
                                        Map<String, String> headers);
}
