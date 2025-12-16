package org.folio.services.callnumber;

import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.getById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.CallNumberTypeRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.CallNumberTypes;
import org.folio.rest.jaxrs.resource.CallNumberTypes.DeleteCallNumberTypesByIdResponse;
import org.folio.rest.jaxrs.resource.CallNumberTypes.GetCallNumberTypesByIdResponse;
import org.folio.rest.jaxrs.resource.CallNumberTypes.GetCallNumberTypesResponse;
import org.folio.rest.jaxrs.resource.CallNumberTypes.PostCallNumberTypesResponse;
import org.folio.rest.jaxrs.resource.CallNumberTypes.PutCallNumberTypesByIdResponse;
import org.folio.services.domainevent.CallNumberTypeDomainEventPublisher;

public class CallNumberTypeService {

  public static final String CALL_NUMBER_TYPE_TABLE = "call_number_type";

  private static final String SYSTEM_CALL_NUMBER_TYPE_SOURCE = "system";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final CallNumberTypeRepository repository;
  private final CallNumberTypeDomainEventPublisher domainEventService;

  public CallNumberTypeService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;

    this.repository = new CallNumberTypeRepository(context, okapiHeaders);
    this.domainEventService = new CallNumberTypeDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return get(CALL_NUMBER_TYPE_TABLE, CallNumberType.class, CallNumberTypes.class,
      cql, offset, limit, okapiHeaders, context, GetCallNumberTypesResponse.class);
  }

  public Future<Response> getByTypeId(String id) {
    return getById(CALL_NUMBER_TYPE_TABLE, CallNumberType.class, id, okapiHeaders, context,
      GetCallNumberTypesByIdResponse.class);
  }

  public Future<Response> create(CallNumberType type) {
    return post(CALL_NUMBER_TYPE_TABLE, type, okapiHeaders, context, PostCallNumberTypesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> update(String id, CallNumberType type) {
    return getIfNotSystemCallNumberType(id, "System call number type couldn't be updated")
      .compose(oldType -> put(CALL_NUMBER_TYPE_TABLE, type, id, okapiHeaders, context,
        PutCallNumberTypesByIdResponse.class)
        .onSuccess(domainEventService.publishUpdated(oldType))
      );
  }

  public Future<Response> delete(String id) {
    return getIfNotSystemCallNumberType(id, "System call number type couldn't be deleted")
      .compose(oldType -> deleteById(CALL_NUMBER_TYPE_TABLE, id, okapiHeaders, context,
        DeleteCallNumberTypesByIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldType))
      );
  }

  private Future<CallNumberType> getIfNotSystemCallNumberType(String id, String errorMessage) {
    return repository.getById(id)
      .compose(callNumberType -> {
        if (isSystemSource(callNumberType)) {
          return Future.failedFuture(new BadRequestException(errorMessage));
        }
        return Future.succeededFuture(callNumberType);
      });
  }

  private boolean isSystemSource(CallNumberType callNumberType) {
    return SYSTEM_CALL_NUMBER_TYPE_SOURCE.equals(callNumberType.getSource());
  }
}
