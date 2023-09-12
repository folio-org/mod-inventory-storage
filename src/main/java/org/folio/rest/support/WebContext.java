package org.folio.rest.support;

import io.vertx.ext.web.RoutingContext;
import java.net.MalformedURLException;
import java.net.URL;

public class WebContext implements Context {

  private static final String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private static final String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  private static final String OKAPI_URL_HEADER = "X-Okapi-Url";
  private static final String OKAPI_USER_ID_HEADER = "X-Okapi-User-Id";
  private static final String OKAPI_REQUEST_ID = "X-Okapi-Request-Id";

  private final RoutingContext routingContext;

  public WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext;
  }

  @Override
  public String getTenantId() {
    return getHeader(OKAPI_TENANT_HEADER, "");
  }

  @Override
  public String getToken() {
    return getHeader(OKAPI_TOKEN_HEADER, "");
  }

  @Override
  public String getOkapiLocation() {
    return getHeader(OKAPI_URL_HEADER, "");
  }

  @Override
  public String getUserId() {
    return getHeader(OKAPI_USER_ID_HEADER, "");
  }

  public String getRequestId() {
    return getHeader(OKAPI_REQUEST_ID);
  }

  private String getHeader(String header) {
    return routingContext.request().getHeader(header);
  }

  private String getHeader(String header, String defaultValue) {
    return hasHeader(header) ? getHeader(header) : defaultValue;
  }

  private boolean hasHeader(String header) {
    return routingContext.request().headers().contains(header);
  }

  public URL absoluteUrl(String path) throws MalformedURLException {
    URL currentRequestUrl = new URL(routingContext.request().absoluteURI());

    //It would seem Okapi preserves headers from the original request,
    // so there is no need to use X-Okapi-Url for this?
    return new URL(currentRequestUrl.getProtocol(), currentRequestUrl.getHost(),
      currentRequestUrl.getPort(), path);
  }

  public Integer getIntegerParameter(String name, Integer defaultValue) {
    String value = routingContext.request().getParam(name);

    return value != null ? Integer.parseInt(value) : defaultValue;
  }

  public String getStringParameter(String name, String defaultValue) {
    String value = routingContext.request().getParam(name);

    return value != null ? value : defaultValue;
  }
}
