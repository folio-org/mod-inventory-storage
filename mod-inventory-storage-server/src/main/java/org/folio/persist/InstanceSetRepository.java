package org.folio.persist;

import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.persist.entity.InstanceSetParts;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.springframework.stereotype.Repository;

/**
 * Reads instances together with their holdings records, items, titles and relationships from the
 * {@code instance_set} view.
 */
@Repository
public class InstanceSetRepository extends AbstractRepository<Instance> {

  private static final String INSTANCE_SET_VIEW = "instance_set";

  public InstanceSetRepository(Context context) {
    super(context, INSTANCE_TABLE, Instance.class);
  }

  /**
   * Returns the JSON {@code {"instanceSets":[...]}} of the instances matching the CQL, each with the parts
   * that are requested. An invalid CQL is reported as {@link BadRequestException}.
   */
  public Future<String> getInstanceSet(String query, InstanceSetParts parts, int offset, int limit, String tenantId) {
    try {
      var sql = buildInstanceSetQuery(parts, offset, limit, query, tenantId);

      return postgresClient(tenantId).select(sql)
        .map(this::buildInstanceSetJson);
    } catch (CQLQueryValidationException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private String buildInstanceSetQuery(InstanceSetParts parts, int offset, int limit, String query, String tenantId) {
    var sql = new StringBuilder(200);
    sql.append("SELECT jsonb_build_object('id', id");

    appendInstanceSetFields(sql, parts);

    sql.append(")::text FROM ")
      .append(getFullTableName(INSTANCE_SET_VIEW, tenantId))
      .append(" JOIN ")
      .append(getFullTableName(tenantId))
      .append(" USING (id) ");

    var cqlWrapper = new CQLWrapper(cql2PgJson, query, limit, offset, "none");
    sql.append(cqlWrapper);

    return sql.toString();
  }

  private void appendInstanceSetFields(StringBuilder sql, InstanceSetParts parts) {
    if (parts.instance()) {
      sql.append(", 'instance', jsonb");
    }
    if (parts.holdingsRecords()) {
      sql.append(", 'holdingsRecords', holdings_records");
    }
    if (parts.items()) {
      sql.append(", 'items', items");
    }
    if (parts.precedingTitles()) {
      sql.append(", 'precedingTitles', preceding_titles");
    }
    if (parts.succeedingTitles()) {
      sql.append(", 'succeedingTitles', succeeding_titles");
    }
    if (parts.superInstanceRelationships()) {
      sql.append(", 'superInstanceRelationships', super_instance_relationships");
    }
    if (parts.subInstanceRelationships()) {
      sql.append(", 'subInstanceRelationships', sub_instance_relationships");
    }
  }

  private String buildInstanceSetJson(RowSet<Row> rowSet) {
    StringBuilder json = new StringBuilder("{\"instanceSets\":[\n");
    boolean first = true;
    for (Row row : rowSet) {
      if (first) {
        first = false;
      } else {
        json.append(",\n");
      }
      json.append(row.getString(0));
    }
    json.append("\n]}");
    return json.toString();
  }
}
