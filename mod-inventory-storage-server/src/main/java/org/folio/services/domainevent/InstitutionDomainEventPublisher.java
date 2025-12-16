package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.INSTITUTION;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.InstitutionRepository;
import org.folio.rest.jaxrs.model.Locinst;

public class InstitutionDomainEventPublisher extends AbstractDomainEventPublisher<Locinst, Locinst> {

  public InstitutionDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new InstitutionRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, INSTITUTION.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, Locinst>>> getRecordIds(Collection<Locinst> institutions) {
    return succeededFuture(institutions.stream()
      .map(institution -> pair(institution.getId(), institution))
      .toList()
    );
  }

  @Override
  protected Locinst convertDomainToEvent(String id, Locinst institution) {
    return institution;
  }

  @Override
  protected String getId(Locinst institution) {
    return institution.getId();
  }
}
