package org.folio.rest.impl;

import java.io.File;
import io.vertx.core.json.JsonObject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);

  private HttpClient httpClient;

  private static List<InputStream> getStreamsfromClassPathDir(String directoryName) throws URISyntaxException, IOException {
    List<InputStream> streams = new LinkedList<>();

    URL url = Thread.currentThread().getContextClassLoader().getResource(directoryName);
    if (url != null) {
      if (url.getProtocol().equals("file")) {
        File file = Paths.get(url.toURI()).toFile();
        if (file != null) {
          File[] files = file.listFiles();
          if (files != null) {
            for (File filename : files) {
              streams.add(new FileInputStream(filename));
            }
          }
        }
      } else if (url.getProtocol().equals("jar")) {
        String dirname = directoryName + "/";
        String path = url.getPath();
        String jarPath = path.substring(5, path.indexOf('!'));
        try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()))) {
          Enumeration<JarEntry> entries = jar.entries();
          while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(dirname) && !dirname.equals(name)) {
              streams.add(Thread.currentThread().getContextClassLoader().getResourceAsStream(name));
            }
          }
        }
      }
    }
    return streams;
  }

  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");

    httpClient = cntxt.owner().createHttpClient();
    super.postTenant(ta, headers, res -> {
      boolean loadReference = false;
      List<Parameter> parameters = ta.getParameters();
      for (Parameter parameter : parameters) {
        if ("loadReference".equals(parameter.getKey())
          && "true".equals(parameter.getValue())) {
          loadReference = true;
        }
      }
      log.info("postTenant loadReference=" + loadReference);
      if (res.succeeded() && loadReference) {
        loadReferenceData(headers, hndlr);
      } else {
        hndlr.handle(res);
      }
    }, cntxt);
  }

  private void loadReferenceData(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr) {
    String[] endPoints = new String[]{
      "material-types",
      "loan-types",
      "location-units/institutions",
      "location-units/campuses",
      "location-units/libraries",
      "locations",
      "identifier-types",
      "contributor-types",
      "service-points",
      "instance-relationship-types",
      "contributor-name-types",
      "instance-types",
      "instance-formats",
      "classification-types",
      "instance-statuses",
      "statistical-code-types", "statistical-codes",
      "modes-of-issuance",
      "alternative-title-types",
      "electronic-access-relationships",
      "ill-policies",
      "holdings-types",
      "call-number-types",
      "holdings-note-types",
      "item-note-types"
    };
    List<String> list = Arrays.asList(endPoints);

    loadRef(headers, list.iterator(), res -> {
      if (res.failed()) {
        hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond500WithTextPlain(res.cause().getLocalizedMessage())));
      } else {
        hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond201WithApplicationJson("")));
      }
    });
  }

  private void loadRef(Map<String, String> headers, Iterator<String> it, Handler<AsyncResult<Response>> res) {
    if (!it.hasNext()) {
      res.handle(Future.succeededFuture());
    } else {
      String endPoint = it.next();
      loadRef(headers, endPoint, x -> {
        if (x.failed()) {
          res.handle(Future.failedFuture(x.cause()));
        } else {
          loadRef(headers, it, res);
        }
      });
    }
  }

  private void endWithXHeaders(HttpClientRequest req, Map<String, String> headers, String json) {
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

  private void loadRef(Map<String, String> headers, String endPoint, Handler<AsyncResult<Void>> res) {
    log.info("loadRef " + endPoint + " begin");
    String okapiUrl = headers.get("X-Okapi-Url-to");
    if (okapiUrl == null) {
      log.warn("No X-Okapi-Url-to. Headers: " + headers);
      res.handle(Future.failedFuture("No X-Okapi-Url-to header"));
      return;
    }
    log.info("loadRef....................");
    List<String> jsonList = new LinkedList<>();
    try {
      List<InputStream> streams = getStreamsfromClassPathDir("ref-data/" + endPoint);
      for (InputStream stream : streams) {
        jsonList.add(IOUtils.toString(stream, "UTF-8"));
      }
    } catch (URISyntaxException ex) {
      res.handle(Future.failedFuture("URISyntaxException for path " + endPoint + " ex=" + ex.getLocalizedMessage()));
      return;

    } catch (IOException ex) {
      res.handle(Future.failedFuture("IOException for path " + endPoint + " ex=" + ex.getLocalizedMessage()));
      return;
    }
    final String endPointUrl = okapiUrl + "/" + endPoint;
    List<Future> futures = new LinkedList<>();
    for (String json : jsonList) {
      Future f = Future.future();
      futures.add(f);

      JsonObject jsonObject = new JsonObject(json);
      String id = jsonObject.getString("id");
      if (id == null) {
        res.handle(Future.failedFuture("Missing id for " + json));
        return;
      }
      HttpClientRequest reqPut = httpClient.putAbs(endPointUrl + "/" + id, resPut -> {
        if (resPut.statusCode() == 404) {
          HttpClientRequest reqPost = httpClient.postAbs(endPointUrl, resPost -> {
            if (resPost.statusCode() == 201) {
              f.handle(Future.succeededFuture());
            } else {
              f.handle(Future.failedFuture("POST " + endPointUrl + " returned status " + resPost.statusCode()));
            }
          });
          endWithXHeaders(reqPost, headers, json);
        } else if (resPut.statusCode() == 200) {
          f.handle(Future.succeededFuture());
        } else {
          f.handle(Future.failedFuture("PUT " + endPointUrl + "/" + id + " returned status " + resPut.statusCode()));
        }
      });
      endWithXHeaders(reqPut, headers, json);
    }
    CompositeFuture.all(futures).setHandler(x -> {
      log.info("loadRef " + endPoint + " done. success=" + x.succeeded());
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
