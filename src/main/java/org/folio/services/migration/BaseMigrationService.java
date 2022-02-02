package org.folio.services.migration;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.dbschema.ObjectMapperTool.readValue;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.Versioned;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;

public abstract class BaseMigrationService {
  private static final Logger log = getLogger(BaseMigrationService.class);
  private final Versioned version;
  private final PostgresClientFuturized postgresClient;
  private Set<String> idsForMigration = new HashSet<>();

  protected BaseMigrationService(String fromVersion, PostgresClientFuturized client) {
    this.version = versioned(fromVersion);
    this.postgresClient = client;
  }

  public boolean shouldExecuteMigration(TenantAttributes tenantAttributes) {
    return isNotBlank(tenantAttributes.getModuleFrom())
      && version.isNewForThisInstall(tenantAttributes.getModuleFrom());
  }

  public Future<Void> runMigration() {
    log.info("Starting migration for class [class={}]", getClass());

    return postgresClient.startTx()
      .compose(con -> openStream(con)
        .compose(this::handleUpdate)
        .onSuccess(records -> log.info("Migration for the class has been " +
          "completed [class={}, recordsProcessed={}]", getClass(), records))
        .onFailure(error -> log.error("Unable to complete migration for class [class={}]",
          getClass(), error))
        .onComplete(result -> postgresClient.endTx(con)))
      .mapEmpty();
  }

  public Future<Void> runMigrationForIds(Set<String> ids){
    idsForMigration = ids;
    return runMigration();
  }

  protected Set<String> getIdsForMigration(){
    return idsForMigration;
  }

  protected abstract Future<RowStream<Row>> openStream(SQLConnection connection);

  protected abstract Future<Integer> updateBatch(List<Row> batch);

  public abstract String getMigrationName();

  private Future<Integer> handleUpdate(RowStream<Row> stream) {
    var batchStream = new BatchedReadStream<>(stream);
    var promise = Promise.<Integer>promise();
    var recordsUpdated = new AtomicInteger(0);

    batchStream
      .endHandler(notUsed -> promise.tryComplete(recordsUpdated.get()))
      .exceptionHandler(promise::tryFail)
      .handler(rows -> {
        // Pause stream, so that updates is executed in sequence
        batchStream.pause();
        updateBatch(rows)
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

  protected <T> T rowToClass(Row row, Class<T> clazz) {
    return readValue(row.getValue("jsonb").toString(), clazz);
  }

  private static Versioned versioned(String version) {
    var versioned = new Versioned() {};
    versioned.setFromModuleVersion(version);
    return versioned;
  }
}
