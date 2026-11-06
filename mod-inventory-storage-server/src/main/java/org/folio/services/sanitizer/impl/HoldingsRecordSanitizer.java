package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.sanitizer.Sanitizer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class HoldingsRecordSanitizer implements Sanitizer<HoldingsRecord> {

  private final Sanitizer<Tags> tagsSanitizer;

  public HoldingsRecordSanitizer(Sanitizer<Tags> tagsSanitizer) {
    this.tagsSanitizer = tagsSanitizer;
  }

  @Override
  public void sanitize(@Nullable HoldingsRecord holdings) {
    if (holdings == null) {
      return;
    }
    holdings.setAdministrativeNotes(cleanList(holdings.getAdministrativeNotes()));
    holdings.setStatisticalCodeIds(cleanSet(holdings.getStatisticalCodeIds()));
    holdings.setFormerIds(cleanSet(holdings.getFormerIds()));

    var tags = holdings.getTags();
    tagsSanitizer.sanitize(tags);
    holdings.setTags(tags);
  }
}
