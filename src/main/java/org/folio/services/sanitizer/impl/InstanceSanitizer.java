package org.folio.services.sanitizer.impl;

import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.sanitizer.SanitizerFactory;
import org.jspecify.annotations.Nullable;

public final class InstanceSanitizer implements Sanitizer<Instance> {

  private Sanitizer<Tags> tagsSanitizer;

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
    getTagsSanitizer().sanitize(tags);
    instance.setTags(tags);
  }

  private Sanitizer<Tags> getTagsSanitizer() {
    if (tagsSanitizer == null) {
      tagsSanitizer = SanitizerFactory.getSanitizer(Tags.class);
    }
    return tagsSanitizer;
  }
}
