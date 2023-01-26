package org.folio.rest.support.kafka;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.rest.support.messages.EventMessage;

public class GroupedCollectedMessages {
  private final Map<String, List<EventMessage>> collectedMessages;

  public GroupedCollectedMessages() {
    collectedMessages = new HashMap<>();
  }

  void add(String key, EventMessage eventMessage) {
    final var keyBucket = collectedMessages.computeIfAbsent(key,
      v -> new ArrayList<>());

    keyBucket.add(eventMessage);
  }

  List<EventMessage> messagesByGroupKey(String key) {
    return collectedMessages.getOrDefault(key, emptyList());
  }

  int groupCount() {
    return collectedMessages.size();
  }

  void empty() {
    collectedMessages.clear();
  }
}
