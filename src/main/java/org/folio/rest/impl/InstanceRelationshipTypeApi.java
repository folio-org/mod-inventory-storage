package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceRelationshipType;
import org.folio.rest.jaxrs.model.InstanceRelationshipTypes;

public class InstanceRelationshipTypeApi extends BaseApi<InstanceRelationshipType, InstanceRelationshipTypes>
  implements org.folio.rest.jaxrs.resource.InstanceRelationshipTypes {

  public static final String INSTANCE_RELATIONSHIP_TYPE_TABLE = "instance_relationship_type";

  @Validate
  @Override
  public void getInstanceRelationshipTypes(String query, String totalRecords, int offset, int limit,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetInstanceRelationshipTypesResponse.class);
  }

  @Validate
  @Override
  public void postInstanceRelationshipTypes(InstanceRelationshipType entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostInstanceRelationshipTypesResponse.class);
  }

  @Validate
  @Override
  public void getInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId,
                                                               Map<String, String> okapiHeaders,
                                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                                               Context vertxContext) {
    getEntityById(relationshipTypeId, okapiHeaders, asyncResultHandler, vertxContext,
      GetInstanceRelationshipTypesByRelationshipTypeIdResponse.class);
  }

  @Validate
  @Override
  public void deleteInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId,
                                                                  Map<String, String> okapiHeaders,
                                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                                  Context vertxContext) {
    deleteEntityById(relationshipTypeId, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse.class);
  }

  @Validate
  @Override
  public void putInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId,
                                                               InstanceRelationshipType entity,
                                                               Map<String, String> okapiHeaders,
                                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                                               Context vertxContext) {
    putEntityById(relationshipTypeId, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutInstanceRelationshipTypesByRelationshipTypeIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return INSTANCE_RELATIONSHIP_TYPE_TABLE;
  }

  @Override
  protected Class<InstanceRelationshipType> getEntityClass() {
    return InstanceRelationshipType.class;
  }

  @Override
  protected Class<InstanceRelationshipTypes> getEntityCollectionClass() {
    return InstanceRelationshipTypes.class;
  }
}
