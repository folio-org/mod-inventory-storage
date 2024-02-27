package org.folio.services.classification;

import static org.folio.rest.jaxrs.resource.ClassificationTypes.DeleteClassificationTypesByClassificationTypeIdResponse;
import static org.folio.rest.jaxrs.resource.ClassificationTypes.GetClassificationTypesByClassificationTypeIdResponse;
import static org.folio.rest.jaxrs.resource.ClassificationTypes.GetClassificationTypesResponse;
import static org.folio.rest.jaxrs.resource.ClassificationTypes.PostClassificationTypesResponse;
import static org.folio.rest.jaxrs.resource.ClassificationTypes.PutClassificationTypesByClassificationTypeIdResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.getById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.ClassificationTypeRepository;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.folio.rest.jaxrs.model.ClassificationTypes;
import org.folio.services.domainevent.ClassificationTypeDomainEventPublisher;

public class ClassificationTypeService {

  public static final String CLASSIFICATION_TYPE_TABLE = "classification_type";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final ClassificationTypeRepository repository;
  private final ClassificationTypeDomainEventPublisher domainEventService;

  public ClassificationTypeService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;

    this.repository = new ClassificationTypeRepository(context, okapiHeaders);
    this.domainEventService = new ClassificationTypeDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return get(CLASSIFICATION_TYPE_TABLE, ClassificationType.class, ClassificationTypes.class,
      cql, offset, limit, okapiHeaders, context, GetClassificationTypesResponse.class);
  }

  public Future<Response> getByTypeId(String id) {
    return getById(CLASSIFICATION_TYPE_TABLE, ClassificationType.class, id, okapiHeaders, context,
      GetClassificationTypesByClassificationTypeIdResponse.class);
  }

  public Future<Response> create(ClassificationType type) {
    return post(CLASSIFICATION_TYPE_TABLE, type, okapiHeaders, context, PostClassificationTypesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> update(String id, ClassificationType type) {
    return repository.getById(id)
      .compose(oldType -> put(CLASSIFICATION_TYPE_TABLE, type, id, okapiHeaders, context,
        PutClassificationTypesByClassificationTypeIdResponse.class)
        .onSuccess(domainEventService.publishUpdated(oldType))
      );
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldType -> deleteById(CLASSIFICATION_TYPE_TABLE, id, okapiHeaders, context,
        DeleteClassificationTypesByClassificationTypeIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldType))
      );
  }

}
