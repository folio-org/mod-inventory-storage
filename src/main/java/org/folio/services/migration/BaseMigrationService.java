package org.folio.services.migration;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.dbschema.ObjectMapperTool.readValue;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.Versioned;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClientFuturized;

public abstract class BaseMigrationService {
  private static final Logger log = getLogger(BaseMigrationService.class);
  private final Versioned version;
  private final PostgresClientFuturized postgresClient;

  protected BaseMigrationService(String fromVersion, PostgresClientFuturized client) {
    this.version = versioned(fromVersion);
    this.postgresClient = client;
  }

  private static Versioned versioned(String version) {
    var versioned = new Versioned() { };
    versioned.setFromModuleVersion(version);
    return versioned;
  }

  public boolean shouldExecuteMigration(TenantAttributes tenantAttributes) {
    return isNotBlank(tenantAttributes.getModuleFrom())
      && version.isNewForThisInstall(tenantAttributes.getModuleFrom());
  }

  public Future<Void> runMigration() {
    log.info("Starting migration for class [class={}]", getClass());
    return postgresClient.getClient().withTrans(conn -> openStream(conn)
        .compose(rows -> handleUpdate(rows, conn)
          .onSuccess(records -> log.info("Migration for the class has been "
                                         + "completed [class={}, recordsProcessed={}]", getClass(), records))
          .onFailure(error -> log.error("Unable to complete migration for class [class={}]",
            getClass(), error))))
      .mapEmpty();
  }

  public abstract String getMigrationName();

  protected abstract Future<RowStream<Row>> openStream(Conn connection);

  protected abstract Future<Integer> updateBatch(List<Row> batch, Conn connection);

  protected <T> T rowToClass(Row row, Class<T> clazz) {
    return readValue(row.getValue("jsonb").toString(), clazz);
  }

  private Future<Integer> handleUpdate(RowStream<Row> stream, Conn connection) {
    var batchStream = new BatchedReadStream<>(stream);
    var promise = Promise.<Integer>promise();
    var recordsUpdated = new AtomicInteger(0);

    batchStream
      .endHandler(notUsed -> promise.tryComplete(recordsUpdated.get()))
      .exceptionHandler(promise::tryFail)
      .handler(rows -> {
        // Pause stream, so that updates is executed in sequence
        batchStream.pause();
        updateBatch(rows, connection)
          .onSuccess(updatedNumber -> {
            log.info("Batch of records has been processed [recordsProcessed={}, class={}]",
              recordsUpdated.addAndGet(updatedNumber), getClass());

            batchStream.resume();
          })
          .onFailure(error -> {
            log.error("Unable to perform update for a batch of records [class={}]",
              getClass(), error);
            promise.tryFail(error);
          });
      });

    return promise.future().onComplete(notUsed -> stream.close());
  }
}
