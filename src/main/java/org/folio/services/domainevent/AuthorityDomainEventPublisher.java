package org.folio.services.domainevent;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AuthorityRepository;
import org.folio.rest.jaxrs.model.Authority;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.AUTHORITY;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

public class AuthorityDomainEventPublisher extends AbstractDomainEventPublisher<Authority, Authority> {

  public AuthorityDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new AuthorityRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        AUTHORITY.fullTopicName(environment(), tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, Authority>>> getInstanceIds(Collection<Authority> authorities) {
    return succeededFuture(authorities.stream()
      .map(authority -> pair(authority.getId(), authority))
      .collect(Collectors.toList()));
  }

  @Override
  protected Authority convertDomainToEvent(String instanceId, Authority domain) {
    return domain;
  }

  @Override
  protected String getId(Authority record) {
    return record.getId();
  }
}
