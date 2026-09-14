package org.folio.services.reindex;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.CommonDomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReindexJobRunnerTest {

  private static final String JOB_ID = "job-1";
  private static final String TENANT_ID = "diku";

  @SuppressWarnings("unchecked")
  private final PostgresClient postgresClient = mock(PostgresClient.class);
  private final ReindexJobRepository reindexJobRepository = mock(ReindexJobRepository.class);
  @SuppressWarnings("unchecked")
  private final CommonDomainEventPublisher<Instance> instanceEventPublisher = mock(CommonDomainEventPublisher.class);
  private final ReindexJobRunner runner = new ReindexJobRunner(postgresClient, reindexJobRepository,
    Vertx.vertx().getOrCreateContext(), instanceEventPublisher, TENANT_ID);

  @Test
  @DisplayName("should not log job details when the record count is not a multiple of the log interval")
  void shouldNotLogJobDetails_whenRecordCountIsNotMultipleOfLogInterval() {
    assertThat(shouldLogJobDetails(999L)).isFalse();
  }

  @Test
  @DisplayName("should log job details when the record count is a multiple of the log interval")
  void shouldLogJobDetails_whenRecordCountIsMultipleOfLogInterval() {
    assertThat(shouldLogJobDetails(1000L)).isTrue();
  }

  @Test
  @DisplayName("should log job details when the record count is zero")
  void shouldLogJobDetails_whenRecordCountIsZero() {
    assertThat(shouldLogJobDetails(0L)).isTrue();
  }

  @Test
  @DisplayName("should skip persisting job details when the record count is not a log interval boundary")
  void shouldSkipPersistingJobDetails_whenNotLogIntervalBoundary() {
    var context = reindexContext(new ReindexJob().withId(JOB_ID));

    var result = logJobDetails(1L, context);

    assertThat(result.result()).isNotNull();
    verify(reindexJobRepository, org.mockito.Mockito.never()).fetchAndUpdate(any(), any());
  }

  @Test
  @DisplayName("should persist job details and continue when the job has not been cancelled")
  void shouldPersistJobDetailsAndContinue_whenJobHasNotBeenCancelled() {
    var context = reindexContext(new ReindexJob().withId(JOB_ID));
    var updatedJob = new ReindexJob().withId(JOB_ID).withJobStatus(ReindexJob.JobStatus.IN_PROGRESS);
    when(reindexJobRepository.fetchAndUpdate(eq(JOB_ID), any())).thenReturn(succeededFuture(updatedJob));

    var result = logJobDetails(1000L, context);

    assertThat(result.result()).isSameAs(updatedJob);
  }

  @Test
  @DisplayName("should fail when the job has been cancelled while logging details")
  void shouldFail_whenJobHasBeenCancelledWhileLoggingDetails() {
    var context = reindexContext(new ReindexJob().withId(JOB_ID));
    var cancelledJob = new ReindexJob().withId(JOB_ID).withJobStatus(ReindexJob.JobStatus.PENDING_CANCEL);
    when(reindexJobRepository.fetchAndUpdate(eq(JOB_ID), any())).thenReturn(succeededFuture(cancelledJob));

    var result = logJobDetails(1000L, context);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("should mark the job cancelled when a failed job was pending cancellation")
  void shouldMarkJobCancelled_whenFailedJobWasPendingCancellation() {
    var context = reindexContext(new ReindexJob().withId(JOB_ID));
    var pendingCancel = new ReindexJob().withId(JOB_ID).withJobStatus(ReindexJob.JobStatus.PENDING_CANCEL);
    when(reindexJobRepository.fetchAndUpdate(eq(JOB_ID), any())).thenAnswer(invocation -> {
      UnaryOperator<ReindexJob> updater = invocation.getArgument(1);
      return succeededFuture(updater.apply(pendingCancel));
    });

    logFailedJob(context);

    verify(reindexJobRepository).fetchAndUpdate(eq(JOB_ID), any());
  }

  @Test
  @DisplayName("should mark the job failed when a failed job was not pending cancellation")
  void shouldMarkJobFailed_whenFailedJobWasNotPendingCancellation() {
    var context = reindexContext(new ReindexJob().withId(JOB_ID));
    var inProgress = new ReindexJob().withId(JOB_ID).withJobStatus(ReindexJob.JobStatus.IN_PROGRESS);
    @SuppressWarnings("unchecked")
    var updatedJobCaptor = org.mockito.ArgumentCaptor.forClass(UnaryOperator.class);
    when(reindexJobRepository.fetchAndUpdate(eq(JOB_ID), updatedJobCaptor.capture()))
      .thenReturn(succeededFuture(inProgress));

    logFailedJob(context);

    var result = (ReindexJob) updatedJobCaptor.getValue().apply(inProgress);
    assertThat(result.getJobStatus()).isEqualTo(ReindexJob.JobStatus.ID_PUBLISHING_FAILED);
  }

  @Test
  @DisplayName("should mark the reindex completed with the published count")
  void shouldMarkReindexCompleted_withPublishedCount() {
    var context = reindexContext(new ReindexJob().withId(JOB_ID));
    var updatedJob = new ReindexJob().withId(JOB_ID);
    @SuppressWarnings("unchecked")
    var updaterCaptor = org.mockito.ArgumentCaptor.forClass(UnaryOperator.class);
    when(reindexJobRepository.fetchAndUpdate(eq(JOB_ID), updaterCaptor.capture()))
      .thenReturn(succeededFuture(updatedJob));

    logReindexCompleted(42L, context);

    var result = (ReindexJob) updaterCaptor.getValue().apply(new ReindexJob());
    assertThat(result.getPublished()).isEqualTo(42);
    assertThat(result.getJobStatus()).isEqualTo(ReindexJob.JobStatus.IDS_PUBLISHED);
  }

  @Test
  @DisplayName("should build a producer record keyed by the row id and tagged with the reindex job id")
  void shouldBuildProducerRecord_keyedByRowIdAndTaggedWithReindexJobId() {
    var context = reindexContext(new ReindexJob().withId(JOB_ID));
    var rowId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    var row = mock(Row.class);
    when(row.getUUID("id")).thenReturn(rowId);

    var producerRecordBuilder = rowToInstanceProducerRecord(row, context);
    var producerRecord = producerRecordBuilder.build();

    assertThat(producerRecord.key()).isEqualTo(rowId.toString());
    assertThat(producerRecord.headers())
      .filteredOn(header -> header.key().equals(ReindexJobRunner.REINDEX_JOB_ID_HEADER))
      .extracting(header -> header.value().toString())
      .containsExactly(JOB_ID);
  }

  private Object reindexContext(ReindexJob job) {
    try {
      Class<?> contextClass = Class.forName("org.folio.services.reindex.ReindexJobRunner$ReindexContext");
      Constructor<?> constructor = contextClass.getDeclaredConstructor(ReindexJob.class);
      constructor.setAccessible(true);
      return constructor.newInstance(job);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private io.vertx.core.Future<ReindexJob> logJobDetails(Long records, Object context) {
    return invokePrivate("logJobDetails", new Class<?>[]{Long.class, context.getClass()}, records, context);
  }

  private void logFailedJob(Object context) {
    invokePrivate("logFailedJob", new Class<?>[]{context.getClass()}, context);
  }

  private void logReindexCompleted(Long recordsPublished, Object context) {
    invokePrivate("logReindexCompleted", new Class<?>[]{Long.class, context.getClass()}, recordsPublished, context);
  }

  private org.folio.kafka.services.KafkaProducerRecordBuilder<String, Object> rowToInstanceProducerRecord(
    Row row, Object context) {
    return invokePrivate("rowToInstanceProducerRecord", new Class<?>[]{Row.class, context.getClass()}, row, context);
  }

  private boolean shouldLogJobDetails(long records) {
    return invokePrivate("shouldLogJobDetails", new Class<?>[]{long.class}, records);
  }

  @SuppressWarnings("unchecked")
  private <T> T invokePrivate(String methodName, Class<?>[] paramTypes, Object... args) {
    try {
      Method method = ReindexJobRunner.class.getDeclaredMethod(methodName, paramTypes);
      method.setAccessible(true);
      return (T) method.invoke(runner, args);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
