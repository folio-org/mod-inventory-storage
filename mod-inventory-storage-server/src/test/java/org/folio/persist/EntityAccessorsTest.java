package org.folio.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.ParameterizedType;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.persist.entity.NotificationSendingError;
import org.folio.persist.helper.EntityAccessors;
import org.folio.rest.impl.BaseApi;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.model.IdentifierType;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstancePrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceStatus;
import org.folio.rest.jaxrs.model.InstanceView;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.StatisticalCode;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.model.SubjectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EntityAccessorsTest {

  private static final String ID = "11111111-1111-4111-8111-111111111111";

  static Stream<Class<?>> entityTypes() {
    return Stream.of(AsyncMigrationJob.class, BoundWithPart.class, CallNumberType.class,
      ClassificationType.class, HoldingsRecord.class, HridSettings.class, IdentifierType.class, Instance.class,
      InstanceCustomLink.class, InstanceDateType.class, InstancePrecedingSucceedingTitle.class,
      InstanceRelationship.class, InstanceStatus.class, Item.class, IterationJob.class, LoanType.class,
      Location.class, LocationCampus.class, LocationInstitution.class, LocationLibrary.class, MarcJson.class,
      MaterialType.class, NotificationSendingError.class, ReindexJob.class, ServicePoint.class, Setting.class,
      StatisticalCode.class, SubjectSource.class, SubjectType.class);
  }

  @ParameterizedTest
  @MethodSource("entityTypes")
  @DisplayName("should write and read the id when the entity type is registered")
  void shouldWriteAndReadId_whenEntityTypeIsRegistered(Class<Object> type) throws ReflectiveOperationException {
    var accessor = EntityAccessors.of(type);
    var entity = type.getDeclaredConstructor().newInstance();

    accessor.setId(entity, ID);

    assertThat(accessor.id(entity)).isEqualTo(ID);
  }

  @Test
  @DisplayName("should return null id when the entity has no id")
  void shouldReturnNullId_whenEntityHasNoId() {
    assertThat(EntityAccessors.of(Item.class).id(new Item())).isNull();
    assertThat(EntityAccessors.of(Setting.class).id(new Setting())).isNull();
  }

  @Test
  @DisplayName("should store the id as uuid when the entity id is a uuid")
  void shouldStoreIdAsUuid_whenEntityIdIsUuid() {
    var setting = new Setting();

    EntityAccessors.of(Setting.class).setId(setting, ID);

    assertThat(setting.getId()).isEqualTo(UUID.fromString(ID));
  }

  @Test
  @DisplayName("should unset the version when it is minus one")
  void shouldUnsetVersion_whenItIsMinusOne() {
    var item = new Item().withVersion(-1);

    EntityAccessors.of(Item.class).unsetVersionIfMinusOne(item);

    assertThat(item.getVersion()).isNull();
  }

  @Test
  @DisplayName("should keep the version when it is not minus one")
  void shouldKeepVersion_whenItIsNotMinusOne() {
    var holdings = new HoldingsRecord().withVersion(3);

    EntityAccessors.of(HoldingsRecord.class).unsetVersionIfMinusOne(holdings);

    assertThat(holdings.getVersion()).isEqualTo(3);
  }

  @Test
  @DisplayName("should ignore the version when the entity has none")
  void shouldIgnoreVersion_whenEntityHasNone() {
    var location = new Location();

    EntityAccessors.of(Location.class).unsetVersionIfMinusOne(location);

    assertThat(location).isEqualTo(new Location());
  }

  @Test
  @DisplayName("should fail when the id of a view only type is used")
  void shouldFail_whenIdOfViewOnlyTypeIsUsed() {
    var accessor = EntityAccessors.of(InstanceView.class);
    var view = new InstanceView();

    assertThatThrownBy(() -> accessor.id(view)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("should fail when the type is not registered")
  void shouldFail_whenTypeIsNotRegistered() {
    assertThatThrownBy(() -> EntityAccessors.of(Object.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("java.lang.Object");
  }

  @Test
  @DisplayName("should have an accessor for the entity type of every repository and reference data Api")
  void shouldHaveAccessor_whenTypeIsStoredByRepositoryOrReferenceDataApi() {
    var classes = new ClassFileImporter()
      .withImportOption(new ImportOption.DoNotIncludeTests())
      .withImportOption(new ImportOption.DoNotIncludeJars())
      .importPackages("org.folio");

    var types = classes.stream()
      .filter(javaClass -> !javaClass.getModifiers().contains(JavaModifier.ABSTRACT))
      .flatMap(javaClass -> storedEntityTypes(javaClass.reflect()))
      .collect(Collectors.toSet());

    assertThat(types).hasSizeGreaterThan(30);
    types.forEach(type -> assertThatCode(() -> EntityAccessors.of(type)).doesNotThrowAnyException());
  }

  private static Stream<Class<?>> storedEntityTypes(Class<?> clazz) {
    if (!AbstractRepository.class.isAssignableFrom(clazz) && !BaseApi.class.isAssignableFrom(clazz)) {
      return Stream.empty();
    }
    var superclass = clazz.getGenericSuperclass();
    if (superclass instanceof ParameterizedType parameterized
      && parameterized.getActualTypeArguments()[0] instanceof Class<?> entityType) {
      return Stream.of(entityType);
    }
    return Stream.empty();
  }
}
