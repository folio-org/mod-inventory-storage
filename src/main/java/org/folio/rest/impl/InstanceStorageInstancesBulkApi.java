package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.InstanceBulkRequest;
import org.folio.rest.jaxrs.resource.InstanceStorageInstancesBulk;

public class InstanceStorageInstancesBulkApi implements InstanceStorageInstancesBulk {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void postInstanceStorageInstancesBulk(InstanceBulkRequest bulkRequest, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

  }

}
