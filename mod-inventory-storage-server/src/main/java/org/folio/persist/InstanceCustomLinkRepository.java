package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.instance.InstanceCustomLinkService.INSTANCE_CUSTOM_LINK_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.Conn;

public class InstanceCustomLinkRepository extends AbstractRepository<InstanceCustomLink> {
  private static final int MAXIMUM_LINK_COUNT = 10;

  private static final String ON_CREATE_QUERY = """
    SELECT
      COUNT(*) AS total_count,
      COUNT(*) FILTER (WHERE lower(f_unaccent(jsonb ->> 'name')) = lower(f_unaccent($1))) AS name_count,
      COUNT(*) FILTER (WHERE lower(f_unaccent(jsonb ->> 'linkText')) = lower(f_unaccent($2))) AS linktext_count,
      COUNT(*) FILTER (WHERE lower(f_unaccent(jsonb ->> 'link')) = lower(f_unaccent($3))) AS link_count
    FROM
      """ + INSTANCE_CUSTOM_LINK_TABLE;

  private static final String ON_MODIFY_QUERY = """
    SELECT
      COUNT(*) FILTER (WHERE lower(f_unaccent(jsonb ->> 'name')) = lower(f_unaccent($1)) AND id != $4) AS name_count,
      COUNT(*) FILTER (WHERE
        lower(f_unaccent(jsonb ->> 'linkText')) = lower(f_unaccent($2))
        AND id != $4
      ) AS linktext_count,
      COUNT(*) FILTER (WHERE
        lower(f_unaccent(jsonb ->> 'link')) = lower(f_unaccent($3))
        AND id != $4
      ) AS link_count
    FROM
      """ + INSTANCE_CUSTOM_LINK_TABLE;

  public InstanceCustomLinkRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_CUSTOM_LINK_TABLE, InstanceCustomLink.class);
  }

  public Future<String> create(Conn conn, InstanceCustomLink entity) {
    return validateAndSave(conn, null, entity, true);
  }

  public Future<String> modify(Conn conn, String id, InstanceCustomLink entity) {
    return validateAndSave(conn, id, entity, false);
  }

  private Future<String> validateAndSave(Conn conn, String id, InstanceCustomLink entity, boolean create) {
    var queryTuple = Tuple.of(entity.getName(), entity.getLinkText(), entity.getLink());
    if (!create) {
      queryTuple.addString(id);
    }
    return conn
      .execute(String.format("LOCK TABLE %s IN EXCLUSIVE MODE", INSTANCE_CUSTOM_LINK_TABLE))
      .compose(x -> conn.execute(create ? ON_CREATE_QUERY : ON_MODIFY_QUERY, queryTuple))
      .compose(rowSet -> {
        var row = rowSet.iterator().next();
        var errors = checkUniqueness(row, entity);
        if (create && row.getInteger("total_count") >= MAXIMUM_LINK_COUNT) {
          errors.add(new Error().withCode("maximumCount")
            .withMessage(String.format("Maximum limit of %d instance custom links reached", MAXIMUM_LINK_COUNT)));
        }
        if (!errors.isEmpty()) {
          return Future.failedFuture(new ValidationException(new Errors().withErrors(errors)));
        }
        if (create) {
          return conn.save(INSTANCE_CUSTOM_LINK_TABLE, entity);
        } else {
          return conn.update(INSTANCE_CUSTOM_LINK_TABLE, entity, id).map(y -> id);
        }
      });
  }

  private ArrayList<Error> checkUniqueness(Row row, InstanceCustomLink entity) {
    var errors = new ArrayList<Error>();
    if (row.getInteger("name_count") > 0) {
      errors.add(uniqueError("name", entity.getName()));
    }
    if (row.getInteger("linktext_count") > 0) {
      errors.add(uniqueError("linkText", entity.getLinkText()));
    }
    if (row.getInteger("link_count") > 0) {
      errors.add(uniqueError("link", entity.getLink()));
    }
    return errors;
  }

  private Error uniqueError(String field, String value) {
    return new Error()
      .withCode("unique")
      .withMessage(String.format("%s must be unique, but %s already exists", field, value))
      .withParameters(List.of(
        new Parameter()
          .withKey(field)
          .withValue(value)
      ));
  }
}
