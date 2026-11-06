package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.springframework.stereotype.Repository;

@Repository
public class CallNumberTypeRepository extends AbstractRepository<CallNumberType> {

  public static final String CALL_NUMBER_TYPE_TABLE = "call_number_type";

  public CallNumberTypeRepository(Context context) {
    super(context, CALL_NUMBER_TYPE_TABLE, CallNumberType.class);
  }
}
