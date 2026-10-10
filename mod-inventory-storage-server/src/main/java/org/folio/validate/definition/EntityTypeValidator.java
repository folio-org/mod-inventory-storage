package org.folio.validate.definition;

import static java.util.stream.Collectors.joining;
import static org.folio.services.record.RecordServiceFactoryImpl.ENTITY_TYPE_TABLES;

import org.folio.rest.jaxrs.model.CustomField;
import org.springframework.stereotype.Component;

/**
 * Refuses custom fields whose entity type has no table in
 * {@link org.folio.services.record.RecordServiceFactoryImpl}: the library would store such a
 * definition silently, with no table holding values and statistics reading 0. The package is
 * dictated by the library's component scan ({@code org.folio.validate}).
 */
@Component
public class EntityTypeValidator implements Validatable {

  private static final String UNSUPPORTED_ENTITY_TYPE = "Unsupported entityType '%s', supported: %s";

  @Override
  public boolean isApplicable(CustomField fieldDefinition) {
    return true;
  }

  @Override
  public void validateDefinition(CustomField fieldDefinition) {
    var entityType = fieldDefinition.getEntityType();
    if (entityType == null || !ENTITY_TYPE_TABLES.containsKey(entityType)) {
      throw new IllegalArgumentException(UNSUPPORTED_ENTITY_TYPE.formatted(entityType,
        ENTITY_TYPE_TABLES.keySet().stream().sorted().collect(joining(", "))));
    }
  }
}
