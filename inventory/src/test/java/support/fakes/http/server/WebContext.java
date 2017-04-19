package support.fakes.http.server;

import io.vertx.ext.web.RoutingContext;

import java.net.MalformedURLException;
import java.net.URL;

public class WebContext {
  private final RoutingContext routingContext;

  public WebContext(RoutingContext routingContext) {
    this.routingContext = routingContext;
  }

  public String getTenantId() {
    return getHeader("X-Okapi-Tenant", "");
  }

  public String getOkapiToken() {
    return getHeader("X-Okapi-Token", "");
  }

  public String getOkapiLocation() {
    return getHeader("X-Okapi-Url", "");
  }

  public String getHeader(String header) {
    return routingContext.request().getHeader(header);
  }

  public String getHeader(String header, String defaultValue) {
    return hasHeader(header) ? getHeader(header) : defaultValue;
  }

  public boolean hasHeader(String header) {
    return routingContext.request().headers().contains(header);
  }

  public Integer getIntegerParameter(String name, Integer defaultValue) {
    String value = routingContext.request().getParam(name);

    return value != null ? Integer.parseInt(value) : defaultValue;
  }

  public String getStringParameter(String name, String defaultValue) {
    String value = routingContext.request().getParam(name);

    return value != null ? value : defaultValue;
  }

  public URL getOkapiBasedUrl(String path)
    throws MalformedURLException {

    URL currentRequestUrl = new URL(getOkapiLocation());

    return new URL(currentRequestUrl.getProtocol(), currentRequestUrl.getHost(),
      currentRequestUrl.getPort(), path);
  }
}
