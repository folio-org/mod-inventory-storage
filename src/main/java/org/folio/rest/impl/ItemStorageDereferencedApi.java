package org.folio.rest.impl;

import static org.folio.dbschema.ObjectMapperTool.readValue;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.DereferencedItem;
import org.folio.rest.jaxrs.model.DereferencedItems;
import org.folio.rest.jaxrs.resource.ItemStorageDereferenced;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.util.UuidUtil;

/**
 * CRUD for Dereferenced Items.
 * This is an experimental endpoint.
 * Aim is to determine if dereferencing item records
 * results in performance improvement when performing
 * checkout/checkin operations.
 */
public class ItemStorageDereferencedApi implements ItemStorageDereferenced {
  public static final String ITEM_TABLE = "item";
  private static final String JSON_COLUMN = "jsonb";
  private static final Map<String, String> FIELDS = new HashMap<>();
  private static final List<String> ID_FIELDS = new ArrayList<>();

  static {
    FIELDS.put("instance", "instanceRecord");
    FIELDS.put("holdings", "holdingsRecord");
    FIELDS.put("materialtype", "materialType");
    FIELDS.put("permloan", "permanentLoanType");
    FIELDS.put("temploan", "temporaryLoanType");
    FIELDS.put("permloc", "permanentLocation");
    FIELDS.put("temploc", "temporaryLocation");
    FIELDS.put("effloc", "effectiveLocation");
    ID_FIELDS.add("holdingsRecordId");
    ID_FIELDS.add("instanceId");
    ID_FIELDS.add("materialTypeId");
    ID_FIELDS.add("permanentLocationId");
    ID_FIELDS.add("temporaryLocationId");
    ID_FIELDS.add("effectiveLocationId");
    ID_FIELDS.add("permanentLoanTypeId");
    ID_FIELDS.add("temporaryLoanTypeId");
  }

  private final String sqlQuery = "SELECT item.jsonb as item, holdingstable.jsonb as " + FIELDS.get("holdings") + ",\n"
    + "instancetable.jsonb as " + FIELDS.get("instance") + ", materialtypetable.jsonb as " + FIELDS.get("materialtype")
    + ",\n"
    + "locationtable.jsonb as " + FIELDS.get("permloc") + ", locationtabletemp.jsonb as " + FIELDS.get("temploc")
    + ",\n"
    + "locationtableeffective.jsonb as " + FIELDS.get("effloc") + ",\n"
    + "loantable.jsonb as " + FIELDS.get("permloan") + ", loantabletemp.jsonb as " + FIELDS.get("temploan") + "\n"
    + "FROM " + ITEM_TABLE + "\n"
    + "INNER JOIN holdings_record as holdingstable on item.holdingsrecordid=holdingstable.id\n"
    + "INNER JOIN instance as instancetable on holdingstable.instanceid=instancetable.id\n"
    + "INNER JOIN material_type as materialtypetable on item.materialtypeid=materialtypetable.id\n"
    + "LEFT JOIN location as locationtable on item.permanentlocationid = locationtable.id\n"
    + "LEFT JOIN location as locationtabletemp on item.temporarylocationid = locationtabletemp.id\n"
    + "LEFT JOIN location as locationtableeffective on item.effectivelocationid = locationtableeffective.id\n"
    + "INNER JOIN loan_type as loantable on item.permanentloantypeid=loantable.id\n"
    + "LEFT JOIN loan_type as loantabletemp on item.temporaryloantypeid=loantabletemp.id\n";

  @Validate
  @Override
  public void getItemStorageDereferencedItems(String totalRecords, int offset, int limit, String query,
                                              Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {

    List<DereferencedItem> mappedResults = new ArrayList<>();
    String whereClause;

    if (query != null) {
      try {
        CQLWrapper wrapper = new CQLWrapper(
          new CQL2PgJSON(ITEM_TABLE + "." + JSON_COLUMN), query, limit, offset);
        whereClause = wrapper.toString();
      } catch (Exception e) {
        respondWith400Error("Invalid CQL query: " + e.getMessage(), asyncResultHandler);
        return;
      }
    } else {
      whereClause = "LIMIT " + limit + " OFFSET " + offset;
    }

    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    postgresClient.select(sqlQuery + whereClause, asyncResult -> {

      if (Boolean.TRUE.equals(handleSelectFailure(asyncResult, asyncResultHandler))) {
        return;
      }
      if (asyncResult.result().size() != 0) {
        asyncResult.result().forEach(row -> mappedResults.add(mapToDereferencedItem(row)));
      }
      DereferencedItems itemCollection = new DereferencedItems();
      itemCollection.setDereferencedItems(mappedResults);
      itemCollection.setTotalRecords(mappedResults.size());

      asyncResultHandler.handle(Future.succeededFuture(
        GetItemStorageDereferencedItemsResponse.respond200WithApplicationJson(itemCollection)));
    });
  }

  @Validate
  @Override
  public void getItemStorageDereferencedItemsByItemId(
    String itemId, java.util.Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (!UuidUtil.isUuid(itemId)) {
      respondWith400Error("Invalid UUID", asyncResultHandler);
      return;
    }
    String whereClause = "WHERE item.id='" + itemId + "'";
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    postgresClient.select(sqlQuery + whereClause, asyncResult -> {
      if (Boolean.TRUE.equals(handleSelectFailure(asyncResult, asyncResultHandler))) {
        return;
      }
      if (asyncResult.result().size() == 0) {
        respondWith404Error("No item records found matching provided UUID.", asyncResultHandler);
        return;
      }
      Row row = asyncResult.result().iterator().next();

      DereferencedItem item = mapToDereferencedItem(row);
      asyncResultHandler.handle(Future.succeededFuture(
        GetItemStorageDereferencedItemsByItemIdResponse.respond200WithApplicationJson(item)));
    });
  }

  private Boolean handleSelectFailure(AsyncResult<RowSet<Row>> asyncResult,
                                      Handler<AsyncResult<Response>> asyncResultHandler) {

    if (asyncResult.failed()) {
      String errorMessage = asyncResult.cause().getMessage();
      respondWith500Error("Can't retrive item records: " + errorMessage, asyncResultHandler);
      return true;
    }
    return false;
  }

  private void respondWith500Error(String message, Handler<AsyncResult<Response>> asyncResultHandler) {

    asyncResultHandler.handle(
      Future.succeededFuture(
        GetItemStorageDereferencedItemsResponse.respond500WithTextPlain(message)));
  }

  private void respondWith404Error(String message, Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(
      Future.succeededFuture(
        GetItemStorageDereferencedItemsResponse.respond404WithTextPlain(message)));
  }

  private void respondWith400Error(String message, Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(
      Future.succeededFuture(
        GetItemStorageDereferencedItemsResponse.respond400WithTextPlain(message)));
  }

  private DereferencedItem mapToDereferencedItem(Row row) {
    JsonObject itemJson = (JsonObject) row.getJson("item");
    ID_FIELDS.forEach(fieldName -> {
      if (itemJson.containsKey(fieldName)) {
        itemJson.remove(fieldName);
      }
    });
    List<String> fieldList = new ArrayList<>(FIELDS.values());
    fieldList.forEach(fieldName -> {
      int pos = row.getColumnIndex(fieldName.toLowerCase());
      if (row.getJson(pos) != null) {
        itemJson.put(fieldName, row.getJson(pos));
      }
    });

    return readValue(itemJson.toString(), DereferencedItem.class);
  }
}
