package org.folio.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SuDocCallNumberTest {

  private static final List<String> logValidSuDocNumbers = Arrays.asList(
    "T22.19:M54",
    "T22.19:M54/990",
    "T22.19/2:P94",
    "T22.19/2:P94/2",
    "T22.19/2:V88/retest",
    "T22.19/2:V88/retest/989",
    "T22.19/2:V88/test/989",
    "T22.19/2:V88/989/student/militia",
    "T22.19/2:V88/989/student/spanish",
    "T22.19/2:V88/989/student/text",
    "T22.19/2:V88/990/student/spanish",
    "T22.19/2:V88/2"
  );

  private static final List<String> logInvalidSuDocNumbers = Arrays.asList(
    "QA 11 .GA1 E53 2005",
    "QB 11 .GA1 F875d 1999",
    "QC 11 .GA1 Q6 2012",
    "QD 11 .GI8 P235s 2006",
    "QG 124 B811m 1875",
    "W 250 M56 2011",
    "Z 250 M6 2011",
    "1Z 250 M6 2011",
    "2A250:M62011",
    null
  );

  @Test
  public void isValidNlmNumber() {
    for (String validNlmNumber : logValidSuDocNumbers) {
      SuDocCallNumber suDocCallNumber = new SuDocCallNumber(validNlmNumber);
      assertTrue(suDocCallNumber.isValid());
      assertNotNull(suDocCallNumber.shelfKey);
    }
  }

  @Test
  public void isInvalidNlmNumber() {
    for (String validNlmNumber : logInvalidSuDocNumbers) {
      assertFalse(new SuDocCallNumber(validNlmNumber).isValid());
    }
  }
}
