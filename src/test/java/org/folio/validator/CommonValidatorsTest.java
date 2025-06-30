package org.folio.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.junit.jupiter.api.Test;

class CommonValidatorsTest {

  @Test
  void refuseIfNotFound_returnsSucceededFuture_whenEntityIsNotNull() {
    var entity = new HoldingsRecord();
    var result = CommonValidators.refuseIfNotFound(entity);

    assertTrue(result.succeeded());
    assertEquals(entity, result.result());
  }

  @Test
  void refuseIfNotFound_returnsFailedFuture_whenEntityIsNull() {
    var result = CommonValidators.refuseIfNotFound(null);

    assertTrue(result.failed());
    assertInstanceOf(NotFoundException.class, result.cause());
    assertEquals("Not found", result.cause().getMessage());
  }

  @Test
  void validateUuidFormat_returnsSucceededFuture_whenSetIsNull() {
    var result = CommonValidators.validateUuidFormat(null);

    assertTrue(result.succeeded());
  }

  @Test
  void validateUuidFormat_returnsSucceededFuture_whenSetIsEmpty() {
    var result = CommonValidators.validateUuidFormat(Set.of());

    assertTrue(result.succeeded());
  }

  @Test
  void validateUuidFormat_returnsSucceededFuture_whenAllUuidsAreValid() {
    var validUuids = Set.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var result = CommonValidators.validateUuidFormat(validUuids);

    assertTrue(result.succeeded());
  }

  @Test
  void validateUuidFormat_returnsFailedFuture_whenSetContainsInvalidUuid() {
    var invalidUuids = Set.of(UUID.randomUUID().toString(), "invalid-uuid");
    var result = CommonValidators.validateUuidFormat(invalidUuids);

    assertTrue(result.failed());
    assertInstanceOf(BadRequestException.class, result.cause());
    assertEquals("invalid input syntax for type uuid: \"invalid-uuid\"", result.cause().getMessage());
  }
}
