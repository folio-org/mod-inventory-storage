package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ContributorNameType;
import org.folio.rest.jaxrs.model.ContributorNameTypes;
import org.folio.rest.persist.PgUtil;

public class ContributorNameTypeApi extends BaseApi<ContributorNameType, ContributorNameTypes>
  implements org.folio.rest.jaxrs.resource.ContributorNameTypes {

  public static final String CONTRIBUTOR_NAME_TYPE_TABLE = "contributor_name_type";

  @Validate
  @Override
  public void getContributorNameTypes(String query, String totalRecords, int offset, int limit,
                                      Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetContributorNameTypesResponse.class);
  }

  @Validate
  @Override
  public void postContributorNameTypes(ContributorNameType entity, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostContributorNameTypesResponse.class);
  }

  @Validate
  @Override
  public void getContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {

    PgUtil.getById(CONTRIBUTOR_NAME_TYPE_TABLE, ContributorNameType.class, contributorNameTypeId,
      okapiHeaders, vertxContext, GetContributorNameTypesByContributorNameTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                                Map<String, String> okapiHeaders,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {
    deleteEntityById(contributorNameTypeId, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteContributorNameTypesByContributorNameTypeIdResponse.class);
  }

  @Validate
  @Override
  public void putContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                             ContributorNameType entity,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    putEntityById(contributorNameTypeId, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutContributorNameTypesByContributorNameTypeIdResponse.class);
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
