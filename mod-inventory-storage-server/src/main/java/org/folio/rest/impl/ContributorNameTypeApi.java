package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ContributorNameType;
import org.folio.rest.jaxrs.model.ContributorNameTypes;

public class ContributorNameTypeApi extends BaseApi<ContributorNameType, ContributorNameTypes>
  implements org.folio.rest.jaxrs.resource.ContributorNameTypes {

  public static final String CONTRIBUTOR_NAME_TYPE_TABLE = "contributor_name_type";

  @Validate
  @Override
  public void getContributorNameTypes(String query, String totalRecords, int offset, int limit,
                                      Map<String, String> headers,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postContributorNameTypes(ContributorNameType entity, Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                             Map<String, String> headers,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {

    getEntityById(contributorNameTypeId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                                Map<String, String> headers,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {
    deleteEntityById(contributorNameTypeId, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                             ContributorNameType entity,
                                                             Map<String, String> headers,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    putEntityById(contributorNameTypeId, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return CONTRIBUTOR_NAME_TYPE_TABLE;
  }

  @Override
  protected Class<ContributorNameType> getEntityClass() {
    return ContributorNameType.class;
  }

  @Override
  protected Class<ContributorNameTypes> getEntityCollectionClass() {
    return ContributorNameTypes.class;
  }
}
