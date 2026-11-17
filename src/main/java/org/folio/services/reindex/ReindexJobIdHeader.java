package org.folio.services.reindex;

import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.folio.kafka.services.KafkaProducerRecordBuilder;

/**
 * Propagates the reindex job identifier sent by mod-search on reindex-records publish/export requests to the Kafka
 * events produced for that request, so that each mod-search instance can process only the events of its own job.
 *
 * <p>The header name must start with {@code X-Okapi-}: RMB passes only such headers to the API implementation.
 */
public final class ReindexJobIdHeader {

  public static final String NAME = "X-Okapi-Reindex-Job-Id";

  private ReindexJobIdHeader() { }

  public static Optional<String> fromOkapiHeaders(Map<String, String> okapiHeaders) {
    if (okapiHeaders == null) {
      return Optional.empty();
    }
    return okapiHeaders.entrySet().stream()
      .filter(entry -> NAME.equalsIgnoreCase(entry.getKey()))
      .map(Map.Entry::getValue)
      .filter(StringUtils::isNotBlank)
      .findFirst();
  }

  /**
   * Adds the reindex job id header to the record when the incoming request carried one; leaves it unchanged otherwise.
   */
  public static <K, V> KafkaProducerRecordBuilder<K, V> propagate(KafkaProducerRecordBuilder<K, V> builder,
                                                                  Map<String, String> okapiHeaders) {
    fromOkapiHeaders(okapiHeaders).ifPresent(jobId -> builder.header(NAME, jobId));
    return builder;
  }
}
