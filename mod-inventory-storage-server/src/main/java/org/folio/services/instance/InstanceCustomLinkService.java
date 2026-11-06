package org.folio.services.instance;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.InstanceCustomLinkRepository;
import org.folio.persist.helper.PgViolationMapper;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.springframework.stereotype.Service;

@Service
public class InstanceCustomLinkService {
  public static final String INSTANCE_CUSTOM_LINK_TABLE = "instance_custom_link";

  private final InstanceCustomLinkRepository instanceCustomLinkRepository;

  public InstanceCustomLinkService(InstanceCustomLinkRepository instanceCustomLinkRepository) {
    this.instanceCustomLinkRepository = instanceCustomLinkRepository;
  }

  public Future<String> create(InstanceCustomLink entity, Map<String, String> headers) {
    return instanceCustomLinkRepository.inTransaction(tenantId(headers),
        conn -> instanceCustomLinkRepository.create(conn, entity))
      .recover(e -> Future.failedFuture(
        PgViolationMapper.map(INSTANCE_CUSTOM_LINK_TABLE, entity.getId(), e)));
  }

  public Future<String> modify(String id, InstanceCustomLink entity, Map<String, String> headers) {
    return instanceCustomLinkRepository.inTransaction(tenantId(headers),
        conn -> instanceCustomLinkRepository.modify(conn, id, entity))
      .recover(e -> Future.failedFuture(PgViolationMapper.map(INSTANCE_CUSTOM_LINK_TABLE, id, e)));
  }
}
