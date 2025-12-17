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
import org.folio.rest.jaxrs.model.LocationCampus;

public class CampusDomainEventPublisher extends AbstractDomainEventPublisher<LocationCampus, LocationCampus> {

  public CampusDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new CampusRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, CAMPUS.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, LocationCampus>>> getRecordIds(Collection<LocationCampus> campuses) {
    return Future.succeededFuture(campuses.stream()
      .map(campus -> pair(campus.getId(), campus))
      .toList()
    );
  }

  @Override
  protected LocationCampus convertDomainToEvent(String id, LocationCampus campus) {
    return campus;
  }

  @Override
  protected String getId(LocationCampus campus) {
    return campus.getId();
  }
}
