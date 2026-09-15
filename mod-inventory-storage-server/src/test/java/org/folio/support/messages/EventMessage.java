package org.folio.support.messages;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Value
public class EventMessage {
  String type;
  String tenant;
  JsonObject newRepresentation;
  JsonObject oldRepresentation;
  JsonObject body;
  Map<String, String> headers;

  public static EventMessage fromConsumerRecord(ConsumerRecord<String, String> consumerRecord) {
    final var value = new JsonObject(consumerRecord.value());

    return new EventMessage(
      value.getString("type"),
      value.getString("tenant"),
      value.getJsonObject("new"),
      value.getJsonObject("old"),
      value,
      rawHeadersToMap(consumerRecord));
  }

  private static Map<String, String> rawHeadersToMap(ConsumerRecord<String, String> consumerRecord) {
    final Map<String, String> headers = new HashMap<>();
    consumerRecord.headers().forEach(header -> headers.put(header.key(), new String(header.value(), UTF_8)));
    return headers;
  }
}
