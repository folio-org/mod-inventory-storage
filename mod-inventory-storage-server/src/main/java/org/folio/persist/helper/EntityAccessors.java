package org.folio.persist.helper;

import static java.util.Map.entry;

import java.util.Map;
import java.util.UUID;
import org.folio.persist.entity.NotificationSendingError;
import org.folio.rest.jaxrs.model.AlternativeTitleType;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.folio.rest.jaxrs.model.ContributorNameType;
import org.folio.rest.jaxrs.model.ContributorType;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationship;
import org.folio.rest.jaxrs.model.HoldingsNoteType;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.HoldingsType;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.model.IdentifierType;
import org.folio.rest.jaxrs.model.IllPolicy;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstanceFormat;
import org.folio.rest.jaxrs.model.InstanceNoteType;
import org.folio.rest.jaxrs.model.InstancePrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationshipType;
import org.folio.rest.jaxrs.model.InstanceStatus;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.jaxrs.model.InstanceView;
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.folio.rest.jaxrs.model.ItemNoteType;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.jaxrs.model.ServicePointsUser;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.StatisticalCode;
import org.folio.rest.jaxrs.model.StatisticalCodeType;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.model.SubjectType;

/**
 * The {@link EntityAccessor} of each entity type that is stored by a repository. The RMB generated models
 * have no common interface, so the accessors are listed here instead of being found with reflection.
 */
public final class EntityAccessors {

  private static final Map<Class<?>, EntityAccessor<?>> ACCESSORS = Map.ofEntries(
    entry(AlternativeTitleType.class, EntityAccessor.of(AlternativeTitleType::getId, AlternativeTitleType::setId)),
    entry(AsyncMigrationJob.class, EntityAccessor.of(AsyncMigrationJob::getId, AsyncMigrationJob::setId)),
    entry(BoundWithPart.class, EntityAccessor.of(BoundWithPart::getId, BoundWithPart::setId)),
    entry(CallNumberType.class, EntityAccessor.of(CallNumberType::getId, CallNumberType::setId)),
    entry(ClassificationType.class, EntityAccessor.of(ClassificationType::getId, ClassificationType::setId)),
    entry(ContributorNameType.class, EntityAccessor.of(ContributorNameType::getId, ContributorNameType::setId)),
    entry(ContributorType.class, EntityAccessor.of(ContributorType::getId, ContributorType::setId)),
    entry(ElectronicAccessRelationship.class,
      EntityAccessor.of(ElectronicAccessRelationship::getId, ElectronicAccessRelationship::setId)),
    entry(HoldingsNoteType.class, EntityAccessor.of(HoldingsNoteType::getId, HoldingsNoteType::setId)),
    entry(HoldingsRecordsSource.class, EntityAccessor.of(HoldingsRecordsSource::getId, HoldingsRecordsSource::setId)),
    entry(HoldingsType.class, EntityAccessor.of(HoldingsType::getId, HoldingsType::setId)),
    entry(HridSettings.class, EntityAccessor.of(HridSettings::getId, HridSettings::setId)),
    entry(IdentifierType.class, EntityAccessor.of(IdentifierType::getId, IdentifierType::setId)),
    entry(IllPolicy.class, EntityAccessor.of(IllPolicy::getId, IllPolicy::setId)),
    entry(InstanceCustomLink.class, EntityAccessor.of(InstanceCustomLink::getId, InstanceCustomLink::setId)),
    entry(InstanceDateType.class, EntityAccessor.of(InstanceDateType::getId, InstanceDateType::setId)),
    entry(InstanceFormat.class, EntityAccessor.of(InstanceFormat::getId, InstanceFormat::setId)),
    entry(InstanceNoteType.class, EntityAccessor.of(InstanceNoteType::getId, InstanceNoteType::setId)),
    entry(InstancePrecedingSucceedingTitle.class,
      EntityAccessor.of(InstancePrecedingSucceedingTitle::getId, InstancePrecedingSucceedingTitle::setId)),
    entry(InstanceRelationship.class, EntityAccessor.of(InstanceRelationship::getId, InstanceRelationship::setId)),
    entry(InstanceRelationshipType.class,
      EntityAccessor.of(InstanceRelationshipType::getId, InstanceRelationshipType::setId)),
    entry(InstanceStatus.class, EntityAccessor.of(InstanceStatus::getId, InstanceStatus::setId)),
    entry(InstanceType.class, EntityAccessor.of(InstanceType::getId, InstanceType::setId)),
    entry(IssuanceMode.class, EntityAccessor.of(IssuanceMode::getId, IssuanceMode::setId)),
    entry(ItemDamageStatus.class, EntityAccessor.of(ItemDamageStatus::getId, ItemDamageStatus::setId)),
    entry(ItemNoteType.class, EntityAccessor.of(ItemNoteType::getId, ItemNoteType::setId)),
    entry(IterationJob.class, EntityAccessor.of(IterationJob::getId, IterationJob::setId)),
    entry(LoanType.class, EntityAccessor.of(LoanType::getId, LoanType::setId)),
    entry(Location.class, EntityAccessor.of(Location::getId, Location::setId)),
    entry(LocationCampus.class, EntityAccessor.of(LocationCampus::getId, LocationCampus::setId)),
    entry(LocationInstitution.class, EntityAccessor.of(LocationInstitution::getId, LocationInstitution::setId)),
    entry(LocationLibrary.class, EntityAccessor.of(LocationLibrary::getId, LocationLibrary::setId)),
    entry(MarcJson.class, EntityAccessor.of(MarcJson::getId, MarcJson::setId)),
    entry(MaterialType.class, EntityAccessor.of(MaterialType::getId, MaterialType::setId)),
    entry(NatureOfContentTerm.class, EntityAccessor.of(NatureOfContentTerm::getId, NatureOfContentTerm::setId)),
    entry(ReindexJob.class, EntityAccessor.of(ReindexJob::getId, ReindexJob::setId)),
    entry(ServicePoint.class, EntityAccessor.of(ServicePoint::getId, ServicePoint::setId)),
    entry(ServicePointsUser.class, EntityAccessor.of(ServicePointsUser::getId, ServicePointsUser::setId)),
    entry(StatisticalCode.class, EntityAccessor.of(StatisticalCode::getId, StatisticalCode::setId)),
    entry(StatisticalCodeType.class, EntityAccessor.of(StatisticalCodeType::getId, StatisticalCodeType::setId)),
    entry(SubjectSource.class, EntityAccessor.of(SubjectSource::getId, SubjectSource::setId)),
    entry(SubjectType.class, EntityAccessor.of(SubjectType::getId, SubjectType::setId)),
    entry(HoldingsRecord.class, EntityAccessor.of(HoldingsRecord::getId, HoldingsRecord::setId,
      HoldingsRecord::getVersion, HoldingsRecord::setVersion)),
    entry(Instance.class, EntityAccessor.of(Instance::getId, Instance::setId,
      Instance::getVersion, Instance::setVersion)),
    entry(Item.class, EntityAccessor.of(Item::getId, Item::setId, Item::getVersion, Item::setVersion)),
    entry(NotificationSendingError.class, EntityAccessor.of(NotificationSendingError::getId,
      NotificationSendingError::setId)),
    entry(ItemPatchRequest.class, EntityAccessor.of(ItemPatchRequest::getId, ItemPatchRequest::setId,
      ItemPatchRequest::getVersion, ItemPatchRequest::setVersion)),
    entry(InstanceView.class, EntityAccessor.unsupported(InstanceView.class)),
    entry(RecordId.class, EntityAccessor.unsupported(RecordId.class)),
    entry(Setting.class, EntityAccessor.<Setting>of(
      setting -> setting.getId() == null ? null : setting.getId().toString(),
      (setting, id) -> setting.setId(UUID.fromString(id))))
  );

  private EntityAccessors() {
  }

  /**
   * Returns the accessor of the type. Types without an id, for example views that are only read,
   * are registered with an accessor that fails when the id is used.
   *
   * @throws IllegalArgumentException when the type is not registered
   */
  @SuppressWarnings("unchecked")
  public static <T> EntityAccessor<T> of(Class<T> type) {
    var accessor = ACCESSORS.get(type);
    if (accessor == null) {
      throw new IllegalArgumentException("No EntityAccessor registered for " + type.getName()
        + ", add it to " + EntityAccessors.class.getSimpleName());
    }
    return (EntityAccessor<T>) accessor;
  }
}
