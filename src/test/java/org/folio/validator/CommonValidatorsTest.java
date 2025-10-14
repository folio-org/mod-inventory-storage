package org.folio.validator;

import static org.folio.validator.CommonValidators.normalizeIfList;
import static org.folio.validator.CommonValidators.normalizeIfMap;
import static org.folio.validator.CommonValidators.normalizeProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.junit.jupiter.api.Test;

class CommonValidatorsTest {

  private static final String FIELD = "fieldName";
  private static final String VALUE_STRING = "stringValue";
  private static final int VALUE_INT = 10;

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

  @Test
  void validateUuidFormatForList_returnsSucceededFuture_whenListIsEmpty() {
    assertTrue(CommonValidators.validateUuidFormatForList(List.of(), item -> Set.of()).succeeded());
  }

  @Test
  void validateUuidFormatForList_returnsSucceededFuture_whenAllUuidsAreValid() {
    var items = List.of(
      createHoldingsRecord(Set.of(UUID.randomUUID().toString(), UUID.randomUUID().toString())),
      createHoldingsRecord(Set.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
    );
    assertTrue(CommonValidators.validateUuidFormatForList(items, HoldingsRecord::getStatisticalCodeIds).succeeded());
  }

  @Test
  void validateUuidFormatForList_returnsFailedFuture_whenListContainsInvalidUuid() {
    var items = List.of(
      new HoldingsRecord(),
      createHoldingsRecord(Set.of(UUID.randomUUID().toString(), UUID.randomUUID().toString())),
      createHoldingsRecord(Set.of(UUID.randomUUID().toString(), "invalid-uuid"))
    );
    var result = CommonValidators.validateUuidFormatForList(items, HoldingsRecord::getStatisticalCodeIds);
    assertTrue(result.failed());
    assertInstanceOf(BadRequestException.class, result.cause());
    assertEquals("invalid input syntax for type uuid: \"invalid-uuid\"", result.cause().getMessage());
  }

  @Test
  void normalizeProperty_shouldConvertStringValue() {
    Map<String, Object> properties = new HashMap<>(Map.of(FIELD, VALUE_INT));
    normalizeProperty(properties, FIELD, Integer::valueOf);

    assertEquals(VALUE_INT, properties.get(FIELD));
  }

  @Test
  void normalizeProperty_shouldNotConvertNonStringValue() {
    Map<String, Object> properties = new HashMap<>(Map.of(FIELD, VALUE_INT));
    normalizeProperty(properties, FIELD, Object::toString);

    assertEquals(VALUE_INT, properties.get(FIELD));
  }

  @Test
  void normalizeProperty_shouldDoNothingIfFieldNotPresent() {
    Map<String, Object> properties = new HashMap<>();
    normalizeProperty(properties, FIELD, Integer::valueOf);

    assertNull(properties.get(FIELD));
  }

  @Test
  void normalizeIfMap_shouldApplyActionWhenValueIsMap() {
    Map<String, Object> props = new HashMap<>();
    Map<String, Object> nested = Map.of(FIELD, VALUE_STRING);
    props.put("data", nested);
    AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
    normalizeIfMap(props, "data", captured::set);

    assertEquals(nested, captured.get());
  }

  @Test
  void normalizeIfMap_shouldNotApplyActionWhenValueIsNotMap() {
    Map<String, Object> props = new HashMap<>();
    props.put(FIELD, VALUE_STRING);
    AtomicInteger called = new AtomicInteger();
    normalizeIfMap(props, FIELD, map -> called.set(VALUE_INT));

    assertEquals(0, called.get());
  }

  @Test
  void normalizeIfMap_shouldNotApplyActionWhenValueIsNull() {
    Map<String, Object> props = new HashMap<>();
    props.put(FIELD, null);
    AtomicBoolean called = new AtomicBoolean(false);
    normalizeIfMap(props, FIELD, map -> called.set(true));

    assertFalse(called.get());
  }

  @Test
  void normalizeIfList_shouldApplyActionForEachMapInList() {
    var props = new HashMap<String, Object>();
    var list = List.of(new HashMap<>(), new HashMap<>());
    props.put(FIELD, list);
    var called = new AtomicBoolean();
    normalizeIfList(props, FIELD, map -> called.set(true));

    assertTrue(called.get());
  }

  @Test
  void normalizeIfList_shouldNotApplyActionWhenValueIsNotList() {
    Map<String, Object> props = new HashMap<>(Map.of(FIELD, VALUE_STRING));
    AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
    normalizeIfList(props, FIELD, captured::set);

    assertNull(captured.get());
  }

  @Test
  void normalizeIfList_shouldNotApplyActionForEmptyList() {
    Map<String, Object> props = new HashMap<>(Map.of(FIELD, List.of()));
    AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
    normalizeIfList(props, FIELD, captured::set);

    assertNull(captured.get());
  }

  private HoldingsRecord createHoldingsRecord(Set<String> statisticalCodeIds) {
    var holdingsRecord = new HoldingsRecord();
    holdingsRecord.setStatisticalCodeIds(statisticalCodeIds);
    return holdingsRecord;
  }
}
