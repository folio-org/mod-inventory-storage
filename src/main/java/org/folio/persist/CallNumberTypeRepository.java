package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.callnumber.CallNumberTypeService.CALL_NUMBER_TYPE_TABLE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.CallNumberType;

public class CallNumberTypeRepository extends AbstractRepository<CallNumberType> {

  public CallNumberTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), CALL_NUMBER_TYPE_TABLE, CallNumberType.class);
  }

}
