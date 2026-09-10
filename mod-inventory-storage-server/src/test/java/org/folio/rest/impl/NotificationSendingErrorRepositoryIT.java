package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.Vertx;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.persist.entity.NotificationSendingError;
import org.folio.rest.persist.PgUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSendingErrorRepositoryIT extends BaseIntegrationTest {

  @Test
  @DisplayName("should save and retrieve a notification sending error")
  void shouldSaveAndRetrieveNotificationError(Vertx vertx) {
    var repository = createRepository(vertx);
    var originalError = new NotificationSendingError(UUID.randomUUID().toString(),
      "topic", "key", "value", "error\nerror2", new Date());

    get(repository.save(originalError.getId(), originalError));

    var savedNotification = get(repository.getById(originalError.getId()));
    assertThat(savedNotification.getTopicName()).isEqualTo("topic");
    assertThat(savedNotification.getPartitionKey()).isEqualTo("key");
    assertThat(savedNotification.getPayload()).isEqualTo("value");
    assertThat(savedNotification.getError()).isEqualTo("error\nerror2");
    assertThat(savedNotification.getIncidentDateTime()).isEqualTo(originalError.getIncidentDateTime());
  }

  private static NotificationSendingErrorRepository createRepository(Vertx vertx) {
    var postgresClient = PgUtil.postgresClient(vertx.getOrCreateContext(),
      new CaseInsensitiveMap<>(Map.of(XOkapiHeaders.TENANT, TENANT_ID)));

    return new NotificationSendingErrorRepository(postgresClient);
  }
}
