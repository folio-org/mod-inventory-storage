package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.LOAN_TYPE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.LoanTypeRepository;
import org.folio.rest.jaxrs.model.LoanType;

public class LoanTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<LoanType, LoanType> {

  public LoanTypeDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new LoanTypeRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        LOAN_TYPE.fullTopicName(tenantId(okapiHeaders))));
  }

  @Override
  protected Future<List<Pair<String, LoanType>>> getRecordIds(Collection<LoanType> loanTypes) {
    return succeededFuture(loanTypes.stream()
      .map(loanType -> pair(loanType.getId(), loanType))
      .toList()
    );
  }

  @Override
  protected LoanType convertDomainToEvent(String instanceId, LoanType loanType) {
    return loanType;
  }

  @Override
  protected String getId(LoanType loanType) {
    return loanType.getId();
  }
}
