package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.IllPolicies;
import org.folio.rest.jaxrs.model.IllPolicy;

public class IllPolicyApi extends BaseApi<IllPolicy, IllPolicies>
  implements org.folio.rest.jaxrs.resource.IllPolicies {

  public static final String ILL_POLICY_TABLE = "ill_policy";

  @Validate
  @Override
  public void getIllPolicies(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetIllPoliciesResponse.class);
  }

  @Validate
  @Override
  public void postIllPolicies(IllPolicy entity, Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostIllPoliciesResponse.class);
  }

  @Validate
  @Override
  public void getIllPoliciesById(String id, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetIllPoliciesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteIllPoliciesById(String id, Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteIllPoliciesByIdResponse.class);
  }

  @Validate
  @Override
  public void putIllPoliciesById(String id, IllPolicy entity, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext, PutIllPoliciesByIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return ILL_POLICY_TABLE;
  }

  @Override
  protected Class<IllPolicy> getEntityClass() {
    return IllPolicy.class;
  }

  @Override
  protected Class<IllPolicies> getEntityCollectionClass() {
    return IllPolicies.class;
  }
}
