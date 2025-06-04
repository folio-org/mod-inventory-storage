package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.CAMPUS;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.CampusRepository;
import org.folio.rest.jaxrs.model.Loccamp;

public class CampusDomainEventPublisher extends AbstractDomainEventPublisher<Loccamp, Loccamp> {

  public CampusDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new CampusRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, CAMPUS.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, Loccamp>>> getRecordIds(Collection<Loccamp> campuses) {
    return Future.succeededFuture(campuses.stream()
      .map(campus -> pair(campus.getId(), campus))
      .toList()
    );
  }

  @Override
  protected Loccamp convertDomainToEvent(String id, Loccamp campus) {
    return campus;
  }

  @Override
  protected String getId(Loccamp campus) {
    return campus.getId();
  }
}
