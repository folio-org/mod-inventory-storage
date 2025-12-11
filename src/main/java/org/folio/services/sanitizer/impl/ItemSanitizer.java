package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.Item;
import org.folio.services.sanitizer.Sanitizer;
import org.jspecify.annotations.Nullable;

public final class ItemSanitizer implements Sanitizer<Item> {

  @Override
  public void sanitize(@Nullable Item item) {
    if (item == null) {
      return;
    }
    item.setAdministrativeNotes(cleanList(item.getAdministrativeNotes()));
    item.setStatisticalCodeIds(cleanSet(item.getStatisticalCodeIds()));
  }
}
