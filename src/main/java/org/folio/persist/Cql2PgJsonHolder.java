package org.folio.persist;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;

public class Cql2PgJsonHolder {
  private static final Logger LOGGER = LogManager.getLogger();

  private static final Map<String, CQL2PgJSON> CQL_2_PG_JSON_MAP = new ConcurrentHashMap<>();

  public static CQL2PgJSON getCql2PgJson(String tableName) {
    return CQL_2_PG_JSON_MAP.computeIfAbsent(tableName, name -> {
      try {
        return new CQL2PgJSON(name + ".jsonb");
      } catch (FieldException e) {
        LOGGER.error("Something happened while creating CQL2PgJSON", e);
        return null;
      }
    });
  }
}
