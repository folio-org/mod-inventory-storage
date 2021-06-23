package org.folio.persist;

import org.folio.persist.entity.NotificationSendingError;
import org.folio.rest.persist.PostgresClient;

public class NotificationSendingErrorRepository extends AbstractRepository<NotificationSendingError> {
  public NotificationSendingErrorRepository(PostgresClient postgresClient) {
    super(postgresClient, "notification_sending_error",
      NotificationSendingError.class);
  }
}
