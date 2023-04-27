package org.folio.services.instance;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.services.instance.PublicationPeriodParser.parsePublicationPeriod;

import java.util.List;
import java.util.Objects;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Publication;
import org.folio.services.batch.BatchOperationContext;

public class InstanceEffectiveValuesService {

  public Instance populateEffectiveValues(Instance instance) {
    if (instance.getPublicationPeriod() == null) {
      populatePublicationPeriod(instance);
    }

    return instance;
  }

  public void populateEffectiveValues(Instance newInstance, Instance oldInstance) {
    if (instancePublicationsChanged(newInstance, oldInstance)) {
      populatePublicationPeriod(newInstance);
    }
  }

  public void populateEffectiveValues(List<Instance> allInstances,
                                      BatchOperationContext<Instance> context) {

    var existingInstancesMap = context.getExistingRecords().stream()
      .collect(toMap(Instance::getId, identity()));

    for (Instance newInstance : allInstances) {
      var instanceId = newInstance.getId();
      if (existingInstancesMap.containsKey(instanceId)) {
        populateEffectiveValues(newInstance, existingInstancesMap.get(instanceId));
      } else {
        populateEffectiveValues(newInstance);
      }
    }
  }

  public void populatePublicationPeriod(Instance instance) {
    instance.withPublicationPeriod(parsePublicationPeriod(instance.getPublication()));
  }

  private boolean instancePublicationsChanged(Instance newInstance, Instance oldInstance) {
    var newPublications = newInstance.getPublication();
    var oldPublications = oldInstance.getPublication();

    if (newPublications == null || oldPublications == null) {
      return true;
    }

    if (newPublications.size() != oldPublications.size()) {
      return true;
    }

    if (newPublications.isEmpty()) {
      return false;
    }

    var lastPublicationIndex = newPublications.size() - 1;
    return dateOfPublicationChanged(newPublications.get(0), oldPublications.get(0))
      || dateOfPublicationChanged(newPublications.get(lastPublicationIndex),
      oldPublications.get(lastPublicationIndex));
  }

  private boolean dateOfPublicationChanged(Publication first, Publication second) {
    return !Objects.equals(first.getDateOfPublication(), second.getDateOfPublication());
  }
}
