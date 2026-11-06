package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.LoanType;
import org.springframework.stereotype.Repository;

@Repository
public class LoanTypeRepository extends AbstractRepository<LoanType> {

  public static final String LOAN_TYPE_TABLE = "loan_type";

  public LoanTypeRepository(Context context) {
    super(context, LOAN_TYPE_TABLE, LoanType.class);
  }
}
