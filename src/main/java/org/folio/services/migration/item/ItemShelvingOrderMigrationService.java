package org.folio.services.migration.item;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.dbschema.ObjectMapperTool.readValue;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.folio.services.item.ItemService;
import org.folio.services.migration.BaseMigrationService;
import org.folio.services.migration.BatchedReadStream;

public class ItemShelvingOrderMigrationService extends BaseMigrationService {
  private static final Logger log = getLogger(ItemShelvingOrderMigrationService.class);
  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
    + "jsonb->>'effectiveShelvingOrder' IS NULL";

  private final PostgresClientFuturized postgresClient;
  private final ItemService itemService;

  public ItemShelvingOrderMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new ItemService(context, okapiHeaders));
  }

  public ItemShelvingOrderMigrationService(
    PostgresClientFuturized postgresClient, ItemService itemService) {

    super("20.2.0");
    this.postgresClient = postgresClient;
    this.itemService = itemService;
  }

  @Override
  public Future<Void> runMigration() {
    log.info("Starting item shelving order migration... ");

    return postgresClient.startTx()
      .compose(con -> postgresClient.selectStream(con, selectSql())
        .compose(this::handleUpdate)
        .onSuccess(records -> log.info(
          "Shelving order migration has been completed [recordsProcessed={}]", records))
        .onFailure(error -> log.error("Unable to complete shelving order migration", error))
        .onComplete(result -> postgresClient.endTx(con)))
      .mapEmpty();
  }

  private Future<Integer> handleUpdate(RowStream<Row> stream) {
    var batchStream = new BatchedReadStream<>(stream);
    var promise = Promise.<Integer>promise();
    var recordsUpdated = new AtomicInteger(0);

    batchStream
      .endHandler(notUsed -> promise.tryComplete(recordsUpdated.get()))
      .exceptionHandler(promise::tryFail)
      .handler(rows -> {
        var items = rows.stream()
          .map(this::rowToItem)
          .map(EffectiveCallNumberComponentsUtil::calculateAndSetEffectiveShelvingOrder)
          .collect(Collectors.toList());

        // Pause stream, so that updates is executed in sequence
        batchStream.pause();
        itemService.updateItems(items)
          .onSuccess(notUsed -> {
            log.info("Shelving order is populated for [{}] items so far",
              recordsUpdated.addAndGet(items.size()));

            batchStream.resume();
          })
          .onFailure(error -> {
            log.error("Unable to perform update for items", error);
            promise.tryFail(error);
          });
      });

    return promise.future().onComplete(notUsed -> stream.close());
  }

  private Item rowToItem(Row row) {
    return readValue(row.getValue("jsonb").toString(), Item.class);
  }

  private String selectSql() {
    return String.format(SELECT_SQL, postgresClient.getFullTableName("item"));
  }
}
