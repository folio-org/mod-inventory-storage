package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Itemdereferenced;
import org.folio.rest.jaxrs.resource.ItemStorageDereferenced;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.cql2pgjson.CQL2PgJSON;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

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
  private static Map<String, String> fields = new HashMap<>();
  private static List<String> idFields = new ArrayList<String>();
  static{
    fields.put("instance", "instanceRecord");
    fields.put("holdings", "holdingsRecord");
    fields.put("materialtype", "materialType");
    fields.put("permloan", "permanentLoanType");
    fields.put("temploan", "temporaryLoanType");
    fields.put("permloc", "permanentLocation");
    fields.put("temploc", "temporaryLocation");
    fields.put("effloc", "effectiveLocation");
    idFields.add("holdingsrecordid");
    idFields.add("instanceid");
    idFields.add("materialtypeid");
    idFields.add("permanentlocationid");
    idFields.add("temporarylocationid");
    idFields.add("effectivelocationid");
    idFields.add("permanentloantypeid");
    idFields.add("temporaryloantypeid");
  }
  private String sqlQuery= "SELECT item.jsonb as item, holdingstable.jsonb as " + fields.get("holdings") + ",\n" + 
  "instancetable.jsonb as " + fields.get("instance") + ", materialtypetable.jsonb as " + fields.get("materialtype") + ",\n" + 
  "locationtable.jsonb as " + fields.get("permloc") + ", locationtabletemp.jsonb as " + fields.get("temploc") + ",\n" + 
  "locationtableeffective.jsonb as " + fields.get("effloc") + ",\n"+
  "loantable.jsonb as " + fields.get("permloan") + ", loantabletemp.jsonb as " + fields.get("temploc") + "\n"+
  "FROM " + ITEM_TABLE + "\n"+
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
    JsonArray mappedResults = new JsonArray();
    String whereClause = "";
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
      
      result.forEach(row -> {mappedResults.add(mapToJson(row));});
      logger.info("mapped results: " + mappedResults.toString());
    
      //GetItemStorageDereferencedItemsResponse.respond200WithApplicationJson(mappedResults);
    });
  }

  @Validate
  @Override
  public void getItemStorageDereferencedItemsByItemId(
      String itemId, String lang, java.util.Map<String, String> okapiHeaders,
      io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
  }

  private HttpServerResponse getResponse(RoutingContext routingContext) {
    final HttpServerResponse response = routingContext.response();
    response.setChunked(true);
    response.putHeader("Content-Type", "application/json");
    return response;
  }

 private JsonObject mapToJson(Row row) {
   JsonObject item = (JsonObject) row.getJson("item");
   idFields.forEach(fieldName -> {
     if (item.containsKey(fieldName)) {
      item.remove(fieldName);
     }
   });   
   List<String> fieldList = new ArrayList<String>(fields.values());
   fieldList.forEach(fieldName -> {
     int pos = row.getColumnIndex(fieldName);
     if (row.getJson(pos) != null) {
       item.put(fieldName, row.getJson(pos));
     }
   });
   return item;
 }


}
