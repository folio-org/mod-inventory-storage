package org.folio.services.locationunit;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;
import org.folio.persist.InstitutionRepository;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Locinsts;
import org.folio.rest.jaxrs.resource.LocationUnits.DeleteLocationUnitsInstitutionsByIdResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.DeleteLocationUnitsInstitutionsResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.GetLocationUnitsInstitutionsByIdResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.GetLocationUnitsInstitutionsResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.PostLocationUnitsInstitutionsResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.PutLocationUnitsInstitutionsByIdResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.domainevent.InstitutionDomainEventPublisher;

@Log4j2
public class InstitutionService {

  public static final String INSTITUTION_TABLE = "locinstitution";
  private static final String MSG_ID_NOT_MATCH =
    "Illegal operation: Institution ID cannot be changed";

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final InstitutionRepository repository;
  private final InstitutionDomainEventPublisher domainEventService;

  public InstitutionService(Context vertxContext,
                            Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.repository = new InstitutionRepository(vertxContext, okapiHeaders);
    this.domainEventService =
      new InstitutionDomainEventPublisher(vertxContext, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return PgUtil.get(INSTITUTION_TABLE, Locinst.class, Locinsts.class, cql,
      offset, limit, okapiHeaders, vertxContext,
      GetLocationUnitsInstitutionsResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(INSTITUTION_TABLE, Locinst.class, id, okapiHeaders,
      vertxContext, GetLocationUnitsInstitutionsByIdResponse.class);
  }

  public Future<Response> create(Locinst institution) {
    return PgUtil.post(INSTITUTION_TABLE, institution, okapiHeaders,
        vertxContext, PostLocationUnitsInstitutionsResponse.class)
      .onSuccess(response ->  domainEventService.publishCreated().handle(response));
  }

  public Future<Response> update(String id, Locinst institution) {
    if (Objects.nonNull(institution.getId()) && !institution.getId().equals(id)) {
      return succeededFuture(
        PutLocationUnitsInstitutionsByIdResponse.respond400WithTextPlain(
          createValidationErrorMessage("locinst", institution.getId(),
            MSG_ID_NOT_MATCH)));
    }

    if (Objects.isNull(institution.getId())) {
      institution.setId(id);
    }

    return repository.getById(id)
      .compose(oldInstitution ->
        PgUtil.put(INSTITUTION_TABLE, institution, id,
          okapiHeaders, vertxContext,
          PutLocationUnitsInstitutionsByIdResponse.class)
        .onSuccess(domainEventService.publishUpdated(institution)));
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldInstitution ->
        PgUtil.deleteById(INSTITUTION_TABLE, id, okapiHeaders,
          vertxContext,
          DeleteLocationUnitsInstitutionsByIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldInstitution)));
  }

  public Future<Response> deleteAll() {
    return repository.deleteAll().transform(prepareDeleteAllResponse())
      .onSuccess(response -> domainEventService.publishAllRemoved());
  }

  private Function<AsyncResult<RowSet<Row>>, Future<Response>> prepareDeleteAllResponse() {
    return reply -> reply.succeeded()
      ? succeededFuture(DeleteLocationUnitsInstitutionsResponse.respond204())
      : succeededFuture(
      DeleteLocationUnitsInstitutionsResponse.respond500WithTextPlain(
        reply.cause().getMessage())
    );
  }
}
