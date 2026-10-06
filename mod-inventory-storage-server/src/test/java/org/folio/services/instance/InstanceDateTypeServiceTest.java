package org.folio.services.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.vertx.core.Future;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.InstanceDateTypeRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.InstanceDateTypePatchRequest;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InstanceDateTypeService's constructor self-constructs its repository/publisher from a real
 * Context, so this uses the same constructor-bypass-plus-field-injection approach as
 * HoldingsServiceTest. patchInstanceDateTypes's consortium routing is the one real branch in an
 * otherwise thin CRUD-passthrough class - doUpdate is left to reach only as far as
 * repository.getById (stubbed to fail fast) so the test doesn't need to also mock PgUtil.put's
 * static, handler-based machinery.
 */
class InstanceDateTypeServiceTest {

  private static final String CENTRAL_TENANT = "central-tenant";
  private static final String MEMBER_TENANT = "member-tenant";

  @Test
  @DisplayName("should proceed to update when there is no consortium data")
  void shouldProceedToUpdate_whenNoConsortiumData() {
    var repository = mock(InstanceDateTypeRepository.class);
    var service = serviceWith(repository, CENTRAL_TENANT, Optional.empty());
    when(repository.getById(anyString())).thenReturn(Future.failedFuture("stub"));

    var result = service.patchInstanceDateTypes("id-1", new InstanceDateTypePatchRequest());

    assertThat(result.failed()).isTrue();
    verify(repository).getById("id-1");
  }

  @Test
  @DisplayName("should proceed to update when the tenant is the central tenant")
  void shouldProceedToUpdate_whenTenantIsCentralTenant() {
    var repository = mock(InstanceDateTypeRepository.class);
    var consortiumData = new ConsortiumData(CENTRAL_TENANT, "consortium-1", List.of(MEMBER_TENANT));
    var service = serviceWith(repository, CENTRAL_TENANT, Optional.of(consortiumData));
    when(repository.getById(anyString())).thenReturn(Future.failedFuture("stub"));

    var result = service.patchInstanceDateTypes("id-1", new InstanceDateTypePatchRequest());

    assertThat(result.failed()).isTrue();
    verify(repository).getById("id-1");
  }

  @Test
  @DisplayName("should reject the update when the tenant is a consortium member tenant")
  void shouldRejectUpdate_whenTenantIsMemberTenant() {
    var repository = mock(InstanceDateTypeRepository.class);
    var consortiumData = new ConsortiumData(CENTRAL_TENANT, "consortium-1", List.of(MEMBER_TENANT));
    var service = serviceWith(repository, MEMBER_TENANT, Optional.of(consortiumData));

    var result = service.patchInstanceDateTypes("id-1", new InstanceDateTypePatchRequest());

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isInstanceOf(BadRequestException.class);
    verify(repository, never()).getById(anyString());
  }

  private static InstanceDateTypeService serviceWith(InstanceDateTypeRepository repository, String tenant,
                                                      Optional<ConsortiumData> consortiumData) {
    var consortiumDataCache = mock(ConsortiumDataCache.class);
    Map<String, String> okapiHeaders = new CaseInsensitiveMap<>(Map.of(XOkapiHeaders.TENANT, tenant));
    when(consortiumDataCache.getConsortiumData(okapiHeaders)).thenReturn(Future.succeededFuture(consortiumData));

    var service = mock(InstanceDateTypeService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    setField(service, "repository", repository);
    setField(service, "consortiumDataCache", consortiumDataCache);
    setField(service, "okapiHeaders", okapiHeaders);
    return service;
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
