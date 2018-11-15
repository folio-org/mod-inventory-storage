package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.File;
import io.vertx.core.Future;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.folio.rest.jaxrs.model.TenantAttributes;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);

  private HttpClient httpClient;

  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");

    httpClient = cntxt.owner().createHttpClient();
    super.postTenant(ta, headers, res -> {
      if (res.succeeded()) {
        loadReferenceData(ta, headers, hndlr);
      } else {
        hndlr.handle(res);
      }
    }, cntxt);
  }

  private void loadReferenceData(TenantAttributes ta, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr) {
    ClassLoader classLoader = TenantRefAPI.class.getClassLoader();

    loadRef(ta, headers, classLoader, "material-types", res -> {
      if (res.failed()) {
        hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond500WithTextPlain(res.cause().getLocalizedMessage())));
      } else {
        hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond201WithApplicationJson("")));

      }
    });
  }

  private void loadRef(TenantAttributes ta, Map<String, String> headers, ClassLoader classLoader, String endPoint, Handler<AsyncResult<Void>> res) {
    log.info("loadRef " + endPoint);
    String okapiUrl = headers.get("X-Okapi-Url");
    if (okapiUrl == null) {
      log.warn("No X-Okapi-Url. Headers: " + headers);
      res.handle(Future.succeededFuture());
      return;
    }
    final String endPointUrl = okapiUrl + "/" + endPoint;
    URL url = classLoader.getResource("reference-data/" + endPoint);
    String path = url.getPath();
    List<String> jsonList = new LinkedList<>();
    for (File f : new File(path).listFiles()) {
      if (f.isFile() && f.getName().endsWith(".json")) {
        String json = null;
        try {
          jsonList.add(FileUtils.readFileToString(f, "UTF-8"));
        } catch (IOException ex) {
          res.handle(io.vertx.core.Future.failedFuture(ex.getLocalizedMessage()));
          return;
        }
      }
    }
    List<Future> futures = new LinkedList<>();
    for (String json : jsonList) {
      Future f = Future.future();
      futures.add(f);

      HttpClientRequest req = httpClient.postAbs(endPointUrl, x -> {
        if (x.statusCode() >= 200 && x.statusCode() <= 299) {
          f.handle(Future.succeededFuture());
        } else {
          f.handle(Future.failedFuture("POST " + endPointUrl + " returned status " + x.statusCode()));
        }
      });
      for (Map.Entry<String, String> e : headers.entrySet()) {
        String k = e.getKey();
        if (k.startsWith("X-") || k.startsWith("x-")) {
          req.headers().add(k, e.getValue());
        }
      }
      req.headers().add("Content-Type", "application/json");
      req.headers().add("Accept", "application/json, text/plain");
      req.end(json);
    }
    CompositeFuture.all(futures).setHandler(x -> {
      if (x.failed()) {
        res.handle(Future.failedFuture(x.cause().getLocalizedMessage()));
      } else {
        res.handle(Future.succeededFuture());
      }
    });
  }

  @Override
  public void getTenant(Map<String, String> map, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("getTenant");
    super.getTenant(map, hndlr, cntxt);
  }

  @Override
  public void deleteTenant(Map<String, String> map, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenant(map, hndlr, cntxt);
  }
}
