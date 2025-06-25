package org.folio.services.locationunit;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Map;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import org.folio.persist.CampusRepository;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Loccamps;
import org.folio.rest.jaxrs.resource.LocationUnits.DeleteLocationUnitsCampusesByIdResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.DeleteLocationUnitsCampusesResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.GetLocationUnitsCampusesByIdResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.GetLocationUnitsCampusesResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.PostLocationUnitsCampusesResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.PutLocationUnitsCampusesByIdResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.domainevent.CampusDomainEventPublisher;

public class CampusService {

  private static final String MSG_ID_NOT_MATCH = "Illegal operation: Campus ID cannot be changed";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final CampusRepository repository;
  private final CampusDomainEventPublisher domainEventService;

  public CampusService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new CampusRepository(context, okapiHeaders);
    this.domainEventService = new CampusDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return PgUtil.get(CAMPUS_TABLE, Loccamp.class, Loccamps.class,
      cql, offset, limit, okapiHeaders, context,
      GetLocationUnitsCampusesResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(CAMPUS_TABLE, Loccamp.class, id, okapiHeaders, context,
      GetLocationUnitsCampusesByIdResponse.class);
  }

  public Future<Response> create(Loccamp loccamp) {
    return PgUtil.post(CAMPUS_TABLE, loccamp, okapiHeaders, context,
        PostLocationUnitsCampusesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> update(String id, Loccamp loccamp) {
    if (loccamp.getId() != null && !loccamp.getId().equals(id)) {
      return succeededFuture(
        PutLocationUnitsCampusesByIdResponse.respond400WithTextPlain(
          createValidationErrorMessage(
            "loccamp", loccamp.getId(), MSG_ID_NOT_MATCH)));
    }
    if (loccamp.getId() == null) {
      loccamp.setId(id);
    }

    return repository.getById(id)
      .compose(oldLoccamp ->
        PgUtil.put(CAMPUS_TABLE, loccamp, id, okapiHeaders, context,
            PutLocationUnitsCampusesByIdResponse.class)
          .onSuccess(domainEventService.publishUpdated(oldLoccamp))
      );
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldLoccamp ->
        PgUtil.deleteById(CAMPUS_TABLE, id, okapiHeaders, context,
            DeleteLocationUnitsCampusesByIdResponse.class)
          .onSuccess(domainEventService.publishRemoved(oldLoccamp))
      );
  }

  public Future<Response> deleteAll() {
    return repository.deleteAll()
      .transform(prepareDeleteAllResponse())
      .onSuccess(response -> domainEventService.publishAllRemoved());
  }

  private Function<AsyncResult<RowSet<Row>>, Future<Response>> prepareDeleteAllResponse() {
    return reply -> reply.succeeded()
      ? succeededFuture(DeleteLocationUnitsCampusesResponse.respond204())
      : succeededFuture(
      DeleteLocationUnitsCampusesResponse.respond500WithTextPlain(
        reply.cause().getMessage())
    );
  }
}
