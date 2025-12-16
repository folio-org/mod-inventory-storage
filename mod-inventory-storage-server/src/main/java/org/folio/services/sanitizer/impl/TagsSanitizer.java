package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.sanitizer.Sanitizer;
import org.jspecify.annotations.Nullable;

public final class TagsSanitizer implements Sanitizer<Tags> {

  @Override
  public void sanitize(@Nullable Tags tags) {
    if (tags == null) {
      return;
    }
    tags.setTagList(cleanList(tags.getTagList()));
  }
}
