package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.loantype.LoanTypeService.LOAN_TYPE_TABLE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.LoanType;

public class LoanTypeRepository extends AbstractRepository<LoanType> {

  public LoanTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), LOAN_TYPE_TABLE, LoanType.class);
  }
}
