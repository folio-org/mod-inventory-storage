package org.folio.services.subjecttype;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.SubjectTypes.PostSubjectTypesResponse.respond422WithApplicationJson;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.support.ResponseUtil.SOURCE_CANNOT_BE_FOLIO;
import static org.folio.rest.support.ResponseUtil.SOURCE_CANNOT_BE_UPDATED;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.SubjectTypeRepository;
import org.folio.rest.jaxrs.model.SubjectType;
import org.folio.rest.jaxrs.model.SubjectTypes;
import org.folio.rest.jaxrs.resource.SubjectTypes.DeleteSubjectTypesBySubjectTypeIdResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.GetSubjectTypesBySubjectTypeIdResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.GetSubjectTypesResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.PostSubjectTypesResponse;
import org.folio.rest.jaxrs.resource.SubjectTypes.PutSubjectTypesBySubjectTypeIdResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.domainevent.SubjectTypeDomainEventPublisher;

public class SubjectTypeService {

  public static final String SUBJECT_TYPE = "subject_type";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final SubjectTypeRepository repository;
  private final SubjectTypeDomainEventPublisher domainEventService;

  public SubjectTypeService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new SubjectTypeRepository(context, okapiHeaders);
    this.domainEventService = new SubjectTypeDomainEventPublisher(context, okapiHeaders);
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
    if (subjectType.getSource().equals(SubjectType.Source.FOLIO)) {
      return sourceValidationError(subjectType.getSource().value(), SOURCE_CANNOT_BE_FOLIO);
    }
    return post(SUBJECT_TYPE, subjectType, okapiHeaders, context, PostSubjectTypesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> update(String id, SubjectType subjectType) {
    return repository.getById(id)
      .compose(oldSubjectType -> {
        if (!oldSubjectType.getSource().equals(subjectType.getSource())) {
          return sourceValidationError(subjectType.getSource().value(), SOURCE_CANNOT_BE_UPDATED);
        }
        return put(SUBJECT_TYPE, subjectType, id, okapiHeaders, context, PutSubjectTypesBySubjectTypeIdResponse.class)
          .onSuccess(domainEventService.publishUpdated(subjectType));
      });
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldSubjectType -> deleteById(SUBJECT_TYPE, id, okapiHeaders, context,
        DeleteSubjectTypesBySubjectTypeIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldSubjectType))
      );
  }

  private Future<Response> sourceValidationError(String field, String message) {
    return succeededFuture(
      respond422WithApplicationJson(
        createValidationErrorMessage("source", field,
          message)));
  }
}
