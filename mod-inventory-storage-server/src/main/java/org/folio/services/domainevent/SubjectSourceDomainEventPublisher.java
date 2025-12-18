package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.SUBJECT_SOURCE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.SubjectSourceRepository;
import org.folio.rest.jaxrs.model.SubjectSource;

public class SubjectSourceDomainEventPublisher extends AbstractDomainEventPublisher<SubjectSource, SubjectSource> {

  public SubjectSourceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new SubjectSourceRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, SUBJECT_SOURCE.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, SubjectSource>>> getRecordIds(
    Collection<SubjectSource> subjectSources) {
    return succeededFuture(subjectSources.stream()
      .map(subjectSource -> pair(subjectSource.getId(), subjectSource))
      .toList()
    );
  }

  @Override
  protected SubjectSource convertDomainToEvent(String instanceId, SubjectSource subjectSource) {
    return subjectSource;
  }

  @Override
  protected String getId(SubjectSource subjectSource) {
    return subjectSource.getId();
  }
}
