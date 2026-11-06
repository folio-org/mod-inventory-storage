package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.sanitizer.Sanitizer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class InstanceSanitizer implements Sanitizer<Instance> {

  private final Sanitizer<Tags> tagsSanitizer;

  public InstanceSanitizer(Sanitizer<Tags> tagsSanitizer) {
    this.tagsSanitizer = tagsSanitizer;
  }

  @Override
  public void sanitize(@Nullable Instance instance) {
    if (instance == null) {
      return;
    }
    instance.setInstanceFormatIds(cleanList(instance.getInstanceFormatIds()));
    instance.setPhysicalDescriptions(cleanList(instance.getPhysicalDescriptions()));
    instance.setLanguages(cleanList(instance.getLanguages()));
    instance.setAdministrativeNotes(cleanList(instance.getAdministrativeNotes()));
    instance.setEditions(cleanSet(instance.getEditions()));
    instance.setPublicationRange(cleanSet(instance.getPublicationRange()));
    instance.setPublicationFrequency(cleanSet(instance.getPublicationFrequency()));
    instance.setNatureOfContentTermIds(cleanSet(instance.getNatureOfContentTermIds()));
    instance.setStatisticalCodeIds(cleanSet(instance.getStatisticalCodeIds()));

    var tags = instance.getTags();
    tagsSanitizer.sanitize(tags);
    instance.setTags(tags);
  }
}
