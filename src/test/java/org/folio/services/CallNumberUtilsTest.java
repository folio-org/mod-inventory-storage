package org.folio.services;

import junit.framework.TestCase;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Optional;

@RunWith(JUnitParamsRunner.class)
public class CallNumberUtilsTest extends TestCase {
  @BeforeClass
  public static void beforeClass() {
    System.out.println("Hello Library of Congress call numbers!");
  }
  @Parameters({
//    "Germ 350/35:1",
//    "Germ 350/35: 2",
//    "Germ 350/35: 1",
//    "Germ 350/35: 2",
//    "Germ 350/1",
//    "Germ 350/35: 1",
//    "Germ 350/35: 2",
//    "Germ 350/1",
//    "AD 12",
//    "AD 12",
//    "AD 120",
//    "AD 120",
//    "AD 12",
//    "AD 120",
//    "AD 12",
//    "AD 120",
//    "AD 10: 1/27",
//    "AD 101: 2/7",
//    "12",
//    "1 2",
//    "1.2",
//    "1-2",
//    "1  2",
//    "1—2",
//    "1 . 2",
//    "1 . . 2",
//    "1— —2",
//    "AD 10: 1/27",
//    "AD 10: 1/27 b",
//    "AD 101: 2/7",
//    "AD 10: 1/27",
//    "AD 10: 1/27 b",
//    "AD 101: 2/7",
//    "AD 10: 1/27 b",
//    "AD 10: 1/27",
//    "AD 101: 2/7",
//    "AD 10: 1/27",
//    "AD 10: 1/27",
//    "AD 101: 2/7",
//    "AD 10: 1/27 b",
//    "ABC123.1 .R15 2018",
//    "ABC123.1 .R15 2018",
//    "ABC123.1 .R15 2018",
//    "BX1935 .A23 1959",
//    "BX1935 .A23 1959",
//    "BX1935 .A23 1959",
//    "S537.N56 C82",
//    "S537.N56 C82",
//    "S537.N56 C82",
//    "S537.N56 C82",
//    "S537.N56 C82",
//    "Z2557 .D57",
//    "Z2557 .D57",
//    "Z2557 .D57",
//    "PS3623.R534 P37 2005",
//    "PS3623.R534 P37 2005",
//    "PS3623.R534 P37 2005",
//    "R534.A1 D56 2015",
//    "R534.A1 D56 2015"
    "PS3623.R534 P37 2005"
  })
  @Test
  public void firstTest(String callNumParam) {
    final String fieldSeparator = ", ";
    StringBuilder sb = new StringBuilder();
    sb.append("Call Number:");
    sb.append(callNumParam);
    sb.append(fieldSeparator);
    Optional<String> shelveNumber = CallNumberUtils.getShelfKeyFromCallNumber(callNumParam);
    sb.append("Valid:"+shelveNumber.isPresent());
    if (shelveNumber.isPresent()) {
      sb.append(fieldSeparator);
      sb.append("Shelve key:");
      sb.append(shelveNumber.get());
    }
    System.out.println(sb.toString());






  }

  @Test
  public void prepareText() {
    String input = "Germ 350/35:1\n" +
      "Germ 350/35: 2\n" +
      "Germ 350/35: 1\n" +
      "Germ 350/35: 2\n" +
      "Germ 350/1\n" +
      "Germ 350/35: 1\n" +
      "Germ 350/35: 2\n" +
      "Germ 350/1\n" +
      "AD 12\n" +
      "AD 12\n" +
      "AD 120\n" +
      "AD 120\n" +
      "AD 12\n" +
      "AD 120\n" +
      "AD 12\n" +
      "AD 120\n" +
      "AD 10: 1/27\n" +
      "AD 101: 2/7\n" +
      "12\n" +
      "1 2\n" +
      "1.2\n" +
      "1-2\n" +
      "1  2\n" +
      "1—2\n" +
      "1 . 2\n" +
      "1 . . 2\n" +
      "1— —2\n" +
      "AD 10: 1/27\n" +
      "AD 10: 1/27 b\n" +
      "AD 101: 2/7\n" +
      "AD 10: 1/27\n" +
      "AD 10: 1/27 b\n" +
      "AD 101: 2/7\n" +
      "AD 10: 1/27 b\n" +
      "AD 10: 1/27\n" +
      "AD 101: 2/7\n" +
      "AD 10: 1/27\n" +
      "AD 10: 1/27\n" +
      "AD 101: 2/7\n" +
      "AD 10: 1/27 b\n" +
      "ABC123.1 .R15 2018\n" +
      "ABC123.1 .R15 2018\n" +
      "ABC123.1 .R15 2018\n" +
      "BX1935 .A23 1959\n" +
      "BX1935 .A23 1959\n" +
      "BX1935 .A23 1959\n" +
      "S537.N56 C82\n" +
      "S537.N56 C82\n" +
      "S537.N56 C82\n" +
      "S537.N56 C82\n" +
      "S537.N56 C82\n" +
      "Z2557 .D57\n" +
      "Z2557 .D57\n" +
      "Z2557 .D57\n" +
      "PS3623.R534 P37 2005\n" +
      "PS3623.R534 P37 2005\n" +
      "PS3623.R534 P37 2005\n" +
      "R534.A1 D56 2015\n" +
      "R534.A1 D56 2015";
    String[] inputLines = input.split("\n");
    Arrays.stream(inputLines).forEach(line -> System.out.println("\""+line.trim()+"\","));
  }

}
