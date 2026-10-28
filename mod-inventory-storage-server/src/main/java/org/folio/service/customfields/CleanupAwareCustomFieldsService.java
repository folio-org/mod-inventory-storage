package org.folio.service.customfields;

import static java.util.stream.Collectors.toMap;
import static org.folio.service.CustomFieldUtils.extractOptionIds;
import static org.folio.services.customfields.CustomFieldsValueCleanup.PLACEHOLDER_ENTITY_TYPE;
import static org.folio.services.record.RecordServiceFactoryImpl.ENTITY_TYPE_TABLES;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.folio.common.OkapiParams;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldCollection;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.jaxrs.model.SelectFieldOption;
import org.folio.service.CustomFieldsService;
import org.folio.service.CustomFieldsServiceImpl;
import org.folio.service.RecordService;
import org.folio.service.exc.InvalidFieldValueException;
import org.folio.service.exc.ServiceExceptions;
import org.folio.services.customfields.CustomFieldsValueCleanup;
import org.folio.validate.definition.EntityTypeValidator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNotNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.ModifierSet;

/**
 * Hides the refId placeholders of deleted fields from the API, starts the value cleanup after field
 * deletes, and refuses to remove an option that items hold. The refusal has to come before the library
 * writes: a single-field update commits before the library calls the RecordService. The package is
 * dictated by the library's component scan ({@code org.folio.service}).
 */
@Service
@Primary
public class CleanupAwareCustomFieldsService implements CustomFieldsService {

  private static final String OPTION_IN_USE =
    "Option '%s' of custom field '%s' is used by %d item(s) and cannot be removed";

  private final CustomFieldsServiceImpl delegate;
  private final RecordService recordService;
  private final Vertx vertx;

  public CleanupAwareCustomFieldsService(CustomFieldsServiceImpl delegate, RecordService recordService,
                                         Vertx vertx) {
    this.delegate = delegate;
    this.recordService = recordService;
    this.vertx = vertx;
  }

  @Override
  public Future<CustomField> save(CustomField customField, OkapiParams params) {
    return delegate.save(customField, params);
  }

  @Override
  public Future<Void> update(String id, CustomField customField, OkapiParams params) {
    var tenantId = params.getTenant();
    return findById(id, tenantId)
      .compose(existing -> refuseRemovedOptionsInUse(existing, customField, tenantId))
      .compose(v -> delegate.update(id, customField, params));
  }

  @Override
  public Future<CustomField> findById(String id, String tenantId) {
    return delegate.findById(id, tenantId)
      .compose(field -> PLACEHOLDER_ENTITY_TYPE.equals(field.getEntityType())
                        ? Future.failedFuture(ServiceExceptions.notFound(CustomField.class, id))
                        : Future.succeededFuture(field));
  }

  @Override
  public Future<CustomFieldCollection> findByQuery(String query, int offset, int limit, String lang,
                                                   String tenantId) {
    return delegate.findByQuery(withoutPlaceholders(query), offset, limit, lang, tenantId);
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    return findById(id, tenantId)
      .compose(field -> delegate.delete(id, tenantId))
      .onSuccess(v -> CustomFieldsValueCleanup.kick(vertx, tenantId));
  }

  /**
   * The library validates the entity type of the listed fields only, so an empty list of the
   * placeholder entity type would delete the placeholders and release their refIds early.
   */
  @Override
  public Future<List<CustomField>> replaceAll(List<CustomField> newFields, String entityType,
                                              OkapiParams params) {
    if (!ENTITY_TYPE_TABLES.containsKey(entityType)) {
      return Future.failedFuture(new InvalidFieldValueException("entityType", entityType,
        EntityTypeValidator.unsupportedMessage(entityType)));
    }
    var tenantId = params.getTenant();
    return delegate.findByQuery("entityType==\"" + entityType + "\"", 0, Integer.MAX_VALUE, null, tenantId)
      .compose(existing -> {
        Map<String, CustomField> existingById = existing.getCustomFields().stream()
          .collect(toMap(CustomField::getId, Function.identity()));
        Future<Void> result = Future.succeededFuture();
        for (var newField : newFields) {
          var current = existingById.get(newField.getId());
          if (current != null) {
            result = result.compose(v -> refuseRemovedOptionsInUse(current, newField, tenantId));
          }
        }
        return result;
      })
      .compose(v -> delegate.replaceAll(newFields, entityType, params))
      .onSuccess(v -> CustomFieldsValueCleanup.kick(vertx, tenantId));
  }

  @Override
  public Future<CustomFieldStatistic> retrieveStatistic(String id, String tenantId) {
    return findById(id, tenantId)
      .compose(field -> delegate.retrieveStatistic(id, tenantId));
  }

  @Override
  public Future<CustomFieldOptionStatistic> retrieveOptionStatistic(String id, String optId, String tenantId) {
    return findById(id, tenantId)
      .compose(field -> delegate.retrieveOptionStatistic(id, optId, tenantId));
  }

  /**
   * Uses the ids in the request: the library gives a new option the id of a removed highest option
   * and then does not see the removal.
   */
  private Future<Void> refuseRemovedOptionsInUse(CustomField existing, CustomField replacement, String tenantId) {
    var keptOptionIds = new HashSet<>(optionIds(replacement));
    Future<Void> result = Future.succeededFuture();
    for (var optionId : optionIds(existing)) {
      if (!keptOptionIds.contains(optionId)) {
        result = result.compose(v -> refuseOptionInUse(existing, optionId, tenantId));
      }
    }
    return result;
  }

  private Future<Void> refuseOptionInUse(CustomField field, String optionId, String tenantId) {
    return recordService.retrieveOptionStatistic(field, optionId, tenantId)
      .compose(statistic -> {
        var count = statistic.getCount();
        if (count != null && count > 0) {
          return Future.failedFuture(new InvalidFieldValueException("selectField.options.values", optionId,
            OPTION_IN_USE.formatted(optionValue(field, optionId), field.getName(), count)));
        }
        return Future.succeededFuture();
      });
  }

  private static List<String> optionIds(CustomField field) {
    if (field.getSelectField() == null || field.getSelectField().getOptions() == null) {
      return List.of();
    }
    return extractOptionIds(field).stream()
      .filter(StringUtils::isNotBlank)
      .toList();
  }

  private static String optionValue(CustomField field, String optionId) {
    return field.getSelectField().getOptions().getValues().stream()
      .filter(option -> optionId.equals(option.getId()))
      .map(SelectFieldOption::getValue)
      .findFirst()
      .orElse(optionId);
  }

  /** A query the parser rejects goes on unchanged, for the library to refuse. */
  private static String withoutPlaceholders(String query) {
    CQLNode node;
    try {
      node = new CQLParser(CQLParser.V1POINT2).parse(StringUtils.isBlank(query) ? "cql.allRecords=1" : query);
    } catch (CQLParseException | IOException e) {
      return query;
    }
    var placeholders = new CQLTermNode("entityType", new CQLRelation("=="), PLACEHOLDER_ENTITY_TYPE);
    if (node instanceof CQLSortNode sortNode) {
      var filtered = new CQLSortNode(new CQLNotNode(sortNode.getSubtree(), placeholders, new ModifierSet("not")));
      sortNode.getSortIndexes().forEach(filtered::addSortIndex);
      return filtered.toCQL();
    }
    return new CQLNotNode(node, placeholders, new ModifierSet("not")).toCQL();
  }
}
