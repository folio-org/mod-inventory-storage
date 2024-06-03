package org.folio.services.locationunit;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Map;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import org.folio.persist.LibraryRepository;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.model.Loclibs;
import org.folio.rest.jaxrs.resource.LocationUnits.DeleteLocationUnitsLibrariesByIdResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.DeleteLocationUnitsLibrariesResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.GetLocationUnitsLibrariesByIdResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.GetLocationUnitsLibrariesResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.PostLocationUnitsLibrariesResponse;
import org.folio.rest.jaxrs.resource.LocationUnits.PutLocationUnitsLibrariesByIdResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.services.domainevent.LibraryDomainEventPublisher;

public class LibraryService {

  public static final String LIBRARY_TABLE = "loclibrary";
  private static final String MSG_ID_NOT_MATCH =
    "Illegal operation: Library ID cannot be changed";
  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final LibraryRepository repository;
  private final LibraryDomainEventPublisher domainEventService;

  public LibraryService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new LibraryRepository(context, okapiHeaders);
    this.domainEventService =
      new LibraryDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return PgUtil.get(LIBRARY_TABLE, Loclib.class, Loclibs.class,
      cql, offset, limit, okapiHeaders, context,
      GetLocationUnitsLibrariesResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(LIBRARY_TABLE, Loclib.class, id, okapiHeaders, context,
      GetLocationUnitsLibrariesByIdResponse.class);
  }

  public Future<Response> create(Loclib loclib) {
    return PgUtil.post(LIBRARY_TABLE, loclib, okapiHeaders, context,
        PostLocationUnitsLibrariesResponse.class)
      .otherwise(throwable ->
        PostLocationUnitsLibrariesResponse.respond500WithTextPlain(
          throwable.getMessage()));
  }

  public Future<Response> update(String id, Loclib loclib) {
    if (loclib.getId() != null && !loclib.getId().equals(id)) {
      return succeededFuture(
        PutLocationUnitsLibrariesByIdResponse.respond400WithTextPlain(
          createValidationErrorMessage(
            "loclib", loclib.getId(), MSG_ID_NOT_MATCH)));
    }
    if (loclib.getId() == null) {
      loclib.setId(id);
    }

    return repository.getById(id)
      .compose(oldLoclib ->
        PgUtil.put(LIBRARY_TABLE, loclib, id, okapiHeaders, context,
            PutLocationUnitsLibrariesByIdResponse.class)
          .onSuccess(domainEventService.publishUpdated(oldLoclib))
          .otherwise(throwable ->
            PostLocationUnitsLibrariesResponse.respond500WithTextPlain(
              throwable.getMessage()))
      );
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldLocation ->
        PgUtil.deleteById(LIBRARY_TABLE, id, okapiHeaders, context,
            DeleteLocationUnitsLibrariesByIdResponse.class)
          .onSuccess(domainEventService.publishRemoved(oldLocation))
      );
  }

  public Future<Response> deleteAll() {
    return repository.deleteAll()
      .transform(prepareDeleteAllResponse())
      .onSuccess(response -> domainEventService.publishAllRemoved());
  }

  private Function<AsyncResult<RowSet<Row>>, Future<Response>> prepareDeleteAllResponse() {
    return reply -> reply.succeeded()
      ? succeededFuture(DeleteLocationUnitsLibrariesResponse.respond204())
      : succeededFuture(
      DeleteLocationUnitsLibrariesResponse.respond500WithTextPlain(
        reply.cause().getMessage())
    );
  }

}

