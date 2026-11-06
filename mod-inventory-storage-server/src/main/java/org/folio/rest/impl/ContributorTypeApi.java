package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ContributorType;
import org.folio.rest.jaxrs.model.ContributorTypes;

/**
 * Implements the instance contributor type persistency using postgres jsonb.
 */
public class ContributorTypeApi extends BaseApi<ContributorType, ContributorTypes>
  implements org.folio.rest.jaxrs.resource.ContributorTypes {

  public static final String CONTRIBUTOR_TYPE_TABLE = "contributor_type";

  @Validate
  @Override
  public void getContributorTypes(String query, String totalRecords, int offset, int limit,
                                  Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postContributorTypes(ContributorType entity, Map<String, String> headers,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getContributorTypesByContributorTypeId(String contributorTypeId,
                                                     Map<String, String> headers,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    getEntityById(contributorTypeId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteContributorTypesByContributorTypeId(String contributorTypeId,
                                                        Map<String, String> headers,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {
    deleteEntityById(contributorTypeId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putContributorTypesByContributorTypeId(String contributorTypeId, ContributorType entity,
                                                     Map<String, String> headers,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    putEntityById(contributorTypeId, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return CONTRIBUTOR_TYPE_TABLE;
  }

  @Override
  protected Class<ContributorType> getEntityClass() {
    return ContributorType.class;
  }

  @Override
  protected Class<ContributorTypes> getEntityCollectionClass() {
    return ContributorTypes.class;
  }
}
