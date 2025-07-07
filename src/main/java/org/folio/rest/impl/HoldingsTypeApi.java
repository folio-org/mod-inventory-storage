package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HoldingsType;
import org.folio.rest.jaxrs.model.HoldingsTypes;
import org.folio.rest.jaxrs.model.Parameter;

public class HoldingsTypeApi extends BaseApi<HoldingsType, HoldingsTypes>
  implements org.folio.rest.jaxrs.resource.HoldingsTypes {

  public static final String HOLDINGS_TYPE_TABLE = "holdings_type";

  @Validate
  @Override
  public void getHoldingsTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetHoldingsTypesResponse.class);
  }

  @Validate
  @Override
  public void postHoldingsTypes(HoldingsType entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostHoldingsTypesResponse.class,
      responseMapper());
  }

  @Validate
  @Override
  public void getHoldingsTypesById(String id, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetHoldingsTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteHoldingsTypesById(String id, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteHoldingsTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void putHoldingsTypesById(String id, HoldingsType entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext, PutHoldingsTypesByIdResponse.class,
      responseMapper());
  }

  @Override
  protected String getReferenceTable() {
    return HOLDINGS_TYPE_TABLE;
  }

  @Override
  protected Class<HoldingsType> getEntityClass() {
    return HoldingsType.class;
  }

  @Override
  protected Class<HoldingsTypes> getEntityCollectionClass() {
    return HoldingsTypes.class;
  }

  private UnaryOperator<Response> responseMapper() {
    return response -> {
      if (response.getEntity() instanceof String
          && isValueAlreadyExists(response.getEntity().toString())
          || isValueAlreadyExists(Json.encode(response.getEntity()))) {
        return getNameDuplicateResponse();
      } else {
        return response;
      }
    };
  }

  private boolean isValueAlreadyExists(String response) {
    return response.contains("value already exists");
  }

  private Response getNameDuplicateResponse() {
    var responseBuilder = Response.status(422).header(CONTENT_TYPE.toString(), APPLICATION_JSON);
    responseBuilder.entity(buildNameDuplicateError());
    return responseBuilder.build();
  }

  private Errors buildNameDuplicateError() {
    return new Errors().withErrors(singletonList(new Error()
      .withCode("name.duplicate")
      .withMessage("Cannot create/update entity; name is not unique")
      .withParameters(singletonList(new Parameter()
        .withKey("fieldLabel")
        .withValue("name")))));
  }
}
