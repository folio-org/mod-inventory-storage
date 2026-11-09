package org.folio.persist;

import io.vertx.core.Context;
import org.folio.persist.entity.NotificationSendingError;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationSendingErrorRepository extends AbstractRepository<NotificationSendingError> {

  private static final String TABLE_NAME = "notification_sending_error";

  public NotificationSendingErrorRepository(Context context) {
    super(context, TABLE_NAME, NotificationSendingError.class);
  }
}
