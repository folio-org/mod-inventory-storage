package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.sanitizer.SanitizerFactory;
import org.jspecify.annotations.Nullable;

public final class ItemSanitizer implements Sanitizer<Item> {

  private Sanitizer<Tags> tagsSanitizer;

  @Override
  public void sanitize(@Nullable Item item) {
    if (item == null) {
      return;
    }
    item.setAdministrativeNotes(cleanList(item.getAdministrativeNotes()));
    item.setStatisticalCodeIds(cleanSet(item.getStatisticalCodeIds()));
    item.setYearCaption(cleanSet(item.getYearCaption()));

    var tags = item.getTags();
    getTagsSanitizer().sanitize(tags);
    item.setTags(tags);
  }

  private Sanitizer<Tags> getTagsSanitizer() {
    if (tagsSanitizer == null) {
      tagsSanitizer = SanitizerFactory.getSanitizer(Tags.class);
    }
    return tagsSanitizer;
  }
}
