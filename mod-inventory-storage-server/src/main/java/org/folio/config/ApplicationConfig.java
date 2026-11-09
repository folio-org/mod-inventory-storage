package org.folio.config;

import static org.folio.services.domainevent.DomainEventPublisherFactory.REINDEX_PRODUCER_MANAGER;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.services.caches.SettingCache;
import org.folio.utils.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Spring configuration created once at module startup by {@code SpringContextUtil.init}.
 *
 * <p>Services, repositories, caches and publishers are discovered by component scanning
 * ({@code @Service}, {@code @Repository}, {@code @Component})
 * and receive their dependencies through constructor injection. Only beans that need a factory stay here: shared
 * resources such as the HTTP client and the Kafka producer managers are created in this class and nowhere else, so
 * they cannot be created per request (MODINVSTOR-1620). {@code Vertx} and {@code Context} are registered by
 * folio-di-support.
 */
@Configuration
@ComponentScan({"org.folio.services", "org.folio.persist", "org.folio.rest.support"})
@Import(org.folio.spring.ApplicationConfig.class)
public class ApplicationConfig {

  @Bean(destroyMethod = "close")
  public HttpClient httpClient(Vertx vertx) {
    return vertx.createHttpClient();
  }

  @Bean
  public SettingCache settingCache(Vertx vertx) {
    return SettingCache.getInstance(vertx);
  }

  @Bean
  @Primary
  public KafkaProducerManager kafkaProducerManager(Context context) {
    return producerManager(context, 0);
  }

  /** Producers with the larger maximum request size needed to publish reindex records. */
  @Bean(REINDEX_PRODUCER_MANAGER)
  public KafkaProducerManager reindexKafkaProducerManager(Context context) {
    return producerManager(context, Environment.getKafkaProducerMaxRequestSize());
  }

  private static KafkaProducerManager producerManager(Context context, int maxRequestSize) {
    var kafkaConfig = KafkaConfig.builder()
      .kafkaPort(KafkaEnvironmentProperties.port())
      .kafkaHost(KafkaEnvironmentProperties.host())
      .maxRequestSize(maxRequestSize)
      .build();

    return new SimpleKafkaProducerManager(context.owner(), kafkaConfig);
  }
}
