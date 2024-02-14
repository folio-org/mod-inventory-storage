package org.folio.services.instance;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Publication;
import org.folio.rest.jaxrs.model.PublicationPeriod;
import org.folio.services.batch.BatchOperationContext;
import org.junit.Test;

public class InstanceEffectiveValuesServiceTest {
  private final InstanceEffectiveValuesService service = new InstanceEffectiveValuesService();

  @Test
  public void shouldPopulatePeriodOnCreate() {
    var instance = createInstance("2020");

    service.populateEffectiveValues(instance);

    assertThat(instance.getPublicationPeriod(), notNullValue());
    assertThat(instance.getPublicationPeriod().getStart(), is(2020));
  }

  @Test
  public void shouldNotPopulatePeriodOnCreateIfAlreadySet() {
    var instance = createInstance("2020")
      .withPublicationPeriod(new PublicationPeriod().withEnd(2021));

    service.populateEffectiveValues(instance);

    assertThat(instance.getPublicationPeriod(), notNullValue());
    assertThat(instance.getPublicationPeriod().getStart(), nullValue());
    assertThat(instance.getPublicationPeriod().getEnd(), is(2021));
  }

  @Test
  public void shouldPopulatePeriodOnUpdateWhenNewPublicationRemoved() {
    var newInstance = createInstanceNoPublications();
    var oldInstance = createInstance("2020");

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), nullValue());
  }

  @Test
  public void shouldPopulatePeriodOnUpdateWhenNewPublicationSet() {
    var newInstance = createInstance("2020");
    var oldInstance = createInstanceNoPublications();

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), notNullValue());
    assertThat(newInstance.getPublicationPeriod().getStart(), is(2020));
  }

  @Test
  public void shouldNotPopulatePeriodWhenNoPublications() {
    var newInstance = createInstanceNoPublications();
    var oldInstance = createInstanceNoPublications();

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), nullValue());
  }

  @Test
  public void shouldPopulatePeriodWhenNewPublicationAdded() {
    var newInstance = createInstance("2020", "2021");
    var oldInstance = createInstance("2020");

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), notNullValue());
    assertThat(newInstance.getPublicationPeriod().getStart(), is(2020));
    assertThat(newInstance.getPublicationPeriod().getEnd(), is(2021));
  }

  @Test
  public void shouldNotPopulatePeriodWhenPublicationsEmpty() {
    var newInstance = createInstance();
    var oldInstance = createInstance();

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), nullValue());
  }

  @Test
  public void shouldPopulatePeriodWhenFirstDateOfPublicationChanged() {
    var newInstance = createInstance("2019", "2021");
    var oldInstance = createInstance("2020", "2021");

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), notNullValue());
    assertThat(newInstance.getPublicationPeriod().getStart(), is(2019));
    assertThat(newInstance.getPublicationPeriod().getEnd(), is(2021));
  }

  @Test
  public void shouldPopulatePeriodWhenDateOfPublicationChanged() {
    var newInstance = createInstance("2019");
    var oldInstance = createInstance("2020");

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), notNullValue());
    assertThat(newInstance.getPublicationPeriod().getStart(), is(2019));
    assertThat(newInstance.getPublicationPeriod().getEnd(), is(nullValue()));
  }

  @Test
  public void shouldPopulatePeriodWhenLastDateOfPublicationChanged() {
    var newInstance = createInstance("2020", "2023");
    var oldInstance = createInstance("2020", "2021");

    service.populateEffectiveValues(newInstance, oldInstance);

    assertThat(newInstance.getPublicationPeriod(), notNullValue());
    assertThat(newInstance.getPublicationPeriod().getStart(), is(2020));
    assertThat(newInstance.getPublicationPeriod().getEnd(), is(2023));
  }

  @Test
  public void shouldPopulatePeriodOnlyWhenDateIsChangedForBatch() {
    var existingInstanceChanged = createInstance("2020");
    var existingInstanceNotChanged = createInstance("2021");
    var instanceToCreateWithDefinedPeriod = createInstance("1999")
      .withPublicationPeriod(new PublicationPeriod().withEnd(2000));
    var instanceToCreateWithNotDefinedPeriod = createInstance("1994");

    var batchOperationContext = new BatchOperationContext<>(
      List.of(instanceToCreateWithDefinedPeriod, instanceToCreateWithNotDefinedPeriod),
      List.of(createInstance("1980").withId(existingInstanceChanged.getId()),
        existingInstanceNotChanged));

    service.populateEffectiveValues(List.of(existingInstanceChanged,
      existingInstanceNotChanged, instanceToCreateWithDefinedPeriod,
      instanceToCreateWithNotDefinedPeriod), batchOperationContext);

    assertThat(existingInstanceChanged.getPublicationPeriod().getStart(), is(2020));
    assertThat(existingInstanceNotChanged.getPublicationPeriod(), nullValue());
    assertThat(instanceToCreateWithDefinedPeriod.getPublicationPeriod().getEnd(), is(2000));
    assertThat(instanceToCreateWithNotDefinedPeriod.getPublicationPeriod().getStart(), is(1994));
  }

  private Instance createInstance(String... datesOfPublications) {
    var publications = Stream.of(datesOfPublications)
      .map(dateOfPublication -> new Publication().withDateOfPublication(dateOfPublication))
      .toList();

    return new Instance().withPublication(publications).withId(randomUUID().toString());
  }

  private Instance createInstanceNoPublications() {
    return new Instance().withPublication(null);
  }
}
