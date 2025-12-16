package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationship;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationships;

public class ElectronicAccessRelationshipApi
  extends BaseApi<ElectronicAccessRelationship, ElectronicAccessRelationships>
  implements org.folio.rest.jaxrs.resource.ElectronicAccessRelationships {

  public static final String ELECTRONIC_ACCESS_RELATIONSHIP_TABLE = "electronic_access_relationship";

  @Validate
  @Override
  public void getElectronicAccessRelationships(String query, String totalRecords, int offset, int limit,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetElectronicAccessRelationshipsResponse.class);
  }

  @Validate
  @Override
  public void postElectronicAccessRelationships(ElectronicAccessRelationship entity,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostElectronicAccessRelationshipsResponse.class);
  }

  @Validate
  @Override
  public void getElectronicAccessRelationshipsById(String id, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext,
      GetElectronicAccessRelationshipsByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteElectronicAccessRelationshipsById(String id, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteElectronicAccessRelationshipsByIdResponse.class);
  }

  @Validate
  @Override
  public void putElectronicAccessRelationshipsById(String id, ElectronicAccessRelationship entity,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutElectronicAccessRelationshipsByIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return ELECTRONIC_ACCESS_RELATIONSHIP_TABLE;
  }

  @Override
  protected Class<ElectronicAccessRelationship> getEntityClass() {
    return ElectronicAccessRelationship.class;
  }

  @Override
  protected Class<ElectronicAccessRelationships> getEntityCollectionClass() {
    return ElectronicAccessRelationships.class;
  }
}
