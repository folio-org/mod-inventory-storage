package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.resource.interfaces.PostDeployVerticle;

/**
 * Replaces the class of the same name in folio-custom-fields, which RMB calls after the module's Spring context
 * is built and which would put a context of the library's configuration alone in its place (FCFIELDS-42). The
 * module's {@code ApplicationConfig} imports the library's configuration instead. This class takes effect because
 * the module's classes come before the library's on the classpath and in the fat jar. Remove it once FCFIELDS-42
 * has removed the library's context.
 */
public class CustomFieldsPostDeployVerticle implements PostDeployVerticle {

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    handler.handle(Future.succeededFuture(true));
  }
}
