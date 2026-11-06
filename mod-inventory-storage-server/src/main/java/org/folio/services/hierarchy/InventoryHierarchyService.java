package org.folio.services.hierarchy;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.List;
import java.util.Map;
import org.folio.persist.InventoryHierarchyRepository;
import org.folio.persist.entity.UpdatedInstanceIdsParams;
import org.springframework.stereotype.Service;

@Service
public class InventoryHierarchyService {
  private final InventoryHierarchyRepository repository;

  public InventoryHierarchyService(InventoryHierarchyRepository repository) {
    this.repository = repository;
  }

  public Future<Void> streamUpdatedInstanceIds(UpdatedInstanceIdsParams params,
                                               Handler<RowStream<Row>> rowStreamHandler,
                                               Map<String, String> headers) {
    return repository.streamUpdatedInstanceIds(params, rowStreamHandler, tenantId(headers));
  }

  public Future<Void> streamItemsAndHoldings(List<String> instanceIds, boolean skipSuppressedFromDiscoveryRecords,
                                             Handler<RowStream<Row>> rowStreamHandler,
                                             Map<String, String> headers) {
    return repository.streamItemsAndHoldings(instanceIds, skipSuppressedFromDiscoveryRecords, rowStreamHandler,
      tenantId(headers));
  }
}
