package org.folio.support.kafka;

import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.InventoryKafkaTopic;
import org.folio.dataimport.testsupport.kafka.KafkaTestEventCollector;
import org.folio.support.messages.EventMessage;

/**
 * Wraps a {@link KafkaTestEventCollector} - which continuously drains the broker into an
 * in-memory per-topic index in the background, so an already-arrived message matches on the
 * first check instead of a test waiting out a broker-poll timeout - and layers this module's
 * per-entity message grouping on top.
 *
 * <p>Subscribes to every {@link InventoryKafkaTopic} for each of the given {@code tenants},
 * using {@link InventoryKafkaTopic#fullTopicName(String)} - the same topic-naming function
 * production code uses - rather than a separately-maintained set of topic name strings, so the
 * two can never drift out of sync.
 *
 * <p>One instance lives for the whole JVM (constructed once from
 * {@link org.folio.it.BaseIntegrationTest}); {@link #discardAllMessages()} doesn't reset the
 * underlying collector (it has no such operation, and recreating a consumer group before every
 * test risks reintroducing flakiness via partition-assignment lag) - it just records a cutoff
 * timestamp that every accessor filters on, so "discarded" messages are simply ignored rather
 * than actually removed.
 */
public final class FakeKafkaConsumer {

  private final List<String> tenants;
  private final KafkaTestEventCollector collector;
  private volatile long discardedBeforeEpochMillis = System.currentTimeMillis();

  public FakeKafkaConsumer(String bootstrapServers, List<String> tenants) {
    this.tenants = tenants;
    var topics = Arrays.stream(InventoryKafkaTopic.values())
      .flatMap(topic -> tenants.stream().map(topic::fullTopicName))
      .toList();
    collector = new KafkaTestEventCollector(bootstrapServers, "fake-kafka-consumer-" + UUID.randomUUID(), topics);
  }

  public void discardAllMessages() {
    discardedBeforeEpochMillis = System.currentTimeMillis();
  }

  public int getAllPublishedInstanceIdsCount() {
    return (int) recordsSince(InventoryKafkaTopic.INSTANCE).map(ConsumerRecord::key).distinct().count();
  }

  public Collection<EventMessage> getMessagesForInstance(String instanceId) {
    return messagesFor(InventoryKafkaTopic.INSTANCE, message -> instanceId.equals(message.key()));
  }

  public Collection<EventMessage> getMessagesForReindexRecord(String id) {
    return messagesFor(InventoryKafkaTopic.REINDEX_RECORDS, message -> id.equals(message.key()));
  }

  public Collection<EventMessage> getMessagesForReindexFileReady(String rangeId) {
    return messagesFor(InventoryKafkaTopic.REINDEX_FILE_READY, message -> rangeId.equals(message.key()));
  }

  public Collection<EventMessage> getMessagesForInstances(List<String> instanceIds) {
    return instanceIds.stream()
      .map(this::getMessagesForInstance)
      .flatMap(Collection::stream)
      .toList();
  }

  public Collection<EventMessage> getMessagesForHoldings(String holdingsId) {
    final var key = instanceAndIdKey(holdingsId, holdingsId);
    return messagesFor(InventoryKafkaTopic.HOLDINGS_RECORD, message -> key.equals(instanceAndIdKey(message)));
  }

  public Collection<EventMessage> getMessagesForDeleteAllHoldings(String instanceId, String holdingsId) {
    final var key = instanceAndIdKey(instanceId, holdingsId);
    return messagesFor(InventoryKafkaTopic.HOLDINGS_RECORD, message -> key.equals(instanceAndIdKey(message)));
  }

  public Collection<EventMessage> getMessagesForItem(String itemId) {
    final var key = instanceAndIdKey(itemId, itemId);
    return messagesFor(InventoryKafkaTopic.ITEM, message -> key.equals(instanceAndIdKey(message)));
  }

  public Collection<EventMessage> getMessagesForLoanType(String loanTypeId) {
    return messagesFor(InventoryKafkaTopic.LOAN_TYPE, message -> loanTypeId.equals(message.key()));
  }

  public Collection<EventMessage> getMessagesForBoundWith(String instanceId) {
    return messagesFor(InventoryKafkaTopic.BOUND_WITH, message -> instanceId.equals(message.key()));
  }

  public Collection<EventMessage> getMessagesForServicePoint(String servicePointId) {
    return messagesFor(InventoryKafkaTopic.SERVICE_POINT, message -> servicePointId.equals(message.key()));
  }

  public Collection<EventMessage> getMessagesForMaterialType(String materialTypeId) {
    return messagesFor(InventoryKafkaTopic.MATERIAL_TYPE, message -> materialTypeId.equals(message.key()));
  }

  public Collection<EventMessage> getMessagesForSetting(String settingId) {
    return messagesFor(InventoryKafkaTopic.SETTING, message -> settingId.equals(message.key()));
  }

  private static String instanceAndIdKey(String instanceId, String itemId) {
    return instanceId + "_" + itemId;
  }

  private static String instanceAndIdKey(ConsumerRecord<String, String> message) {
    final JsonObject payload = new JsonObject(message.value());
    final var oldOrNew = payload.getJsonObject(payload.containsKey("new") ? "new" : "old");
    final var id = oldOrNew != null ? oldOrNew.getString("id") : null;

    return instanceAndIdKey(message.key(), id);
  }

  private Collection<EventMessage> messagesFor(InventoryKafkaTopic topic,
                                               Predicate<ConsumerRecord<String, String>> matches) {

    return recordsSince(topic)
      .filter(matches)
      .map(EventMessage::fromConsumerRecord)
      .toList();
  }

  private Stream<ConsumerRecord<String, String>> recordsSince(InventoryKafkaTopic topic) {
    return tenants.stream()
      .map(topic::fullTopicName)
      .flatMap(topicName -> collector.received(topicName).stream())
      .filter(message -> message.timestamp() >= discardedBeforeEpochMillis);
  }
}
