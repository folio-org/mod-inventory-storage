package org.folio.services.inventoryview;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import java.util.Optional;
import org.folio.persist.InventoryViewRepository;
import org.folio.rest.jaxrs.model.InstanceView;
import org.folio.rest.persist.PostgresClientStreamResult;
import org.springframework.stereotype.Service;

@Service
public class InventoryViewService {
  private final InventoryViewRepository repository;

  public InventoryViewService(InventoryViewRepository repository) {
    this.repository = repository;
  }

  /**
   * Gets the HRID the query asks for, if the query is only a search by HRID.
   */
  public Optional<String> extractHrid(String query) {
    return InventoryViewHridQuery.extractHrid(query);
  }

  public Future<String> getInstancesByHrid(String hrid, boolean withBoundedItems, int offset, int limit,
                                           Map<String, String> headers) {
    return repository.getInventoryViewInstancesByHrid(hrid, withBoundedItems, offset, limit, tenantId(headers));
  }

  public Future<String> getInstancesWithBoundedItems(int offset, int limit, String query,
                                                     Map<String, String> headers) {
    return repository.getInventoryViewInstancesWithBoundedItems(offset, limit, query, tenantId(headers));
  }

  /**
   * Streams the instances matching the CQL.
   */
  public Future<PostgresClientStreamResult<InstanceView>> streamInstances(String query, int offset, int limit,
                                                                          Map<String, String> headers) {
    return repository.stream(query, offset, limit, tenantId(headers));
  }
}
