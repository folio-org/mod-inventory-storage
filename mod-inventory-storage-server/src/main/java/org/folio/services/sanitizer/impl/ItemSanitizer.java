package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.sanitizer.Sanitizer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class ItemSanitizer implements Sanitizer<Item> {

  private final Sanitizer<Tags> tagsSanitizer;

  public ItemSanitizer(Sanitizer<Tags> tagsSanitizer) {
    this.tagsSanitizer = tagsSanitizer;
  }

  @Override
  public void sanitize(@Nullable Item item) {
    if (item == null) {
      return;
    }
    item.setAdministrativeNotes(cleanList(item.getAdministrativeNotes()));
    item.setStatisticalCodeIds(cleanSet(item.getStatisticalCodeIds()));
    item.setFormerIds(cleanSet(item.getFormerIds()));
    item.setYearCaption(cleanSet(item.getYearCaption()));

    var tags = item.getTags();
    tagsSanitizer.sanitize(tags);
    item.setTags(tags);
  }
}
