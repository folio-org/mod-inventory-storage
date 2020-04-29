package org.folio.rest.api;

import static org.folio.rest.api.InstanceStorageTest.smallAngryPlanet;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class HoldingsCallNumberNormalizedTest extends TestBaseWithInventoryUtil {

  @BeforeClass
  public static void createHoldingsRecords() throws Exception {
    final IndividualResource instance = instancesClient
      .create(smallAngryPlanet(UUID.randomUUID()));

    for (String[] callNumberComponents : callNumberData()) {
      holdingsClient.create(new HoldingRequestBuilder()
        .forInstance(instance.getId())
        .withPermanentLocation(mainLibraryLocationId)
        .withCallNumberPrefix(callNumberComponents[0])
        .withCallNumber(callNumberComponents[1])
        .withCallNumberSuffix(callNumberComponents[2]));
    }
  }

  @Parameters({
    "germ 350",
    "germ350",
    "Germ 350/"
  })
  @Test
  public void canApplyRightTruncationForCallNumber(String searchQuery) throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized(searchQuery);

    assertThat(callNumbersFound, hasSize(3));
    assertThat(callNumbersFound, allOf(
      hasItem("H Germ 350/35: 1"),
      hasItem("H Germ 350/35: 2"),
      hasItem("F Germ 350/1")
    ));
  }

  @Test
  public void canApplyRightTruncationForSuffix() throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized("GE77 .F73 2014 Curriculum");

    assertThat(callNumbersFound, hasSize(1));
    assertThat(callNumbersFound, hasItem("GE77 .F73 2014 Curriculum Materials Collection"));
  }

  @Test
  public void canIgnoreNonAlphanumericCharacters() throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized("12");

    assertThat(callNumbersFound, hasSize(4));
    assertThat(callNumbersFound, allOf(
      hasItem("1—2"),
      hasItem("1 . 2"),
      hasItem("1 . . 2"),
      hasItem("1— —2")
    ));
  }

  @Test
  public void canSkipPrefix() throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized("ABC123.1 R15 2018");

    assertThat(callNumbersFound, hasSize(1));
    assertThat(callNumbersFound, hasItem("Oversize ABC123.1 .R15 2018"));
  }

  @Test
  public void canSkipNonAlphanumericCharactersInPrefix() throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized("Oversize BX1935.A23 1959");

    assertThat(callNumbersFound, hasSize(1));
    assertThat(callNumbersFound, hasItem("++ Oversize BX1935 .A23 1959"));
  }

  @Test
  public void canSearchByCallNumberAndIgnorePrefixAndSuffix() throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized("S537.N56 C82");

    assertThat(callNumbersFound, hasSize(2));
    assertThat(callNumbersFound, allOf(
      hasItem("Rare Books S537.N56 C82 ++"),
      hasItem("Rare Books S537.N56 C82 Foo")
    ));
  }

  @Test
  public void cannotSearchByNonAlphanumericsOnly() throws Exception {
    assertThat(searchByCallNumberNormalized("++"), hasSize(0));
  }

  @Parameters({
    "Rare Books S537 N56 C82",
    "Rare Books S537 N56",
    "rarebookss537n56c82",
  })
  @Test
  public void canSearchByPrefixAndCallNumber(String searchQuery) throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized(searchQuery);

    assertThat(callNumbersFound, hasSize(2));
    assertThat(callNumbersFound, allOf(
      hasItem("Rare Books S537.N56 C82 ++"),
      hasItem("Rare Books S537.N56 C82 Foo")
    ));
  }

  @Test
  public void canSearchByPrefixOnly() throws Exception {
    final List<String> callNumbersFound = searchByCallNumberNormalized("foo");

    assertThat(callNumbersFound, hasSize(2));
    assertThat(callNumbersFound, allOf(
      hasItem("Foo QE423 .T66"),
      hasItem("Foo QE423 .T66 Dictionary")
    ));
  }

  @Parameters({
    "Curriculum Materials Collection",
    "curriculum materials",
    "dictionary"
  })
  @Test
  public void cannotSearchBySuffixOnly(String searchQuery) throws Exception {
    assertThat(searchByCallNumberNormalized(searchQuery), hasSize(0));
  }

  private List<String> searchByCallNumberNormalized(String callNumber) throws Exception {
    // Ex: fullCallNumberNormalized="foo" OR callNumberAndSuffixNormalized="foo"
    final List<IndividualResource> holdings = holdingsClient.getMany(
      "fullCallNumberNormalized=\"%1$s\" OR callNumberAndSuffixNormalized=\"%1$s\"",
      callNumber);

    return holdings.stream()
      .map(IndividualResource::getJson)
      .map(json -> Stream.of(json.getString("callNumberPrefix"),
        json.getString("callNumber"), json.getString("callNumberSuffix"))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" ")))
      .collect(Collectors.toList());
  }

  private static String[][] callNumberData() {
    // Suffix, call number, prefix
    return new String[][]{
      {null, "AD 12", null},
      {null, "AD 120", null},
      {null, "AD 10: 1/27", null},
      {null, "AD 101: 2/7", null},
      {null, "1—2", null},
      {null, "1 . 2", null},
      {null, "1 . . 2", null},
      {null, "1— —2", null},
      {"H", "Germ 350/35: 1", null},
      {"H", "Germ 350/35: 2", null},
      {"F", "Germ 350/1", null},
      {"Oversize", "ABC123.1 .R15 2018", null},
      {"++ Oversize", "BX1935 .A23 1959", null},
      {"Rare Books", "S537.N56 C82", "++"},
      {"Rare Books", "S537.N56 C82", "Foo"},
      {null, "GE77 .F73 2014", "Curriculum Materials Collection"},
      {"Foo", "QE423 .T66", null},
      {"Foo", "QE423 .T66", "Dictionary"},
    };
  }
}
