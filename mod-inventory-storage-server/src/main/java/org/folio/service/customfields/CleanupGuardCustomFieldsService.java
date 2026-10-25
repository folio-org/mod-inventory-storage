package org.folio.service.customfields;

import static java.util.stream.Collectors.toMap;
import static org.folio.service.CustomFieldUtils.extractOptionIds;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.ws.rs.BadRequestException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.folio.common.OkapiParams;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldCollection;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.jaxrs.model.SelectFieldOption;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.CustomFieldsService;
import org.folio.service.CustomFieldsServiceImpl;
import org.folio.service.RecordService;
import org.folio.service.exc.InvalidFieldValueException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Guards the custom field definitions against the refId reuse of folio-custom-fields while queued
 * value cleanups are pending: a new field with the name of a deleted one gets the deleted field's
 * refId, so it would silently adopt the values that
 * {@link org.folio.services.customfields.CustomFieldsValueCleanup} has not removed yet. Creating such
 * a field is refused until the cleanup is done, with 400 because the shared interface declares it on
 * every write endpoint. Removing an option is refused while items hold it, before the library writes
 * anything, so a removed option leaves no values to clean up; 422 with the library's error JSON, as
 * its own definition checks answer. The package is dictated by the library's component scan
 * ({@code org.folio.service}).
 */
@Component
@Primary
public class CleanupGuardCustomFieldsService implements CustomFieldsService {

  private static final String NAME_PENDING =
    "Custom field '%s' cannot be created while the values of a deleted custom field with the same name"
      + " are still being removed";
  private static final String RECREATE_IN_REQUEST =
    "Custom field '%s' cannot be deleted and recreated in one request";
  private static final String OPTION_IN_USE =
    "Option '%s' of custom field '%s' is used by %d item(s) and cannot be removed";

  private final CustomFieldsServiceImpl delegate;
  private final RecordService recordService;
  private final Vertx vertx;

  public CleanupGuardCustomFieldsService(CustomFieldsServiceImpl delegate, RecordService recordService,
                                         Vertx vertx) {
    this.delegate = delegate;
    this.recordService = recordService;
    this.vertx = vertx;
  }

  @Override
  public Future<CustomField> save(CustomField customField, OkapiParams params) {
    return pendingRefIds(params.getTenant())
      .compose(pending -> refusePendingName(customField.getName(), pending))
      .compose(v -> delegate.save(customField, params));
  }

  @Override
  public Future<Void> update(String id, CustomField customField, OkapiParams params) {
    return delegate.findById(id, params.getTenant())
      .compose(existing -> refuseRemovedOptionsInUse(existing, customField, params.getTenant()))
      .compose(v -> delegate.update(id, customField, params));
  }

  @Override
  public Future<CustomField> findById(String id, String tenantId) {
    return delegate.findById(id, tenantId);
  }

  @Override
  public Future<CustomFieldCollection> findByQuery(String query, int offset, int limit, String lang,
                                                   String tenantId) {
    return delegate.findByQuery(query, offset, limit, lang, tenantId);
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    return delegate.delete(id, tenantId);
  }

  @Override
  public Future<List<CustomField>> replaceAll(List<CustomField> newFields, String entityType,
                                              OkapiParams params) {
    var tenantId = params.getTenant();
    return delegate.findByQuery("entityType==\"" + entityType + "\"", 0, Integer.MAX_VALUE, null, tenantId)
      .compose(existing -> pendingRefIds(tenantId)
        .compose(pending -> refuseReplaceAllHazards(existing.getCustomFields(), newFields, pending, tenantId)))
      .compose(v -> delegate.replaceAll(newFields, entityType, params));
  }

  @Override
  public Future<CustomFieldStatistic> retrieveStatistic(String id, String tenantId) {
    return delegate.retrieveStatistic(id, tenantId);
  }

  @Override
  public Future<CustomFieldOptionStatistic> retrieveOptionStatistic(String id, String optId, String tenantId) {
    return delegate.retrieveOptionStatistic(id, optId, tenantId);
  }

  private Future<Void> refuseReplaceAllHazards(List<CustomField> existingFields, List<CustomField> newFields,
                                               List<String> pending, String tenantId) {
    Map<String, CustomField> existingById = existingFields.stream()
      .collect(toMap(CustomField::getId, Function.identity()));
    Set<String> newIds = new HashSet<>();
    newFields.stream().map(CustomField::getId).filter(StringUtils::isNotBlank).forEach(newIds::add);
    List<String> removedRefIds = existingFields.stream()
      .filter(field -> !newIds.contains(field.getId()))
      .map(CustomField::getRefId)
      .toList();

    Future<Void> result = Future.succeededFuture();
    for (var newField : newFields) {
      var existing = StringUtils.isBlank(newField.getId()) ? null : existingById.get(newField.getId());
      if (existing == null) {
        var base = unAccentName(newField.getName());
        if (removedRefIds.stream().anyMatch(refId -> refIdBase(refId).equals(base))) {
          return Future.failedFuture(new BadRequestException(RECREATE_IN_REQUEST.formatted(newField.getName())));
        }
        result = result.compose(v -> refusePendingName(newField.getName(), pending));
      } else {
        result = result.compose(v -> refuseRemovedOptionsInUse(existing, newField, tenantId));
      }
    }
    return result;
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

  private static Future<Void> refusePendingName(String name, List<String> pending) {
    var base = unAccentName(name);
    if (pending.stream().anyMatch(refId -> refIdBase(refId).equals(base))) {
      return Future.failedFuture(new BadRequestException(NAME_PENDING.formatted(name)));
    }
    return Future.succeededFuture();
  }

  private Future<List<String>> pendingRefIds(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId)
      .select("SELECT ref_id FROM " + PostgresClient.convertToPsqlStandard(tenantId) + ".custom_fields_cleanup")
      .map(rows -> {
        var pending = new ArrayList<String>();
        rows.forEach(row -> pending.add(row.getString("ref_id")));
        return List.copyOf(pending);
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

  /** The refId without the suffix the library appends for repeated names. */
  private static String refIdBase(String refId) {
    return refId.replaceFirst("_\\d+$", "");
  }

  /**
   * Copy of the library's private {@code CustomFieldsServiceImpl.unAccentName}, which derives the
   * refId from the field name; must stay identical so the guard predicts the refId a new field
   * would get.
   */
  private static String unAccentName(String customFieldName) {
    final String capitalizedString = WordUtils.capitalizeFully(customFieldName);
    final String splitString = Normalizer.normalize(capitalizedString, Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
      .replaceAll("[^a-zA-Z\\s]", "")
      .replaceAll("\\s+", "");

    final String result = StringUtils.uncapitalize(splitString);
    return StringUtils.isBlank(result)
      ? Base64.getEncoder().withoutPadding().encodeToString(customFieldName.getBytes(StandardCharsets.UTF_8))
      : result;
  }
}
