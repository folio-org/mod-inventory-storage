package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.getVertx;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.persist.entity.NotificationSendingError;
import org.folio.rest.persist.PgUtil;
import org.junit.Test;

public class NotificationSendingErrorRepositoryTest extends TestBaseWithInventoryUtil {
  @Test
  public void canSaveNotificationError() {
    var repository = createRepository();
    var originalError = new NotificationSendingError(UUID.randomUUID().toString(),
      "topic", "key", "value", "error\nerror2", new Date());

    get(repository.save(originalError.getId(), originalError));

    var savedNotification = get(repository.getById(originalError.getId()));
    assertThat(savedNotification.getTopicName(), is("topic"));
    assertThat(savedNotification.getPartitionKey(), is("key"));
    assertThat(savedNotification.getPayload(), is("value"));
    assertThat(savedNotification.getError(), is("error\nerror2"));
    assertThat(savedNotification.getIncidentDateTime(), is(originalError.getIncidentDateTime()));
  }

  private NotificationSendingErrorRepository createRepository() {
    var postgresClient = PgUtil.postgresClient(getVertx().getOrCreateContext(),
      Map.of("x-okapi-tenant", TENANT_ID));

    return new NotificationSendingErrorRepository(postgresClient);
  }
}
