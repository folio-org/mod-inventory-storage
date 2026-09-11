package org.folio.services.setting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.vertx.core.ThreadingModel;
import org.folio.services.caches.SettingCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingUpdateConsumerVerticleTest {

  private SettingCache cache;

  @BeforeEach
  void setUp() {
    cache = mock(SettingCache.class);
  }

  @Test
  void getDeploymentOptionsShouldReturnCorrectOptions() {
    var options = SettingUpdateConsumerVerticle.getDeploymentOptions();

    assertNotNull(options);
    assertThat(options.getThreadingModel(), is(ThreadingModel.WORKER));
    assertThat(options.getInstances(), is(1));
  }

  @Test
  void constructorShouldCreateVerticleWithCache() {
    var verticle = new SettingUpdateConsumerVerticle(cache);

    assertNotNull(verticle);
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

    assertNotNull(options1);
    assertNotNull(options2);
    assertThat(options1 != options2, is(true));
  }
}
