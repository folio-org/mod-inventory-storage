package org.folio.services.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
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
import org.folio.services.domainevent.InstanceDateTypeDomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * patchInstanceDateTypes's consortium routing is the one real branch in an
 * otherwise thin CRUD-passthrough class - doUpdate is left to reach only as far as
 * repository.getById (stubbed to fail fast).
 */
class InstanceDateTypeServiceTest {

  private static final String CENTRAL_TENANT = "central-tenant";
  private static final String MEMBER_TENANT = "member-tenant";

  @Test
  @DisplayName("should proceed to update when there is no consortium data")
  void shouldProceedToUpdate_whenNoConsortiumData() {
    var headers = headers(CENTRAL_TENANT);
    var repository = failingRepository();
    var service = serviceWith(headers, Optional.empty(), repository);

    var result = service.patchInstanceDateTypes("id-1", new InstanceDateTypePatchRequest(), headers);

    assertThat(result.failed()).isTrue();
    verify(repository).getById("id-1", tenantId(headers));
  }

  @Test
  @DisplayName("should proceed to update when the tenant is the central tenant")
  void shouldProceedToUpdate_whenTenantIsCentralTenant() {
    var headers = headers(CENTRAL_TENANT);
    var consortiumData = new ConsortiumData(CENTRAL_TENANT, "consortium-1", List.of(MEMBER_TENANT));
    var repository = failingRepository();
    var service = serviceWith(headers, Optional.of(consortiumData), repository);

    var result = service.patchInstanceDateTypes("id-1", new InstanceDateTypePatchRequest(), headers);

    assertThat(result.failed()).isTrue();
    verify(repository).getById("id-1", tenantId(headers));
  }

  @Test
  @DisplayName("should reject the update when the tenant is a consortium member tenant")
  void shouldRejectUpdate_whenTenantIsMemberTenant() {
    var headers = headers(MEMBER_TENANT);
    var consortiumData = new ConsortiumData(CENTRAL_TENANT, "consortium-1", List.of(MEMBER_TENANT));
    var repository = failingRepository();
    var service = serviceWith(headers, Optional.of(consortiumData), repository);

    var result = service.patchInstanceDateTypes("id-1", new InstanceDateTypePatchRequest(), headers);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isInstanceOf(BadRequestException.class);
    verify(repository, never()).getById(anyString(), any());
  }

  private static InstanceDateTypeRepository failingRepository() {
    var repository = mock(InstanceDateTypeRepository.class);
    when(repository.getById(anyString(), any())).thenReturn(Future.failedFuture("stub"));
    return repository;
  }

  private static Map<String, String> headers(String tenant) {
    return new CaseInsensitiveMap<>(Map.of(XOkapiHeaders.TENANT, tenant));
  }

  private static InstanceDateTypeService serviceWith(Map<String, String> headers,
                                                      Optional<ConsortiumData> consortiumData,
                                                      InstanceDateTypeRepository repository) {
    var consortiumDataCache = mock(ConsortiumDataCache.class);
    when(consortiumDataCache.getConsortiumData(headers)).thenReturn(Future.succeededFuture(consortiumData));
    return new InstanceDateTypeService(consortiumDataCache,
      mock(InstanceDateTypeDomainEventPublisher.class), repository);
  }
}
