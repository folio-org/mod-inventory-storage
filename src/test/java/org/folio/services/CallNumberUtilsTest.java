package org.folio.services;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import junit.framework.TestCase;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class CallNumberUtilsTest extends TestCase {

  @Parameters({
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
    "RR 3718,,,RR 718,,,,,"
  })
  @Test
  public void inputForShelvingNumber(
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
}
