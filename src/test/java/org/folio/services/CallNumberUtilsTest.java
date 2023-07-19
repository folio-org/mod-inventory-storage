package org.folio.services;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class CallNumberUtilsTest {

  @ParameterizedTest
  @CsvSource({
    "PN 12 A6,PN12 .A6,,PN2 .A6,,,,,",
    "PN 12 A6 V 13 NO 12 41999,PN2 .A6 v.3 no.2 1999,,PN2 .A6,v. 3,no. 2,1999,,",
    "PN 12 A6 41999,PN12 .A6 41999,,PN2 .A6 1999,,,,,",
    "PN 12 A6 41999 CD,PN12 .A6 41999 CD,,PN2 .A6 1999,,,,,CD",
    "PN 12 A6 41999 12,PN12 .A6 41999 C.12,,PN2 .A6 1999,,,,2,",
    "PN 12 A69 41922 12,PN12 .A69 41922 C.12,,PN2 .A69,,,1922,2,",
    "PN 12 A69 NO 12,PN12 .A69 NO.12,,PN2 .A69,,no. 2,,,",
    "PN 12 A69 NO 12 41922 11,PN12 .A69 NO.12 41922 C.11,,PN2 .A69,,no. 2,1922,1,",
    "PN 12 A69 NO 12 41922 12,PN12 .A69 NO.12 41922 C.12,Wordsworth,PN2 .A69,,no. 2,1922,2,",
    "PN 12 A69 V 11 NO 11,PN12 .A69 V.11 NO.11,,PN2 .A69,v.1,no. 1,,,",
    "PN 12 A69 V 11 NO 11 +,PN12 .A69 V.11 NO.11 +,Over,PN2 .A69,v.1,no. 1,,,+",
    "PN 12 A69 V 11 NO 11 41921,PN12 .A69 V.11 NO.11 41921,,PN2 .A69,v.1,no. 1,1921,,",
    "PR 49199.3 41920 L33 41475 A6,PR 49199.3 41920 .L33 41475 .A6,,PR9199.3 1920 .L33 1475 .A6,,,,,",
    "PQ 42678 K26 P54,PQ 42678 .K26 P54,,PQ2678.K26 P54,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5 1992,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5,,,1992,,",
    "PR 3919 L33 41990,PR 3919 .L33 41990,,PR919 .L33 1990,,,,,",
    "PR 49199 A39,PR 49199 .A39,,PR9199 .A39,,,,,",
    "PR 49199.48 B3,PR 49199.48 .B3,,PR9199.48 .B3,,,,,",
    "3341.7,,,341.7,,,,,",
    "3341.7 258 221,,,341.7/58 / 21,,,,,",
    "3341.7 258 221,,T1,341.7/58 / 21,,,,,",
    "3394.1 O41 B,,,394.1 O41b,,,,,",
    "3621.56 W91 M V 13 NO 12 41999,,,621.56 W91m,v.3,no. 2,1999,,",
    "221 E23,,,21 E23,,,,,",
    "11,,,00001,,,,,",
    "3325 D A 41908 FREETOWN MAP,,,325-d A-1908 (Freetown) Map,,,,,",
    "45001 MICROFILM,,,5001 Microfilm,,,,,",
    "19 A2 C 6444218 MUSIC CD,,,9A2 C0444218 Music CD,,,,,",
    "GROUP Smith,,,GROUP Smith,,,,,",
    "free-text,,,free-text,,,,,",
    "RR 3718,,,RR 718,,,,,",
    "QS 211 G A1 E53 42005 42005,QS11 .GA1 E53 2005,,QS 11 .GA1 E53 2005,,,2005,,",
    "WB 3102.5 B62 42018 42018,WB102.5 .B62 2018,,WB 102.5 B62 2018,,,2018,,"
  })
  void inputForShelvingNumber(
    String desiredShelvingOrder,
    String initiallyDesiredShelvesOrder,
    String prefix,
    String callNumber,
    String volume,
    String enumeration,
    String chronology,
    String copy,
    String suffix
  ) {

    Item item = new Item();
    item.setItemLevelCallNumberPrefix(prefix);
    item.setItemLevelCallNumber(callNumber);
    item.setVolume(volume);
    item.setEnumeration(enumeration);
    item.setChronology(chronology);
    item.setCopyNumber(copy);
    item.setItemLevelCallNumberSuffix(suffix);

    HoldingsRecord holdingsRecord = new HoldingsRecord();
    EffectiveCallNumberComponentsUtil.setCallNumberComponents(item, holdingsRecord);
    EffectiveCallNumberComponentsUtil.calculateAndSetEffectiveShelvingOrder(item);

    assertThat(item.getEffectiveShelvingOrder(), is(desiredShelvingOrder));
  }

  @Test
  void testSuDocSortingOrder() {
    var callNumbers = Arrays.asList(
      "J29.2:D84/982",
      "J29.2:D84/2",
      "L36.202:F15/990",
      "L36.202:F15/991",
      "L36.202:F15/2",
      "L37.s:Oc1/2/991",
      "L37.2:Oc1/2/conversion",
      "T22.19:M54",
      "T22.19:M54/990",
      "T22.19/2:P94",
      "T22.19/2:P94/2",
      "Y4.F76/2:Af8/12",
      "Y4.F76/2:Af8/12/rev."
    );
    var expected  = callNumbers.stream().map(SuDocCallNumber::new).map(SuDocCallNumber::getShelfKey).toList();
    Collections.shuffle(callNumbers, new Random(0));  // reproducible shuffle
    var actual = callNumbers.stream().map(SuDocCallNumber::new).map(SuDocCallNumber::getShelfKey).sorted().toList();

    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @CsvSource({
    "D 3.186,D 13 !3186",
    "D 3.186/3,D 13 !3186 13",
    "Y3.M58/2,Y 13  !M 258 12",
    "C 55.309/2,C 255 !3309 12",
    "D 3.186/7-3,D 13 !3186 17 13",
    "T22.19:M54,T 222 219  !M 254",
    "C 55.309/2-2,C 255 !3309 12 12",
    "C 55.309/2-8,C 255 !3309 12 18",
    "EP 1.23:91-44,EP 11 223 291 244",
    "Y3.M58/summ,Y 13  !M 258  !SUMM",
    "C 55.309/2-10,C 255 !3309 12 210",
    "T22.19:M54/990,T 222 219  !M 254 !3990",
    "T22.19/2:P94/2,T 222 219 12  !P 294 12",
    "T22.19:M54/990,T 222 219  !M 254 !3990",
    "EP 1.23:A 62 A 1.35,EP 11 223  !A 262  !A 11 235",
    "T22.19/2:V88/2/989,T 222 219 12  !V 288 12 !3989",
    "T22.19/2:V88/retest,T 222 219 12  !V 288  !RETEST",
    "T22.19/2:V88/test/989,T 222 219 12  !V 288  !TEST !3989",
    "T22.19/2:V88/retest/989, T 222 219 12  !V 288  !RETEST !3989",
    "T22.19/2:V88/989/student/militia,T 222 219 12  !V 288 !3989  !STUDENT  !MILITIA",
    "T22.19/2:V88/989/student/spanish,T 222 219 12  !V 288 !3989  !STUDENT  !SPANISH",
  })
  void checkSuDocShelvingKey(
    String callNumber,
    String expectedShelvingKey
  ) {
    var shelvingKey = new SuDocCallNumber(callNumber).getShelfKey();
    assertEquals(expectedShelvingKey, shelvingKey);
  }
}
