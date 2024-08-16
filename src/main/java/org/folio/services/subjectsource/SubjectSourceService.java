package org.folio.services.subjectsource;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.SubjectSources.DeleteSubjectSourcesBySubjectSourceIdResponse;
import static org.folio.rest.jaxrs.resource.SubjectSources.PostSubjectSourcesResponse;
import static org.folio.rest.jaxrs.resource.SubjectSources.PostSubjectSourcesResponse.respond422WithApplicationJson;
import static org.folio.rest.jaxrs.resource.SubjectSources.PutSubjectSourcesBySubjectSourceIdResponse;
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
import org.folio.persist.SubjectSourceRepository;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.model.SubjectSources;
import org.folio.rest.jaxrs.resource.SubjectSources.GetSubjectSourcesBySubjectSourceIdResponse;
import org.folio.rest.jaxrs.resource.SubjectSources.GetSubjectSourcesResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.domainevent.SubjectSourceDomainEventPublisher;

public class SubjectSourceService {

  public static final String SUBJECT_SOURCE = "subject_source";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final SubjectSourceRepository repository;
  private final SubjectSourceDomainEventPublisher domainEventService;

  public SubjectSourceService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new SubjectSourceRepository(context, okapiHeaders);
    this.domainEventService = new SubjectSourceDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return get(SUBJECT_SOURCE, SubjectSource.class, SubjectSources.class,
      cql, offset, limit, okapiHeaders, context, GetSubjectSourcesResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(SUBJECT_SOURCE, SubjectSource.class, id, okapiHeaders, context,
      GetSubjectSourcesBySubjectSourceIdResponse.class);
  }

  public Future<Response> create(SubjectSource subjectSource) {
    if (subjectSource.getSource().equals(SubjectSource.Source.FOLIO)) {
      return sourceValidationError(subjectSource.getSource().value(), SOURCE_CANNOT_BE_FOLIO);
    }
    return post(SUBJECT_SOURCE, subjectSource, okapiHeaders, context, PostSubjectSourcesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> update(String id, SubjectSource subjectSource) {
    return repository.getById(id)
      .compose(oldSubjectSource -> {
        if (!oldSubjectSource.getSource().equals(subjectSource.getSource())) {
          return sourceValidationError(subjectSource.getSource().value(), SOURCE_CANNOT_BE_UPDATED);
        }
        return put(SUBJECT_SOURCE, subjectSource, id, okapiHeaders, context,
          PutSubjectSourcesBySubjectSourceIdResponse.class)
          .onSuccess(domainEventService.publishUpdated(subjectSource));
      });
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldSubjectSource -> deleteById(SUBJECT_SOURCE, id, okapiHeaders, context,
        DeleteSubjectSourcesBySubjectSourceIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldSubjectSource))
      );
  }

  private Future<Response> sourceValidationError(String field, String message) {
    return succeededFuture(
      respond422WithApplicationJson(
        createValidationErrorMessage("source", field,
          message)));
  }
}
