package org.folio.utils;

import static io.vertx.core.Promise.promise;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.persist.Conn;

public final class DatabaseUtils {

  private DatabaseUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static Future<RowStream<Row>> selectStream(Conn con, String query) {
    Promise<RowStream<Row>> result = promise();

    con.selectStream(query, Tuple.tuple(), result::complete);

    return result.future();
  }
}
