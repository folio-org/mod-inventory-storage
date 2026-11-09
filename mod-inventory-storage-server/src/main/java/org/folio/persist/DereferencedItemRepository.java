package org.folio.persist;

import static org.folio.dbschema.ObjectMapperTool.readValue;
import static org.folio.persist.ItemRepository.ITEM_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.DereferencedItem;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.util.UuidUtil;
import org.springframework.stereotype.Repository;

/**
 * Reads items together with the records they refer to: the holdings record, instance, material type,
 * locations and loan types.
 */
@Repository
public class DereferencedItemRepository extends AbstractRepository<Item> {

  private static final String DEREFERENCED_SQL = """
    SELECT item.jsonb as item, holdingstable.jsonb as holdingsRecord,
    instancetable.jsonb as instanceRecord, materialtypetable.jsonb as materialType,
    locationtable.jsonb as permanentLocation, locationtabletemp.jsonb as temporaryLocation,
    locationtableeffective.jsonb as effectiveLocation,
    loantable.jsonb as permanentLoanType, loantabletemp.jsonb as temporaryLoanType
    FROM item
    INNER JOIN holdings_record as holdingstable on item.holdingsrecordid=holdingstable.id
    INNER JOIN instance as instancetable on holdingstable.instanceid=instancetable.id
    INNER JOIN material_type as materialtypetable on item.materialtypeid=materialtypetable.id
    LEFT JOIN location as locationtable on item.permanentlocationid = locationtable.id
    LEFT JOIN location as locationtabletemp on item.temporarylocationid = locationtabletemp.id
    LEFT JOIN location as locationtableeffective on item.effectivelocationid = locationtableeffective.id
    INNER JOIN loan_type as loantable on item.permanentloantypeid=loantable.id
    LEFT JOIN loan_type as loantabletemp on item.temporaryloantypeid=loantabletemp.id
    """;

  private static final List<String> DEREFERENCED_FIELDS = List.of("instanceRecord", "holdingsRecord",
    "materialType", "permanentLoanType", "temporaryLoanType", "permanentLocation", "temporaryLocation",
    "effectiveLocation");

  private static final List<String> DEREFERENCED_ID_FIELDS = List.of("holdingsRecordId", "instanceId",
    "materialTypeId", "permanentLocationId", "temporaryLocationId", "effectiveLocationId",
    "permanentLoanTypeId", "temporaryLoanTypeId");

  private final CQL2PgJSON itemCql;

  public DereferencedItemRepository(Context context) {
    super(context, ITEM_TABLE, Item.class);
    try {
      itemCql = new CQL2PgJSON(ITEM_TABLE + ".jsonb");
    } catch (Exception e) {
      throw new IllegalArgumentException("Unexpected error creating CQL2PgJSON", e);
    }
  }

  /**
   * Finds the items matching the CQL, a null query matches all. An invalid CQL is reported as
   * {@link BadRequestException}.
   */
  public Future<List<DereferencedItem>> query(String cql, int offset, int limit, String tenantId) {
    String suffix;
    try {
      suffix = cql == null
               ? "LIMIT " + limit + " OFFSET " + offset
               : new CQLWrapper(itemCql, cql, limit, offset).toString();
    } catch (Exception e) {
      return Future.failedFuture(new BadRequestException("Invalid CQL query: " + e.getMessage()));
    }
    return postgresClient(tenantId).select(DEREFERENCED_SQL + suffix).map(this::toDereferencedItems);
  }

  /**
   * Finds the item with the id, empty if there is no such item. An id that is not a UUID is reported as
   * {@link BadRequestException}.
   */
  public Future<Optional<DereferencedItem>> findDereferencedById(String itemId, String tenantId) {
    if (!UuidUtil.isUuid(itemId)) {
      return Future.failedFuture(new BadRequestException("Invalid UUID"));
    }
    return postgresClient(tenantId)
      .execute(DEREFERENCED_SQL + "WHERE item.id = $1", Tuple.of(UUID.fromString(itemId)))
      .map(this::toDereferencedItems)
      .map(items -> items.stream().findFirst());
  }

  private List<DereferencedItem> toDereferencedItems(Iterable<Row> rows) {
    var items = new ArrayList<DereferencedItem>();
    rows.forEach(row -> items.add(toDereferencedItem(row)));
    return items;
  }

  private DereferencedItem toDereferencedItem(Row row) {
    var itemJson = (JsonObject) row.getJson("item");
    DEREFERENCED_ID_FIELDS.forEach(itemJson::remove);
    DEREFERENCED_FIELDS.forEach(fieldName -> {
      var value = row.getJson(row.getColumnIndex(fieldName.toLowerCase()));
      if (value != null) {
        itemJson.put(fieldName, value);
      }
    });
    return readValue(itemJson.toString(), DereferencedItem.class);
  }
}
