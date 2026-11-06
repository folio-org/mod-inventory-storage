package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.persist.AbstractRepository;
import org.folio.services.batch.BatchOperationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractDomainEventPublisherTest {

  private static final Map<String, String> HEADERS = Map.of();

  private @Mock AbstractRepository<String> repository;
  private @Mock CommonDomainEventPublisher<TestEvent> domainEventService;

  private @InjectMocks TestPublisher publisher;

  @Test
  @DisplayName("should publish the created record")
  void shouldPublishCreatedRecord() {
    when(domainEventService.publishRecordsCreated(any(), any())).thenReturn(succeededFuture());

    publisher.publishRecordCreated("record-1", HEADERS);

    verify(domainEventService).publishRecordsCreated(List.of(Pair.of("record-1", event("record-1"))), HEADERS);
  }

  @Test
  @DisplayName("should skip publishing when the batch operation disables events")
  void shouldSkipPublishing_whenBatchOperationDisablesEvents() {
    var batch = new BatchOperationContext<>(List.of("new-1"), List.of("old-1"), false);

    publisher.publishRecordsCreatedOrUpdated(batch, HEADERS);

    verifyNoInteractions(domainEventService);
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("should publish created and updated records when the batch operation enables events")
  void shouldPublishCreatedAndUpdated_whenBatchOperationEnablesEvents() {
    when(domainEventService.publishRecordsCreated(any(), any())).thenReturn(succeededFuture());
    when(repository.getByIds(anyCollection(), ArgumentMatcherHelper.<String>anyFunction(), any()))
      .thenReturn(succeededFuture(Map.of("old-1", "old-1")));
    when(domainEventService.publishRecordsUpdated(any(), any())).thenReturn(succeededFuture());
    var batch = new BatchOperationContext<>(List.of("new-1"), List.of("old-1"), true);

    publisher.publishRecordsCreatedOrUpdated(batch, HEADERS);

    verify(domainEventService).publishRecordsCreated(List.of(Pair.of("new-1", event("new-1"))), HEADERS);
    verify(domainEventService).publishRecordsUpdated(
      List.of(Triple.of("old-1", event("old-1"), event("old-1"))), HEADERS);
  }

  @Test
  @DisplayName("should publish the removed record")
  void shouldPublishRemovedRecord() {
    when(domainEventService.publishRecordRemoved(eq("record-1"), any(TestEvent.class), any()))
      .thenReturn(succeededFuture());

    publisher.publishRecordRemoved("record-1", HEADERS);

    verify(domainEventService).publishRecordRemoved("record-1", event("record-1"), HEADERS);
  }

  @Test
  @DisplayName("should delegate to the domain event service when removing a raw record")
  void shouldDelegateToDomainEventService_whenRemovingRawRecord() {
    publisher.publishRemoved("instance-1", "raw-record", HEADERS);

    verify(domainEventService).publishRecordRemoved("instance-1", "raw-record", HEADERS);
  }

  @Test
  @DisplayName("should delegate to the domain event service when removing all records")
  void shouldDelegateToDomainEventService_whenRemovingAllRecords() {
    when(domainEventService.publishAllRecordsRemoved(any())).thenReturn(succeededFuture());

    publisher.publishAllRemoved(HEADERS);

    verify(domainEventService).publishAllRecordsRemoved(HEADERS);
  }

  @Test
  @DisplayName("should publish the updated record")
  void shouldPublishUpdatedRecord() {
    when(repository.getByIds(anyCollection(), ArgumentMatcherHelper.<String>anyFunction(), any()))
      .thenReturn(succeededFuture(Map.of("old-1", "old-1")));
    when(domainEventService.publishRecordsUpdated(any(), any())).thenReturn(succeededFuture());

    publisher.publishRecordUpdated("old-1", HEADERS);

    verify(domainEventService).publishRecordsUpdated(
      List.of(Triple.of("old-1", event("old-1"), event("old-1"))), HEADERS);
  }

  @Test
  @DisplayName("should skip publishing when there are no records to update")
  void shouldSkipPublishing_whenNoRecordsToUpdate() {
    var result = publisher.callPublishUpdated(List.of());

    assertThat(result.succeeded()).isTrue();
    verifyNoInteractions(repository);
    verifyNoInteractions(domainEventService);
  }

  @Test
  @DisplayName("should pair each new record with its matching old record by id")
  void shouldPairEachNewRecordWithMatchingOldRecord_byId() {
    var oldRecords = List.of(Pair.of("old-1", "old-1"));
    var newRecords = List.of(Pair.of("new-1", "old-1"));

    var result = publisher.callMapOldRecordsToNew(oldRecords, newRecords);

    assertThat(result).containsExactly(Triple.of("new-1", event("old-1"), event("old-1")));
  }

  private static TestEvent event(String domain) {
    return new TestEvent("event:" + domain);
  }

  private record TestEvent(String value) { }

  /**
   * D = domain type is the record's own id string; E = TestEvent wraps "event:" + domain, kept
   * distinct from String so it can't collide with CommonDomainEventPublisher's raw-String
   * publishRecordRemoved overload.
   */
  private static final class TestPublisher extends AbstractDomainEventPublisher<String, TestEvent> {

    private final AbstractRepository<String> repository;

    TestPublisher(AbstractRepository<String> repository, CommonDomainEventPublisher<TestEvent> service) {
      super(service);
      this.repository = repository;
    }

    @Override
    protected AbstractRepository<String> repository() {
      return repository;
    }

    @Override
    protected Future<List<Pair<String, String>>> getRecordIds(Collection<String> domainTypes,
                                                              Map<String, String> headers) {
      return succeededFuture(domainTypes.stream().map(domain -> Pair.of(domain, domain)).toList());
    }

    @Override
    protected TestEvent convertDomainToEvent(String instanceId, String domain) {
      return event(domain);
    }

    @Override
    protected String getId(String entity) {
      return entity;
    }

    private Future<Void> callPublishUpdated(Collection<String> oldRecords) {
      return publishUpdated(oldRecords, HEADERS);
    }

    private List<Triple<String, TestEvent, TestEvent>> callMapOldRecordsToNew(
      List<Pair<String, String>> oldRecords, List<Pair<String, String>> newRecords) {
      return mapOldRecordsToNew(oldRecords, newRecords);
    }
  }

  /**
   * Mockito's own {@code any()} can't infer a {@code Function<String, String>} type witness
   * cleanly at the call site - this narrows it without an unchecked warning at each use.
   */
  private static final class ArgumentMatcherHelper {
    private ArgumentMatcherHelper() { }

    static <T> Function<T, String> anyFunction() {
      return any();
    }
  }
}
