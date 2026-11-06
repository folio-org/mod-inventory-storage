package org.folio.services.instanceset;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.InstanceSetRepository;
import org.folio.persist.entity.InstanceSetParts;
import org.springframework.stereotype.Service;

@Service
public class InstanceSetService {
  private final InstanceSetRepository repository;

  public InstanceSetService(InstanceSetRepository repository) {
    this.repository = repository;
  }

  public Future<String> getInstanceSet(String query, InstanceSetParts parts, int offset, int limit,
                                       Map<String, String> headers) {
    return repository.getInstanceSet(query, parts, offset, limit, tenantId(headers));
  }
}
