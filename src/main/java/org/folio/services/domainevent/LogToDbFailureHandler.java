package org.folio.services.domainevent;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.persist.entity.NotificationSendingError;

final class LogToDbFailureHandler implements FailureHandler {
  private final NotificationSendingErrorRepository repository;

  LogToDbFailureHandler(NotificationSendingErrorRepository repository) {
    this.repository = repository;
  }

  LogToDbFailureHandler(Context context, Map<String, String> okapiHeaders) {
    this(new NotificationSendingErrorRepository(postgresClient(context, okapiHeaders)));
  }

  @Override
  public void handleFailure(Throwable error, KafkaProducerRecord<String, String> producerRecord) {
    var errorLog = new NotificationSendingError(UUID.randomUUID().toString(),
      producerRecord.topic(), producerRecord.key(), producerRecord.value(),
      getStackTrace(error), new Date());

    repository.save(errorLog.getId(), errorLog);
  }
}
