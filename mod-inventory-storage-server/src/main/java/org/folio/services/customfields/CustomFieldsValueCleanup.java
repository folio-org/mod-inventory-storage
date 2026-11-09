package org.folio.services.customfields;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.CustomFieldsCleanupRepository;
import org.folio.persist.CustomFieldsCleanupRepository.QueuedRemoval;
import org.folio.services.record.RecordServiceFactoryImpl;

/**
 * Removes the values of deleted custom fields queued in {@code custom_fields_cleanup}. A pass
 * starts after each field delete and on each {@code _timer} call. The timer's passes pick up the rows that
 * other passes leave behind: after a restart, after a failed row or pass, or a row queued after the running
 * pass last read the queue.
 */
public final class CustomFieldsValueCleanup {

  public static final int READ_SIZE = 1000;

  private static final Logger log = LogManager.getLogger(CustomFieldsValueCleanup.class);

  private CustomFieldsValueCleanup() {
  }

  /**
   * Starts a pass over the tenant's queue. It starts inside the future chain, so an exception thrown before
   * {@code processQueue} returns a future (for example {@code PostgresClient.getInstance} for tenant
   * {@code public}) is logged instead of thrown to the caller.
   */
  public static void kick(Vertx vertx, String tenantId) {
    Future.<Void>succeededFuture()
      .compose(x -> processQueue(vertx, tenantId))
      .onFailure(e -> log.error("kick:: custom field value cleanup failed [tenant={}]", tenantId, e));
  }

  private static Future<Void> processQueue(Vertx vertx, String tenantId) {
    return CustomFieldsCleanupRepository.withConnection(vertx, tenantId, CustomFieldsValueCleanup::processQueueLocked);
  }

  /**
   * Runs the pass under a session advisory lock on the tenant, held on the repository's connection across
   * the pass's transactions, so one pass per tenant runs across all module instances. A pass that finds
   * the lock held leaves the queue to the holder, which reads the queue again before it ends.
   *
   * <p>The pass also commits asynchronously, since it commits once per record and waiting for each commit
   * to be flushed would make it several times slower. A crash can undo the latest removals, but only
   * together with everything committed after them, including the deletion of their queue row, so the
   * next pass removes them again. The pass resets the commit mode and releases the lock when it ends, before
   * the connection returns to the pool.
   */
  private static Future<Void> processQueueLocked(CustomFieldsCleanupRepository repository) {
    return repository.tryLock()
      .compose(locked -> {
        if (!Boolean.TRUE.equals(locked)) {
          log.info("processQueueLocked:: custom field value cleanup already runs [schema={}]", repository.schema());
          return Future.succeededFuture();
        }
        return repository.commitAsynchronously()
          .compose(x -> processRows(repository))
          .eventually(() -> resetCommitMode(repository))
          .eventually(() -> unlock(repository));
      });
  }

  private static Future<Void> resetCommitMode(CustomFieldsCleanupRepository repository) {
    return repository.resetCommitMode()
      .recover(e -> {
        log.warn("resetCommitMode:: resetting synchronous_commit failed [schema={}]", repository.schema(), e);
        return Future.succeededFuture();
      });
  }

  private static Future<Void> unlock(CustomFieldsCleanupRepository repository) {
    return repository.unlock()
      .recover(e -> {
        log.warn("unlock:: releasing the custom field value cleanup lock failed [schema={}]", repository.schema(), e);
        return Future.succeededFuture();
      });
  }

  /**
   * Processes the queue's due rows in order and reads it again until none is due, so rows queued during the
   * pass are processed too. A failed row is not due again until its wait has passed, so it does not keep the
   * pass going.
   */
  private static Future<Void> processRows(CustomFieldsCleanupRepository repository) {
    return whileMore(() -> repository.readQueue()
      .compose(queue -> {
        Future<Void> result = Future.succeededFuture();
        for (var removal : queue) {
          result = result.compose(x -> processRow(repository, removal)
            .recover(e -> recordFailure(repository, removal.id(), e)));
        }
        return result.map(!queue.isEmpty());
      }));
  }

  private static Future<Void> processRow(CustomFieldsCleanupRepository repository, QueuedRemoval removal) {
    var table = RecordServiceFactoryImpl.ENTITY_TYPE_TABLES.get(removal.entityType());
    if (table == null) {
      log.warn("processRow:: discarding cleanup row with unsupported entityType [{}]", removal.entityType());
      return repository.deleteQueueRow(removal.id(), removal.refId());
    }
    return removeFieldValues(repository, table, removal.refId())
      .compose(x -> repository.deleteQueueRow(removal.id(), removal.refId()));
  }

  /** Keeps the row for a later pass with the cause; a failure to record it ends the pass. */
  private static Future<Void> recordFailure(CustomFieldsCleanupRepository repository, UUID id, Throwable cause) {
    log.error("recordFailure:: removing custom field values failed, kept for a later pass [id={}]", id, cause);
    return repository.recordFailure(id, String.valueOf(cause.getMessage()));
  }

  /**
   * Updates each record in a transaction of its own, as a single-item write does, so the removal locks the
   * rows that the item triggers lock in the same order as any other item write and adds no deadlock that
   * two item writes could not run into. The records are read in batches.
   *
   * <p>A record whose update fails keeps the value and is left out of the following batches, so it does not
   * hold up the records after it. The removal then fails, which keeps the field queued for a later pass.
   */
  private static Future<Void> removeFieldValues(CustomFieldsCleanupRepository repository, String table,
                                                String refId) {
    log.info("removeFieldValues:: removing values of custom field [refId={}]", refId);
    var records = new AtomicLong();
    var batches = new AtomicInteger();
    Map<UUID, Throwable> failed = new LinkedHashMap<>();
    var start = System.nanoTime();
    return whileMore(() -> repository.readHolders(table, refId, READ_SIZE, failed.keySet())
      .compose(ids -> {
        if (ids.isEmpty()) {
          return Future.succeededFuture(false);
        }
        batches.incrementAndGet();
        return removeValueOneByOne(repository, table, refId, ids, records, failed).map(true);
      }))
      .compose(v -> endRemoval(refId, records.get(), batches.get(), failed, start));
  }

  private static Future<Void> endRemoval(String refId, long records, int batches, Map<UUID, Throwable> failed,
                                         long start) {
    log.info("endRemoval:: removed values of custom field [refId={}, records={}, failedRecords={}, batches={}, "
        + "durationMs={}]",
      refId, records, failed.size(), batches, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    if (failed.isEmpty()) {
      return Future.succeededFuture();
    }
    var first = failed.entrySet().iterator().next();
    return Future.failedFuture(new IllegalStateException(failed.size() + " of the records holding the value could not "
      + "be updated, first [id=" + first.getKey() + "]: " + first.getValue().getMessage(), first.getValue()));
  }

  private static Future<Void> removeValueOneByOne(CustomFieldsCleanupRepository repository, String table,
                                                  String refId, List<UUID> ids, AtomicLong records,
                                                  Map<UUID, Throwable> failed) {
    var remaining = ids.iterator();
    return whileMore(() -> {
      if (!remaining.hasNext()) {
        return Future.succeededFuture(false);
      }
      var id = remaining.next();
      return repository.removeValue(table, refId, id)
        .map(removed -> {
          records.addAndGet(removed);
          return true;
        })
        .recover(e -> {
          var message = e.getMessage();
          log.warn("removeValueOneByOne:: removing the custom field value failed, kept for a later pass "
            + "[refId={}, id={}]: {}", refId, id, message);
          failed.put(id, e);
          return Future.succeededFuture(true);
        });
    });
  }

  /**
   * Runs the step until it yields false. Each step starts the next from its callback, and the last one
   * completes the returned future. Composing the steps instead would chain one future per step, and
   * completing that chain on long runs would overflow the stack and leave the future incomplete.
   */
  static Future<Void> whileMore(Supplier<Future<Boolean>> step) {
    Promise<Void> done = Promise.promise();
    runStep(step, done);
    return done.future();
  }

  private static void runStep(Supplier<Future<Boolean>> step, Promise<Void> done) {
    step.get().onComplete(more -> {
      if (more.failed()) {
        done.fail(more.cause());
      } else if (Boolean.TRUE.equals(more.result())) {
        runStep(step, done);
      } else {
        done.complete();
      }
    });
  }
}
