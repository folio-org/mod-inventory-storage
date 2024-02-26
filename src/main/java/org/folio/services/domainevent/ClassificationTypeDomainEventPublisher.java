package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.CLASSIFICATION_TYPE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.ClassificationTypeRepository;
import org.folio.rest.jaxrs.model.ClassificationType;

public class ClassificationTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<ClassificationType, ClassificationType> {

  public ClassificationTypeDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new ClassificationTypeRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        CLASSIFICATION_TYPE.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, ClassificationType>>> getRecordIds(Collection<ClassificationType> records) {
    return succeededFuture(records.stream()
      .map(record -> pair(record.getId(), record))
      .toList()
    );
  }

  @Override
  protected ClassificationType convertDomainToEvent(String instanceId, ClassificationType domain) {
    return domain;
  }

  @Override
  protected String getId(ClassificationType entity) {
    return entity.getId();
  }
}
