package org.folio.services.subjecttype;

import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.support.SubjectUtil.sourceValidationError;
import static org.folio.rest.support.SubjectUtil.validateSubjectSourceCreate;
import static org.folio.rest.support.SubjectUtil.validateSubjectSourceUpdate;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.SubjectTypeRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.SubjectType;
import org.folio.rest.jaxrs.model.SubjectTypes;
import org.folio.rest.jaxrs.resource.SubjectTypes.DeleteSubjectTypesBySubjectTypeIdResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.GetSubjectTypesBySubjectTypeIdResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.GetSubjectTypesResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.PostSubjectTypesResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.PutSubjectTypesBySubjectTypeIdResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.ConsortiumServiceImpl;
import org.folio.services.domainevent.SubjectTypeDomainEventPublisher;

public class SubjectTypeService {
  public static final String SUBJECT_TYPE = "subject_type";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final SubjectTypeRepository repository;
  private final SubjectTypeDomainEventPublisher domainEventService;
  private final ConsortiumService consortiumService;

  public SubjectTypeService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new SubjectTypeRepository(context, okapiHeaders);
    this.domainEventService = new SubjectTypeDomainEventPublisher(context, okapiHeaders);
    this.consortiumService = new ConsortiumServiceImpl(context.owner().createHttpClient(),
      context.get(ConsortiumDataCache.class.getName()));
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return get(SUBJECT_TYPE, SubjectType.class, SubjectTypes.class,
      cql, offset, limit, okapiHeaders, context, GetSubjectTypesResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(SUBJECT_TYPE, SubjectType.class, id, okapiHeaders, context,
      GetSubjectTypesBySubjectTypeIdResponse.class);
  }

  public Future<Response> create(SubjectType subjectType) {
    return validateSubjectSourceCreate(subjectType.getSource().value(), consortiumService, okapiHeaders)
      .compose(errorsOptional -> errorsOptional.isPresent() ? sourceValidationError(errorsOptional.get()) :
        createSubjectType(subjectType));
  }

  public Future<Response> update(String id, SubjectType subjectType) {
    if (subjectType.getId() == null) {
      subjectType.setId(id);
    }

    return repository.getById(id)
      .compose(oldSubjectType -> {
        if (oldSubjectType != null) {
          return validateSubjectSourceUpdate(subjectType.getSource().value(), oldSubjectType.getSource().value(),
            consortiumService, okapiHeaders)
            .compose(errorsOptional -> errorsOptional.isPresent()
              ? sourceValidationError(errorsOptional.get()) : updateSubjectType(id, subjectType));
        }
        return Future.failedFuture(new NotFoundException("SubjectType was not found"));
      });
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldSubjectType -> deleteById(SUBJECT_TYPE, id, okapiHeaders, context,
        DeleteSubjectTypesBySubjectTypeIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldSubjectType))
      );
  }

  private Future<Response> createSubjectType(SubjectType subjectType) {
    return post(SUBJECT_TYPE, subjectType, okapiHeaders, context, PostSubjectTypesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  private Future<Response> updateSubjectType(String id, SubjectType subjectType) {
    return put(SUBJECT_TYPE, subjectType, id, okapiHeaders, context, PutSubjectTypesBySubjectTypeIdResponse.class)
      .onSuccess(domainEventService.publishUpdated(subjectType));
  }
}
