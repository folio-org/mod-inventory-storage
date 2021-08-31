package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.impl.InstanceStorageAPI.PreparedCQL;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.jaxrs.resource.ItemStorageDereferenced;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.cql2pgjson.exception.QueryValidationException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


/**
 * CRUD for Dereferenced Items.
 * This is an experimental endpoint.
 * Aim is to determine if dereferencing item records
 * results in performance improvement when performing
 * checkout/checkin operations.
 */
public class ItemStorageDereferencedAPI implements ItemStorageDereferenced {
  private static final Logger logger = LogManager.getLogger("okapi");
  public static final String ITEM_TABLE = "item";
  private static final String JSON_COLUMN = "jsonb";
  private String sqlQuery= "SELECT item.jsonb as itemrecord, holdingstable.jsonb as holdingsrecord,\n" + 
  "instancetable.jsonb as instancerecord, materialtypetable.jsonb as materialtyperecord,\n" + 
  "locationtable.jsonb as permlocation, locationtabletemp.jsonb as templocation, locationtableeffective.jsonb as effectivelocation,\n"+
  "loantable.jsonb as permloantype, loantabletemp.jsonb as temploantype\n"+
  "FROM item\n"+
  "INNER JOIN holdings_record as holdingstable on item.holdingsrecordid=holdingstable.id\n"+
  "INNER JOIN instance as instancetable on holdingstable.instanceid=instancetable.id\n"+
  "INNER JOIN material_type as materialtypetable on item.materialtypeid=materialtypetable.id\n"+
  "LEFT JOIN location as locationtable on item.permanentlocationid = locationtable.id\n"+
  "LEFT JOIN location as locationtabletemp on item.temporarylocationid = locationtabletemp.id\n"+
  "LEFT JOIN location as locationtableeffective on item.effectivelocationid = locationtableeffective.id\n"+
  "INNER JOIN loan_type as loantable on item.permanentloantypeid=loantable.id\n"+
  "LEFT JOIN loan_type as loantabletemp on item.temporaryloantypeid=loantabletemp.id\n";

  @Validate
  @Override
  public void getItemStorageDereferencedItems(
    int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String whereClause = "";
    logger.info("query is: " + query);
    if (query != null) {
      try {
        CQLWrapper wrapper = new CQLWrapper(new CQL2PgJSON(ITEM_TABLE + "." + JSON_COLUMN), query, limit, offset);
        logger.info("translated SQL query: " + wrapper.toString());
        whereClause = wrapper.toString();
      } catch(Exception e) {
        GetItemStorageDereferencedItemsResponse.respond400WithTextPlain("Invalid CQL query: " + e.getMessage());
      }
    } else {
      whereClause = "LIMIT " + limit + " OFFSET " + offset;
    }

    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    postgresClient.select(sqlQuery + whereClause, asyncResult -> {
      if (asyncResult.failed()) {
        String failureMessage = asyncResult.cause().getMessage();
        GetItemStorageDereferencedItemsResponse.respond500WithTextPlain("Unable to retrieve data from database: " + failureMessage);
      }
      RowSet<Row> result = asyncResult.result();
      if (result.size() == 0) {
        GetItemStorageDereferencedItemsResponse.respond404WithTextPlain("No records found matching search criteria.");
      }

      logger.info("result is " + result.size() + " entries.");
      logger.info("Retrived rows: ");
      result.forEach(row -> logger.info(row.deepToString()));
    });
  }

  @Validate
  @Override
  public void getItemStorageDereferencedItemsByItemId(
      String itemId, String lang, java.util.Map<String, String> okapiHeaders,
      io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

  }


}
