package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import java.util.function.Predicate;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource.Source;
import org.folio.rest.jaxrs.model.HoldingsRecordsSources;

public class HoldingsRecordsSourceApi extends BaseApi<HoldingsRecordsSource, HoldingsRecordsSources>
  implements org.folio.rest.jaxrs.resource.HoldingsSources {

  public static final String HOLDINGS_RECORDS_SOURCE_TABLE = "holdings_records_source";

  @Validate
  @Override
  public void getHoldingsSources(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetHoldingsSourcesResponse.class);
  }

  @Validate
  @Override
  public void postHoldingsSources(HoldingsRecordsSource entity,
                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostHoldingsSourcesResponse.class);
  }

  @Validate
  @Override
  public void getHoldingsSourcesById(String id,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetHoldingsSourcesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteHoldingsSourcesById(String id,
                                        Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteHoldingsSourcesByIdResponse.class);
  }

  @Validate
  @Override
  public void putHoldingsSourcesById(String id,
                                     HoldingsRecordsSource entity, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext, PutHoldingsSourcesByIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return HOLDINGS_RECORDS_SOURCE_TABLE;
  }

  @Override
  protected Class<HoldingsRecordsSource> getEntityClass() {
    return HoldingsRecordsSource.class;
  }

  @Override
  protected Class<HoldingsRecordsSources> getEntityCollectionClass() {
    return HoldingsRecordsSources.class;
  }

  @Override
  protected Map<String, Predicate<HoldingsRecordsSource>> deleteValidationPredicates() {
    return Map.of(
      "Holdings Records Sources with source of folio can not be deleted",
      holdingsRecordsSource -> holdingsRecordsSource.getSource() != Source.FOLIO
    );
  }
}
