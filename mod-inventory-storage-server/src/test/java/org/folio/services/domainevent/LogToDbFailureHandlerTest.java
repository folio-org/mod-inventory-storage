package org.folio.services.domainevent;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsBlankString.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.vertx.kafka.client.producer.impl.KafkaProducerRecordImpl;
import java.util.Date;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.persist.entity.NotificationSendingError;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.WARN)
@ExtendWith(MockitoExtension.class)
class LogToDbFailureHandlerTest {
  @Mock
  private NotificationSendingErrorRepository repository;

  @Test
  void canHandleFailure() {
    var handler = new LogToDbFailureHandler(repository);
    handler.handleFailure(new IllegalArgumentException("null"),
      new KafkaProducerRecordImpl<>("topic", "key", "value"));

    var errorArgumentCaptor = ArgumentCaptor.forClass(NotificationSendingError.class);
    verify(repository).save(any(), errorArgumentCaptor.capture());

    var notificationSendingError = errorArgumentCaptor.getValue();
    assertThat(notificationSendingError.getId(), not(blankOrNullString()));
    assertThat(notificationSendingError.getTopicName(), is("topic"));
    assertThat(notificationSendingError.getPartitionKey(), is("key"));
    assertThat(notificationSendingError.getPayload(), is("value"));
    assertTrue(notificationSendingError.getError().contains("IllegalArgumentException: null"));
    assertThat(notificationSendingError.getIncidentDateTime()
      .after(new Date()), Matchers.is(false));
  }
}
