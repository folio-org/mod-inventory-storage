package org.folio.services.referencedata;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.folio.persist.AbstractRepository;
import org.folio.persist.ReferenceDataRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.persist.PostgresClientStreamResult;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.stereotype.Service;

/**
 * CRUD for simple reference data tables. One stateless repository is kept per table.
 */
@Service
public class ReferenceDataService {

  private final Context context;
  private final Map<RepositoryKey, AbstractRepository<?>> repositories = new ConcurrentHashMap<>();

  public ReferenceDataService(Context context) {
    this.context = context;
  }

  public <E> Future<Results<E>> query(String table, Class<E> entityClass, String cql, int offset, int limit,
                                      String totalRecords, Map<String, String> headers) {
    return repository(table, entityClass).query(cql, offset, limit, totalRecords, tenantId(headers));
  }

  public <E> Future<PostgresClientStreamResult<E>> stream(String table, Class<E> entityClass, String cql,
                                                          int offset, int limit, Map<String, String> headers) {
    return repository(table, entityClass).stream(cql, offset, limit, tenantId(headers));
  }

  public <E> Future<PostgresClientStreamResult<E>> stream(String table, Class<E> entityClass, CQLWrapper cqlWrapper,
                                                          Map<String, String> headers) {
    return repository(table, entityClass).stream(cqlWrapper, tenantId(headers));
  }

  public <E> Future<E> getById(String table, Class<E> entityClass, String id, Map<String, String> headers) {
    return repository(table, entityClass).findById(id, tenantId(headers));
  }

  public <E> Future<E> create(String table, Class<E> entityClass, E entity, Map<String, String> headers) {
    return repository(table, entityClass).create(entity, tenantId(headers));
  }

  public <E> Future<Void> update(String table, Class<E> entityClass, String id, E entity,
                                 Map<String, String> headers) {
    return repository(table, entityClass).replace(id, entity, tenantId(headers));
  }

  public <E> Future<Void> delete(String table, Class<E> entityClass, String id, Map<String, String> headers) {
    return repository(table, entityClass).remove(id, tenantId(headers));
  }

  /**
   * Deletes the record when all the given conditions hold, the keys of the map are the failure messages.
   */
  public <E> Future<Void> delete(String table, Class<E> entityClass, String id,
                                 Map<String, Predicate<E>> conditions, Map<String, String> headers) {
    return repository(table, entityClass).getById(id, tenantId(headers))
      .recover(e -> Future.failedFuture(new BadRequestException("Failed to fetch entity: " + e.getMessage())))
      .compose(entity -> {
        if (entity == null) {
          return Future.failedFuture(
            new NotFoundException("Entity with id '%s' not found for deletion".formatted(id)));
        }
        for (var condition : conditions.entrySet()) {
          if (!condition.getValue().test(entity)) {
            return Future.failedFuture(new BadRequestException(condition.getKey()));
          }
        }
        return delete(table, entityClass, id, headers);
      });
  }

  public <E> Future<Void> deleteAll(String table, Class<E> entityClass, Map<String, String> headers) {
    return repository(table, entityClass).deleteAll(tenantId(headers)).mapEmpty();
  }

  public <E> Future<Void> upsert(String table, Class<E> entityClass, String id, E entity,
                                 Map<String, String> headers) {
    return repository(table, entityClass).upsert(id, entity, tenantId(headers));
  }

  public <E> Future<Void> deleteIfExists(String table, Class<E> entityClass, String id,
                                         Map<String, String> headers) {
    return repository(table, entityClass).removeIfExists(id, tenantId(headers));
  }

  public <E> Future<Void> deleteByQuery(String table, Class<E> entityClass, String cql,
                                        Map<String, String> headers) {
    return repository(table, entityClass).removeByQuery(cql, tenantId(headers));
  }

  public <E> Future<Void> saveBatch(String table, Class<E> entityClass, List<E> entities,
                                    Map<String, String> headers) {
    return repository(table, entityClass).saveBatch(entities, tenantId(headers));
  }

  @SuppressWarnings("unchecked")
  private <E> AbstractRepository<E> repository(String table, Class<E> entityClass) {
    return (AbstractRepository<E>) repositories.computeIfAbsent(new RepositoryKey(table, entityClass),
      key -> new ReferenceDataRepository<>(context, table, entityClass));
  }

  private record RepositoryKey(String table, Class<?> entityClass) { }
}
