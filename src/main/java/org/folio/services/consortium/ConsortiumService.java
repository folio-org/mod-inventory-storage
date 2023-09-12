package org.folio.services.consortium;

import io.vertx.core.Future;
import java.util.Optional;
import org.folio.rest.support.Context;
import org.folio.services.consortium.entities.ConsortiumConfiguration;
import org.folio.services.consortium.entities.SharingInstance;

public interface ConsortiumService {
  /**
   * Runs job at mod-consortia that creates shadow instance for local tenant.
   *
   * @param context                    - Context for creating shadow instance.
   * @param instanceId                 - id of shadow instance to create.
   * @param consortiumConfiguration    - consortium configuration that consists of centralTenantId and consortiumId
   * @return - future of sharingInstance
   */
  Future<SharingInstance> createShadowInstance(Context context, String instanceId,
                                               ConsortiumConfiguration consortiumConfiguration);

  /**
   * Retrieves centralTenantId and consortiumId.
   *
   * @param context                    - Context for retrieving centralTenantId.
   * @return - future of consortiumConfiguration
   */
  Future<Optional<ConsortiumConfiguration>> getConsortiumConfiguration(Context context);

  /**
   * Starts sharing instance process.
   *
   * @param context                    - Context for running sharing process.
   * @param consortiumId               - Consortium id for running sharing process.
   * @param sharingInstance            - Sharing Instance entity that configures sourceTenantId,
   *                                     targetTenantId and instanceIdentifier.
   * @return - future of sharingInstance
   */
  Future<SharingInstance> shareInstance(Context context, String consortiumId, SharingInstance sharingInstance);
}
