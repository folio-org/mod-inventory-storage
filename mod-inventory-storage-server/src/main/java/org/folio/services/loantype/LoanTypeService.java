package org.folio.services.loantype;

import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.LoanTypeRepository;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.LoanTypes;
import org.folio.rest.jaxrs.resource.LoanTypes.DeleteLoanTypesByLoantypeIdResponse;
import org.folio.rest.jaxrs.resource.LoanTypes.GetLoanTypesByLoantypeIdResponse;
import org.folio.rest.jaxrs.resource.LoanTypes.GetLoanTypesResponse;
import org.folio.rest.jaxrs.resource.LoanTypes.PostLoanTypesResponse;
import org.folio.rest.jaxrs.resource.LoanTypes.PutLoanTypesByLoantypeIdResponse;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.services.domainevent.LoanTypeDomainEventPublisher;

public class LoanTypeService {

  public static final String LOAN_TYPE_TABLE = "loan_type";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final LoanTypeRepository repository;
  private final LoanTypeDomainEventPublisher domainEventService;

  public LoanTypeService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new LoanTypeRepository(context, okapiHeaders);
    this.domainEventService = new LoanTypeDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return get(LOAN_TYPE_TABLE, LoanType.class, LoanTypes.class,
      cql, offset, limit, okapiHeaders, context, GetLoanTypesResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(LOAN_TYPE_TABLE, LoanType.class, id, okapiHeaders, context,
      GetLoanTypesByLoantypeIdResponse.class);
  }

  public Future<Response> create(LoanType entity) {
    return post(LOAN_TYPE_TABLE, entity, okapiHeaders, context, PostLoanTypesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> update(String id, LoanType entity) {
    return repository.getById(id)
      .compose(oldLoanType -> put(LOAN_TYPE_TABLE, entity, id, okapiHeaders, context,
        PutLoanTypesByLoantypeIdResponse.class)
        .onSuccess(domainEventService.publishUpdated(oldLoanType))
      );
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldLoanType -> deleteById(LOAN_TYPE_TABLE, id, okapiHeaders, context,
        DeleteLoanTypesByLoantypeIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldLoanType))
      );
  }

  public Future<Response> deleteAll() {
    return PostgresClientFactory.getInstance(context, okapiHeaders)
      .delete(LOAN_TYPE_TABLE, new Criterion())
      .compose(notUsed -> domainEventService.publishAllRemoved())
      .map(notUsed -> Response.noContent().build());
  }
}
