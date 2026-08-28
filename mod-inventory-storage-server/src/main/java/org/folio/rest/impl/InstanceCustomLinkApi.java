package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.jaxrs.model.InstanceCustomLinks;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.PgUtil;
import org.folio.services.instance.InstanceCustomLinkService;

public class InstanceCustomLinkApi extends BaseApi<InstanceCustomLink, InstanceCustomLinks>
  implements org.folio.rest.jaxrs.resource.InstanceCustomLinks {

  public static final String INSTANCE_CUSTOM_LINK_TYPE_TABLE = "instance_custom_link";

  private static final String FIELD_BASE_URL = "baseUrl";
  private static final List<String> QUERY_TOKENS = List.of("{{UUID}}", "{{HRID}}", "{{indexTitle}}");

  @Validate
  @Override
  public void getInstanceCustomLinks(String query, String totalRecords, int offset, int limit, Map<String,
      String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetInstanceCustomLinksResponse.class);
  }

  @Validate
  @Override
  public void postInstanceCustomLinks(InstanceCustomLink entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    var errors = validate(entity);
    if (!errors.isEmpty()) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostInstanceCustomLinksResponse.respond422WithApplicationJson(new Errors().withErrors(errors))));
      return;
    }

    new InstanceCustomLinkService(vertxContext, okapiHeaders).create(entity)
      .onSuccess(id -> asyncResultHandler.handle(Future.succeededFuture(PostInstanceCustomLinksResponse
        .respond201WithApplicationJson(entity.withId(id), PostInstanceCustomLinksResponse.headersFor201()))))
      .onFailure(cause -> {
        if (cause instanceof ValidationException ve) {
          asyncResultHandler.handle(Future.succeededFuture(PostInstanceCustomLinksResponse
            .respond422WithApplicationJson(ve.getErrors())));
        } else {
          try {
            var respond500 = PostInstanceCustomLinksResponse.class.getMethod("respond500WithTextPlain", Object.class);
            PgUtil.response(INSTANCE_CUSTOM_LINK_TYPE_TABLE, entity.getId(), cause,
              PostInstanceCustomLinksResponse.class, respond500, respond500).onComplete(asyncResultHandler);
          } catch (NoSuchMethodException ex) {
            Future.failedFuture(ex);
          }
        }
      });
  }

  @Validate
  @Override
  public void getInstanceCustomLinksById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetInstanceCustomLinksByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteInstanceCustomLinksById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteInstanceCustomLinksByIdResponse.class);
  }

  @Validate
  @Override
  public void putInstanceCustomLinksById(String id, InstanceCustomLink entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    var errors = validate(entity);

    if (!errors.isEmpty()) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PutInstanceCustomLinksByIdResponse.respond422WithApplicationJson(
          new Errors().withErrors(errors)
        )));
      return;
    }

    PgUtil.put(INSTANCE_CUSTOM_LINK_TYPE_TABLE, entity, id, okapiHeaders, vertxContext,
      PutInstanceCustomLinksByIdResponse.class, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return INSTANCE_CUSTOM_LINK_TYPE_TABLE;
  }

  @Override
  protected Class<InstanceCustomLink> getEntityClass() {
    return InstanceCustomLink.class;
  }

  @Override
  protected Class<InstanceCustomLinks> getEntityCollectionClass() {
    return InstanceCustomLinks.class;
  }

  private List<Error> validate(InstanceCustomLink entity) {
    List<Error> errors = new ArrayList<>();

    errors.addAll(validateQueryString(entity));
    errors.addAll(validateBaseUrl(entity));

    return errors;
  }

  private List<Error> validateQueryString(InstanceCustomLink entity) {
    List<Error> errors = new ArrayList<>();

    if (entity.getQueryString() != null) {
      var queryString = entity.getQueryString();
      if (QUERY_TOKENS.stream().noneMatch(queryString::contains)) {
        errors.add(fieldError("missingToken", "queryString",
          "must contain at least one of: " + QUERY_TOKENS, queryString));
      }
    }

    return errors;
  }

  private List<Error> validateBaseUrl(InstanceCustomLink entity) {
    List<Error> errors = new ArrayList<>();

    if (entity.getBaseUrl() != null) {
      var baseUrl = entity.getBaseUrl();
      try {
        var baseUri = new URI(baseUrl);
        baseUri.toURL();
        var baseScheme = baseUri.getScheme();
        if (!"http".equalsIgnoreCase(baseScheme) && !"https".equalsIgnoreCase(baseScheme)) {
          errors.add(fieldError("invalidScheme", FIELD_BASE_URL, "does not start with http or https", baseUrl));
        }
      } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
        errors.add(fieldError("invalidUrl", FIELD_BASE_URL, "not a valid URL", baseUrl));
      }
    }

    return errors;
  }

  private Error fieldError(String code, String field, String message, String value) {
    return new Error()
      .withCode(code)
      .withMessage(message)
      .withParameters(List.of(
        new Parameter().withKey(field).withValue(value)
      ));
  }
}
