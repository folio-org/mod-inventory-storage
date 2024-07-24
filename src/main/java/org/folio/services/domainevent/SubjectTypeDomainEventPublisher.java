package org.folio.services.domainevent;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.SubjectTypeRepository;
import org.folio.rest.jaxrs.model.SubjectType;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.SUBJECT_TYPE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

public class SubjectTypeDomainEventPublisher extends AbstractDomainEventPublisher<SubjectType, SubjectType> {

  public SubjectTypeDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new SubjectTypeRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders, SUBJECT_TYPE.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, SubjectType>>> getRecordIds(Collection<SubjectType> subjectTypes) {
    return succeededFuture(subjectTypes.stream()
      .map(subjectType -> pair(subjectType.getId(), subjectType))
      .toList()
    );
  }

  @Override
  protected SubjectType convertDomainToEvent(String instanceId, SubjectType subjectType) {
    return subjectType;
  }

  @Override
  protected String getId(SubjectType subjectType) {
    return subjectType.getId();
  }
}
