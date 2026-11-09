package org.folio.persist;

import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.folio.persist.entity.UpdatedInstanceIdsParams;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;
import org.springframework.stereotype.Repository;

/**
 * Streams for the inventory hierarchy, the view of the instances for harvesting, for example by OAI-PMH.
 */
@Repository
public class InventoryHierarchyRepository extends AbstractRepository<Instance> {
  private static final String UPDATED_INSTANCE_IDS_SQL =
    "select * from get_updated_instance_ids_view($1,$2,$3,$4,$5,$6);";
  private static final String ITEMS_AND_HOLDINGS_SQL = "select * from get_items_and_holdings_view($1,$2);";
  private static final String NOT_SUPPRESSED_FILTER = "(instance.jsonb ->> 'discoverySuppress')::bool = false";
  private static final String INITIAL_LOAD_SQL = """
    SELECT id as "instanceId",
           instance.jsonb ->> 'source' AS source,
           strToTimestamp(instance.jsonb -> 'metadata' ->> 'updatedDate') AS "updatedDate",
           (instance.jsonb ->> 'discoverySuppress')::bool AS "suppressFromDiscovery",
           false AS deleted
    FROM instance
    WHERE (CAST($1 as varchar) IS NULL OR (instance.jsonb ->> 'source')::varchar = $1)
    """;
  private static final String INITIAL_LOAD_DELETED_RECORDS_SQL = """
     UNION ALL
      (SELECT (jsonb #>> '{record,id}')::uuid            AS "instanceId",
            jsonb #>> '{record,source}'                 AS source,
            strToTimestamp(jsonb ->> 'createdDate')     AS "updatedDate",
            false                                       AS "suppressFromDiscovery",
            true                                        AS deleted
      FROM audit_instance
      WHERE (CAST($1 as varchar) IS NULL OR (jsonb ->> 'source')::varchar = $1))
    """;

  public InventoryHierarchyRepository(Context context) {
    super(context, INSTANCE_TABLE, Instance.class);
  }

  /**
   * Streams the ids of the instances, with their source, update date, discovery suppress flag and whether
   * they are deleted. Without a start and an end date these are all instances, otherwise the instances that
   * were changed in the period. An unparsable date is reported as {@link BadRequestException}.
   */
  public Future<Void> streamUpdatedInstanceIds(UpdatedInstanceIdsParams params,
                                               Handler<RowStream<Row>> rowStreamHandler, String tenantId) {
    if (params.isInitialLoad()) {
      return streamInitialLoad(params, rowStreamHandler, tenantId);
    }
    Tuple tuple;
    try {
      tuple = Tuple.tuple()
        .addValue(StringUtils.isEmpty(params.startDate()) ? null : OffsetDateTime.parse(params.startDate()))
        .addValue(StringUtils.isEmpty(params.endDate()) ? null : OffsetDateTime.parse(params.endDate()))
        .addBoolean(params.deletedRecordSupport())
        .addBoolean(params.skipSuppressedFromDiscoveryRecords())
        .addBoolean(params.onlyInstanceUpdateDate())
        .addValue(params.source());
    } catch (Exception e) {
      return Future.failedFuture(new BadRequestException(new IllegalArgumentException(e).getMessage()));
    }
    return selectStream(UPDATED_INSTANCE_IDS_SQL, tuple, rowStreamHandler, tenantId);
  }

  /**
   * Streams the items and holdings records of the instances. An id that is not a UUID is reported as
   * {@link BadRequestException}.
   */
  public Future<Void> streamItemsAndHoldings(List<String> instanceIds, boolean skipSuppressedFromDiscoveryRecords,
                                             Handler<RowStream<Row>> rowStreamHandler, String tenantId) {
    Tuple params;
    try {
      var ids = instanceIds == null ? new UUID[0] : instanceIds.stream().map(UUID::fromString).toArray(UUID[]::new);
      params = Tuple.tuple().addArrayOfUUID(ids).addBoolean(skipSuppressedFromDiscoveryRecords);
    } catch (Exception e) {
      return Future.failedFuture(new BadRequestException(new IllegalArgumentException(e).getMessage()));
    }
    return selectStream(ITEMS_AND_HOLDINGS_SQL, params, rowStreamHandler, tenantId);
  }

  private Future<Void> streamInitialLoad(UpdatedInstanceIdsParams params, Handler<RowStream<Row>> rowStreamHandler,
                                         String tenantId) {
    var sql = INITIAL_LOAD_SQL;
    if (params.skipSuppressedFromDiscoveryRecords()) {
      sql += " AND " + NOT_SUPPRESSED_FILTER;
    }
    if (params.deletedRecordSupport()) {
      sql += INITIAL_LOAD_DELETED_RECORDS_SQL;
    }
    return selectStream(sql, Tuple.tuple().addValue(params.source()), rowStreamHandler, tenantId);
  }
}
