package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.sanitizer.SanitizerFactory;
import org.jspecify.annotations.Nullable;

public final class HoldingsRecordSanitizer implements Sanitizer<HoldingsRecord> {

  private Sanitizer<Tags> tagsSanitizer;

  @Override
  public void sanitize(@Nullable HoldingsRecord holdings) {
    if (holdings == null) {
      return;
    }
    holdings.setAdministrativeNotes(cleanList(holdings.getAdministrativeNotes()));
    holdings.setStatisticalCodeIds(cleanSet(holdings.getStatisticalCodeIds()));
    holdings.setFormerIds(cleanSet(holdings.getFormerIds()));

    var tags = holdings.getTags();
    getTagsSanitizer().sanitize(tags);
    holdings.setTags(tags);
  }

  private Sanitizer<Tags> getTagsSanitizer() {
    if (tagsSanitizer == null) {
      tagsSanitizer = SanitizerFactory.getSanitizer(Tags.class);
    }
    return tagsSanitizer;
  }
}
