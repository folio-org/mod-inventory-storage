package org.folio.rest.api.testdata;

import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.DEWEY_CALL_NUMBER_TYPE;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.LC_CALL_NUMBER_TYPE;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.MOYS_CALL_NUMBER_TYPE;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.NLM_CALL_NUMBER_TYPE;

import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ItemEffectiveCallNumberComponentsTestData {

  private static CallNumberComponentPropertyNames forProperty(String effectivePropertyName) {
    if (effectivePropertyName.equals("callNumber")) {
      return new CallNumberComponentPropertyNames(
        "callNumber", "itemLevelCallNumber", effectivePropertyName
      );
    }

    final String holdingsPropertyName = "callNumber" + StringUtils.capitalize(effectivePropertyName);
    final String itemPropertyName = "itemLevel" + StringUtils.capitalize(holdingsPropertyName);

    return new CallNumberComponentPropertyNames(
      holdingsPropertyName, itemPropertyName, effectivePropertyName
    );
  }

  @SuppressWarnings("unused")
  public Object[][] createPropertiesParams() {
    // Format:
    // CallNumber component names, holdings value, item value
    return new Object[][] {
      // Call Number
      {forProperty("callNumber"), "hrCallNumber", "itCallNumber"},
      {forProperty("callNumber"), null, "itCallNumber"},
      {forProperty("callNumber"), "hrCallNumber", null},
      {forProperty("callNumber"), null, null},
      // CallNumberSuffix
      {forProperty("suffix"), "hrCNSuffix", "itCNSuffix"},
      {forProperty("suffix"), "hrCNSuffix", null},
      {forProperty("suffix"), null, "itCNSuffix"},
      {forProperty("suffix"), null, null},
      // CallNumberPrefix
      {forProperty("prefix"), "hrCNPrefix", "itCNPrefix"},
      {forProperty("prefix"), "hrCNPrefix", null},
      {forProperty("prefix"), null, "itCNPrefix"},
      {forProperty("prefix"), null, null},
      // CallNumberTypeId
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, LC_CALL_NUMBER_TYPE},
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, null},
      {forProperty("typeId"), null, LC_CALL_NUMBER_TYPE},
      {forProperty("typeId"), null, null},
      };
  }

  @SuppressWarnings("unused")
  public Object[][] updatePropertiesParams() {
    // Format:
    // CallNumber component names, init holdings value, target holdings value, init item value, target item value
    return Stream.of(
      callNumberTestCases(),
      suffixTestCases(),
      prefixTestCases(),
      prefixDuplicateTestCases(),
      typeIdTestCases()
    ).flatMap(Arrays::stream).toArray(Object[][]::new);
  }

  private Object[][] callNumberTestCases() {
    return new Object[][] {
      {forProperty("callNumber"), "initHrCN", "targetHrCN", "initItCN", "targetItCN"},
      {forProperty("callNumber"), "initHrCN", null, "initItCN", "targetItCN"},
      {forProperty("callNumber"), "initHrCN", "targetHrCN", "initItCN", null},
      {forProperty("callNumber"), "initHrCN", null, "initItCN", null},
      {forProperty("callNumber"), "initHrCN", null, "initItCN", "initItCN"},
      {forProperty("callNumber"), "initHrCN", "initHrCN", "initItCN", null},
      {forProperty("callNumber"), "initHrCN", "targetHrCN", null, null},
      {forProperty("callNumber"), null, "targetHrCN", "initItCN", null}
    };
  }

  private Object[][] suffixTestCases() {
    return new Object[][] {
      {forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", "initItCNSuffix", "targetItCNSuffix"},
      {forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", "targetItCNSuffix"},
      {forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", "initItCNSuffix", null},
      {forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", null},
      {forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", "initItCNSuffix"},
      {forProperty("suffix"), "initHrCNSuffix", "initHrCNSuffix", "initItCNSuffix", null},
      {forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", null, null},
      {forProperty("suffix"), null, "targetHrCNSuffix", "initItCNSuffix", null}
    };
  }

  private Object[][] prefixTestCases() {
    return new Object[][] {
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "initItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "initHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", null, null},
      {forProperty("prefix"), null, "targetHrCNPrefix", "initItCNPrefix", null}
    };
  }

  private Object[][] prefixDuplicateTestCases() {
    return new Object[][] {
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "initItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "initHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", null, null},
      {forProperty("prefix"), null, "targetHrCNPrefix", "initItCNPrefix", null}
    };
  }

  private Object[][] typeIdTestCases() {
    return new Object[][] {
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, NLM_CALL_NUMBER_TYPE,
       LC_CALL_NUMBER_TYPE, MOYS_CALL_NUMBER_TYPE},
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, null, LC_CALL_NUMBER_TYPE, MOYS_CALL_NUMBER_TYPE},
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, NLM_CALL_NUMBER_TYPE, LC_CALL_NUMBER_TYPE, null},
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, null, LC_CALL_NUMBER_TYPE, null},
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, null, LC_CALL_NUMBER_TYPE, LC_CALL_NUMBER_TYPE},
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, DEWEY_CALL_NUMBER_TYPE, LC_CALL_NUMBER_TYPE, null},
      {forProperty("typeId"), DEWEY_CALL_NUMBER_TYPE, NLM_CALL_NUMBER_TYPE, null, null},
      {forProperty("typeId"), null, NLM_CALL_NUMBER_TYPE, LC_CALL_NUMBER_TYPE, null}
    };
  }

  public static final class CallNumberComponentPropertyNames {
    public final String holdingsPropertyName;
    public final String itemPropertyName;
    public final String effectivePropertyName;

    private CallNumberComponentPropertyNames(
      String holdingsPropertyName, String itemPropertyName, String effectivePropertyName) {

      this.holdingsPropertyName = holdingsPropertyName;
      this.itemPropertyName = itemPropertyName;
      this.effectivePropertyName = effectivePropertyName;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(null)
        .append(holdingsPropertyName)
        .append(itemPropertyName)
        .append(effectivePropertyName)
        .toString();
    }
  }
}
