package org.folio.rest.impl;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.annotations.Validate;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.jaxrs.model.DereferencedItem;
import org.folio.rest.jaxrs.model.DereferencedItems;
import org.folio.rest.jaxrs.resource.ItemStorageDereferenced;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import static org.folio.dbschema.ObjectMapperTool.readValue;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

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
    idFields.add("holdingsRecordId");
    idFields.add("instanceId");
    idFields.add("materialTypeId");
    idFields.add("permanentLocationId");
    idFields.add("temporaryLocationId");
    idFields.add("effectiveLocationId");
    idFields.add("permanentLoanTypeId");
    idFields.add("temporaryLoanTypeId");
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
    List<DereferencedItem> mappedResults = new ArrayList<>();
    String whereClause = "";
    if (query != null) {
      try {
        CQLWrapper wrapper = new CQLWrapper(
          new CQL2PgJSON(ITEM_TABLE + "." + JSON_COLUMN), query, limit, offset);
        logger.info("translated SQL query: " + wrapper.toString());
        whereClause = wrapper.toString();
      } catch(Exception e) {
         asyncResultHandler.handle(Future.succeededFuture(
           GetItemStorageDereferencedItemsResponse.respond400WithTextPlain(
             "Invalid CQL query: " + e.getMessage())));
      }
    } else {
      whereClause = "LIMIT " + limit + " OFFSET " + offset;
    }

    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    postgresClient.select(sqlQuery + whereClause, asyncResult -> {
      if (asyncResult.failed()) {
        String failureMessage = asyncResult.cause().getMessage();
        asyncResultHandler.handle(Future.succeededFuture(
          GetItemStorageDereferencedItemsResponse.respond500WithTextPlain(
            "Unable to retrieve data from database: " + failureMessage)));
        return;
      }
      RowSet<Row> result = asyncResult.result();
      if (result.size() == 0) {
        asyncResultHandler.handle(Future.succeededFuture(
          GetItemStorageDereferencedItemsResponse.respond404WithTextPlain(
            "No records found matching search criteria.")));
      }
      result.forEach(row -> {mappedResults.add(mapToDereferencedItem(row));});
      DereferencedItems itemCollection = new DereferencedItems();
      itemCollection.setDereferencedItems(mappedResults);
      itemCollection.setTotalRecords(mappedResults.size());

      asyncResultHandler.handle(Future.succeededFuture(
        GetItemStorageDereferencedItemsResponse.respond200WithApplicationJson(itemCollection)));
      return;
    });
  }

  @Validate
  @Override
  public void getItemStorageDereferencedItemsByItemId(
      String itemId, String lang, java.util.Map<String, String> okapiHeaders,
      io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
  }

  private DereferencedItem mapToDereferencedItem(Row row) {
    
    JsonObject itemJson = (JsonObject) row.getJson("item");
    idFields.forEach(fieldName -> {
      if (itemJson.containsKey(fieldName)) {
        itemJson.remove(fieldName);
      }
    });   
    List<String> fieldList = new ArrayList<String>(fields.values());
    fieldList.forEach(fieldName -> {
      int pos = row.getColumnIndex(fieldName.toLowerCase());
      if (row.getJson(pos) != null) {
        itemJson.put(fieldName, row.getJson(pos));
      }
    });

    DereferencedItem item = readValue(itemJson.toString(), DereferencedItem.class);
    return item;
  }
}
