package org.folio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.caches.SettingCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.domainevent.SettingEventPublisher;
import org.folio.services.holding.HoldingsService;
import org.folio.services.instance.InstanceService;
import org.folio.services.item.ItemService;
import org.folio.services.setting.SettingsService;
import org.folio.spring.SpringContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class ApplicationConfigTest {

  private Vertx vertx;
  private Context context;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    context = vertx.getOrCreateContext();
    SpringContextUtil.init(vertx, context, ApplicationConfig.class);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  @DisplayName("Should resolve every bean when the Spring context starts")
  void shouldResolveAllBeans_whenContextStarts() {
    assertThat(bean(context, HttpClient.class)).isNotNull();
    assertThat(bean(context, ConsortiumDataCache.class)).isNotNull();
    assertThat(bean(context, SettingCache.class)).isNotNull();
    assertThat(bean(context, ConsortiumService.class)).isNotNull();
    assertThat(bean(context, SettingEventPublisher.class)).isNotNull();
    assertThat(bean(context, SettingsService.class)).isNotNull();
  }

  @Test
  @DisplayName("Should return the same HttpClient and service instances when beans are requested repeatedly")
  void shouldReturnSameInstances_whenBeansRequestedRepeatedly() {
    var firstClient = bean(context, HttpClient.class);
    var firstService = bean(context, SettingsService.class);

    for (int i = 0; i < 5; i++) {
      assertThat(bean(context, HttpClient.class)).isSameAs(firstClient);
      assertThat(bean(context, SettingsService.class)).isSameAs(firstService);
    }
  }

  @Test
  @DisplayName("Should have a Spring context stored in the Vert.x context")
  void shouldStoreSpringContext_whenInitialized() {
    ApplicationContext springContext = context.get("springContext");

    assertThat(springContext).isNotNull();
  }

  @Test
  @DisplayName("Should create exactly one HttpClient however often services are resolved")
  void shouldCreateOneHttpClient_whenServicesAreResolvedRepeatedly() {
    var spiedVertx = spy(Vertx.vertx());
    var spiedContext = spiedVertx.getOrCreateContext();
    SpringContextUtil.init(spiedVertx, spiedContext, ApplicationConfig.class);

    for (int i = 0; i < 20; i++) {
      bean(spiedContext, SettingsService.class);
      bean(spiedContext, InstanceService.class);
      bean(spiedContext, HoldingsService.class);
      bean(spiedContext, ItemService.class);
    }

    verify(spiedVertx, times(1)).createHttpClient();
    spiedVertx.close();
  }

  private static <T> T bean(Context vertxContext, Class<T> type) {
    ApplicationContext springContext = vertxContext.get("springContext");
    return springContext.getBean(type);
  }
}
