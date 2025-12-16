package org.folio.services.subjectsource;

import static org.folio.rest.jaxrs.resource.SubjectSources.DeleteSubjectSourcesBySubjectSourceIdResponse;
import static org.folio.rest.jaxrs.resource.SubjectSources.PostSubjectSourcesResponse;
import static org.folio.rest.jaxrs.resource.SubjectSources.PutSubjectSourcesBySubjectSourceIdResponse;
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
import org.folio.persist.SubjectSourceRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.model.SubjectSources;
import org.folio.rest.jaxrs.resource.SubjectSources.GetSubjectSourcesBySubjectSourceIdResponse;
import org.folio.rest.jaxrs.resource.SubjectSources.GetSubjectSourcesResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.ConsortiumServiceImpl;
import org.folio.services.domainevent.SubjectSourceDomainEventPublisher;

public class SubjectSourceService {

  public static final String SUBJECT_SOURCE = "subject_source";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final SubjectSourceRepository repository;
  private final SubjectSourceDomainEventPublisher domainEventService;
  private final ConsortiumService consortiumService;

  public SubjectSourceService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new SubjectSourceRepository(context, okapiHeaders);
    this.domainEventService = new SubjectSourceDomainEventPublisher(context, okapiHeaders);
    this.consortiumService = new ConsortiumServiceImpl(context.owner().createHttpClient(),
      context.get(ConsortiumDataCache.class.getName()));
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
    return validateSubjectSourceCreate(subjectSource.getSource().value(), consortiumService, okapiHeaders)
      .compose(errorsOptional -> errorsOptional.isPresent() ? sourceValidationError(errorsOptional.get()) :
        createSubjectSource(subjectSource));
  }

  public Future<Response> update(String id, SubjectSource subjectSource) {
    if (subjectSource.getId() == null) {
      subjectSource.setId(id);
    }

    return repository.getById(id)
      .compose(oldSubjectSource -> {
        if (oldSubjectSource != null) {
          return validateSubjectSourceUpdate(subjectSource.getSource().value(), oldSubjectSource.getSource().value(),
            consortiumService, okapiHeaders)
            .compose(errorsOptional -> errorsOptional.isPresent()
              ? sourceValidationError(errorsOptional.get()) : updateSubjectSource(id, subjectSource));
        }
        return Future.failedFuture(new NotFoundException("SubjectSource was not found"));
      });
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldSubjectSource -> {
        if (oldSubjectSource != null) {
          return deleteById(SUBJECT_SOURCE, id, okapiHeaders, context,
            DeleteSubjectSourcesBySubjectSourceIdResponse.class)
            .onSuccess(domainEventService.publishRemoved(oldSubjectSource));
        }
        return Future.failedFuture(new NotFoundException("SubjectSource was not found"));
      });
  }

  private Future<Response> createSubjectSource(SubjectSource subjectSource) {
    return post(SUBJECT_SOURCE, subjectSource, okapiHeaders, context, PostSubjectSourcesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  private Future<Response> updateSubjectSource(String id, SubjectSource subjectSource) {
    return put(SUBJECT_SOURCE, subjectSource, id, okapiHeaders, context,
      PutSubjectSourcesBySubjectSourceIdResponse.class)
      .onSuccess(domainEventService.publishUpdated(subjectSource));
  }
}
