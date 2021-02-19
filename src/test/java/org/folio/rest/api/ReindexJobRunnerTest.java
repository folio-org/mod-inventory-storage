package org.folio.rest.api;

import static io.vertx.core.Future.succeededFuture;
import static org.awaitility.Awaitility.await;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.services.reindex.ReindexJobRunner;
import org.junit.Test;

public class ReindexJobRunnerTest extends TestBaseWithInventoryUtil {
  private final ReindexJobRepository repository = getRepository();

  @Test
  public void canCancelReindex() {
    var rowStream = new TestRowStream();
    var reindexJob = reindexJob();
    var postgresClientFuturized = spy(getPostgresClientFuturized());

    doReturn(succeededFuture(rowStream))
      .when(postgresClientFuturized).selectStream(any(), anyString());

    var runner = new ReindexJobRunner(getContext(), okapiHeaders(),
      postgresClientFuturized, reindexJob);

    get(repository.save(reindexJob.getId(), reindexJob).toCompletionStage()
      .toCompletableFuture());

    runner.startReindex();

    instanceReindex.cancelReindexJob(reindexJob.getId());

    await().until(() -> instanceReindex.getReindexJob(reindexJob.getId())
      .getJobStatus() == CANCELLED);

    var job = instanceReindex.getReindexJob(reindexJob.getId());

    assertThat(job.getJobStatus(), is(CANCELLED));
    assertThat(job.getPublished(), greaterThanOrEqualTo(1000));
  }

  private static ReindexJob reindexJob() {
    return new ReindexJob()
      .withJobStatus(IN_PROGRESS)
      .withId(UUID.randomUUID().toString())
      .withSubmittedDate(new Date());
  }

  private static Map<String, String> okapiHeaders() {
    return Map.of(TENANT.toLowerCase(), TENANT_ID);
  }

  private static Context getContext() {
    return StorageTestSuite.getVertx().getOrCreateContext();
  }

  private PostgresClientFuturized getPostgresClientFuturized() {
    var postgresClient = postgresClient(getContext(), okapiHeaders());
    return new PostgresClientFuturized(postgresClient);
  }

  private ReindexJobRepository getRepository() {
    return new ReindexJobRepository(getContext(), okapiHeaders());
  }

  private static class TestRowStream implements RowStream<Row> {
    private Handler<Throwable> errorHandler;
    private Handler<Void> endHandler;
    private volatile boolean paused;
    private volatile boolean closed;

    @Override
    public RowStream<Row> exceptionHandler(Handler<Throwable> handler) {
      this.errorHandler = handler;
      return this;
    }

    @Override
    public RowStream<Row> handler(Handler<Row> handler) {
      new Thread(() -> {
        try {
          for (int i = 0; i < Integer.MAX_VALUE; i++) {
            if (closed) {
              break;
            }

            if (!paused) {
              var row = mock(Row.class);
              when(row.getUUID("id")).thenReturn(UUID.randomUUID());

              handler.handle(row);
            } else {
              synchronized (this) {
                wait();
              }
            }
          }
        } catch (Exception ex) {
          errorHandler.handle(ex);
        }

        endHandler.handle(null);
      }).start();

      return this;
    }

    @Override
    public RowStream<Row> pause() {
      paused = true;
      return this;
    }

    @Override
    public RowStream<Row> resume() {
      paused = false;
      synchronized (this) {
        notifyAll();
      }
      return this;
    }

    @Override
    public RowStream<Row> endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }

    @Override
    public RowStream<Row> fetch(long l) {
      return this;
    }

    @Override
    public Future<Void> close() {
      Promise<Void> promise = Promise.promise();

      close(promise);

      return promise.future();
    }

    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
      closed = true;
      synchronized (this) {
        notifyAll();
      }
      completionHandler.handle(succeededFuture());
    }
  }
}
