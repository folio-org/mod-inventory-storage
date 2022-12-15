package org.folio.rest.support.messages;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.rest.support.kafka.FakeKafkaConsumer;

public class BoundWithEventMessageChecks {
  public static boolean hasPublishedBoundWithHoldingsRecordIds(UUID id1,
    UUID id2, UUID id3) {
    List<String> holdingsRecordIds = List.of(id1.toString(), id2.toString(),
      id3.toString());
    List<String> publishedHoldingsRecordIds = FakeKafkaConsumer.getAllPublishedBoundWithEvents().stream()
      .filter(json -> json.containsKey("new"))
      .map(json -> json.getJsonObject("new").getString("holdingsRecordId"))
      .collect(Collectors.toList());
    return publishedHoldingsRecordIds.containsAll(holdingsRecordIds);
  }
}
