package org.folio.rest.api.testdata;

import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.HOLDINGS_CALL_NUMBER_TYPE;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.HOLDINGS_CALL_NUMBER_TYPE_SECOND;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.ITEM_LEVEL_CALL_NUMBER_TYPE;
import static org.folio.rest.api.ItemEffectiveCallNumberComponentsTest.ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND;

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
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, ITEM_LEVEL_CALL_NUMBER_TYPE},
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, null},
      {forProperty("typeId"), null, ITEM_LEVEL_CALL_NUMBER_TYPE},
      {forProperty("typeId"), null, null},
      };
  }

  @SuppressWarnings("unused")
  public Object[][] updatePropertiesParams() {
    // Format:
    // CallNumber component names, init holdings value, target holdings value, init item value, target item value
    return new Object[][] {
      // CallNumber
      {forProperty("callNumber"), "initHrCN", "targetHrCN", "initItCN", "targetItCN"},
      {forProperty("callNumber"), "initHrCN", null, "initItCN", "targetItCN"},
      {forProperty("callNumber"), "initHrCN", "targetHrCN", "initItCN", null},
      {forProperty("callNumber"), "initHrCN", null, "initItCN", null},
      {forProperty("callNumber"), "initHrCN", null, "initItCN", "initItCN"},
      {forProperty("callNumber"), "initHrCN", "initHrCN", "initItCN", null},
      {forProperty("callNumber"), "initHrCN", "targetHrCN", null, null},
      {forProperty("callNumber"), null, "targetHrCN", "initItCN", null},
      // CallNumberSuffix
      {forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", "initItCNSuffix", "targetItCNSuffix"},
      {forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", "targetItCNSuffix"},
      {forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", "initItCNSuffix", null},
      {forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", null},
      {forProperty("suffix"), "initHrCNSuffix", null, "initItCNSuffix", "initItCNSuffix"},
      {forProperty("suffix"), "initHrCNSuffix", "initHrCNSuffix", "initItCNSuffix", null},
      {forProperty("suffix"), "initHrCNSuffix", "targetHrCNSuffix", null, null},
      {forProperty("suffix"), null, "targetHrCNSuffix", "initItCNSuffix", null},
      // CallNumberPrefix
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "initItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "initHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", null, null},
      {forProperty("prefix"), null, "targetHrCNPrefix", "initItCNPrefix", null},
      // CallNumberPrefix
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "targetItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", null, "initItCNPrefix", "initItCNPrefix"},
      {forProperty("prefix"), "initHrCNPrefix", "initHrCNPrefix", "initItCNPrefix", null},
      {forProperty("prefix"), "initHrCNPrefix", "targetHrCNPrefix", null, null},
      {forProperty("prefix"), null, "targetHrCNPrefix", "initItCNPrefix", null},
      // CallNumberTypeId
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, HOLDINGS_CALL_NUMBER_TYPE_SECOND,
       ITEM_LEVEL_CALL_NUMBER_TYPE, ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND
      },
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, null, ITEM_LEVEL_CALL_NUMBER_TYPE,
       ITEM_LEVEL_CALL_NUMBER_TYPE_SECOND
      },
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, HOLDINGS_CALL_NUMBER_TYPE_SECOND,
       ITEM_LEVEL_CALL_NUMBER_TYPE, null
      },
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, null, ITEM_LEVEL_CALL_NUMBER_TYPE, null},
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, null, ITEM_LEVEL_CALL_NUMBER_TYPE,
       ITEM_LEVEL_CALL_NUMBER_TYPE
      },
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, HOLDINGS_CALL_NUMBER_TYPE,
       ITEM_LEVEL_CALL_NUMBER_TYPE, null
      },
      {forProperty("typeId"), HOLDINGS_CALL_NUMBER_TYPE, HOLDINGS_CALL_NUMBER_TYPE_SECOND, null, null},
      {forProperty("typeId"), null, HOLDINGS_CALL_NUMBER_TYPE_SECOND, ITEM_LEVEL_CALL_NUMBER_TYPE, null},
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
