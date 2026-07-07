package org.folio.services.setting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.vertx.core.ThreadingModel;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.folio.services.caches.SettingCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SettingUpdateConsumerVerticleTest {

  private SettingCache cache;
  private MockedStatic<KafkaEnvironmentProperties> mockedKafkaEnvProperties;

  @BeforeEach
  void setUp() {
    cache = mock(SettingCache.class);
    mockedKafkaEnvProperties = mockStatic(KafkaEnvironmentProperties.class);
    mockedKafkaEnvProperties.when(KafkaEnvironmentProperties::environment).thenReturn("test-env");
    mockedKafkaEnvProperties.when(KafkaEnvironmentProperties::host).thenReturn("localhost");
    mockedKafkaEnvProperties.when(KafkaEnvironmentProperties::port).thenReturn("9092");
  }

  @AfterEach
  void tearDown() {
    mockedKafkaEnvProperties.close();
  }

  @Test
  void getDeploymentOptionsShouldReturnCorrectOptions() {
    var options = SettingUpdateConsumerVerticle.getDeploymentOptions();

    assertThat(options, is(notNullValue()));
    assertThat(options.getThreadingModel(), is(ThreadingModel.WORKER));
    assertThat(options.getInstances(), is(1));
  }

  @Test
  void constructorShouldCreateVerticleWithCache() {
    var verticle = new SettingUpdateConsumerVerticle(cache);

    assertThat(verticle, is(notNullValue()));
  }

  @Test
  void getDeploymentOptionsShouldSetWorkerThreadingModel() {
    var options = SettingUpdateConsumerVerticle.getDeploymentOptions();

    assertThat(options.getThreadingModel(), is(ThreadingModel.WORKER));
  }

  @Test
  void getDeploymentOptionsShouldSetSingleInstance() {
    var options = SettingUpdateConsumerVerticle.getDeploymentOptions();

    assertThat(options.getInstances(), is(1));
  }

  @Test
  void verticleShouldExtendAbstractVerticle() {
    assertThat(SettingUpdateConsumerVerticle.class.getSuperclass().getSimpleName(), is("AbstractVerticle"));
  }

  @Test
  void getDeploymentOptionsShouldReturnNewInstanceEachTime() {
    var options1 = SettingUpdateConsumerVerticle.getDeploymentOptions();
    var options2 = SettingUpdateConsumerVerticle.getDeploymentOptions();

    assertThat(options1, is(notNullValue()));
    assertThat(options2, is(notNullValue()));
    assertThat(options1 != options2, is(true));
  }
}
