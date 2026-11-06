package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.LOAN_TYPE;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.LoanTypeRepository;
import org.folio.rest.jaxrs.model.LoanType;
import org.springframework.stereotype.Component;

@Component
public class LoanTypeDomainEventPublisher
  extends AbstractDomainEventPublisher<LoanType, LoanType> {

  private final LoanTypeRepository loanTypeRepository;

  public LoanTypeDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                      LoanTypeRepository loanTypeRepository) {
    super(publisherFactory.publisher(LOAN_TYPE));
    this.loanTypeRepository = loanTypeRepository;
  }

  @Override
  protected AbstractRepository<LoanType> repository() {
    return loanTypeRepository;
  }

  @Override
  protected Future<List<Pair<String, LoanType>>> getRecordIds(Collection<LoanType> loanTypes,
                                                              Map<String, String> headers) {
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
