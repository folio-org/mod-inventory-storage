package org.folio.services;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.marc4j.callnum.AbstractCallNumber;
import org.marc4j.callnum.Utils;

public class SuDocCallNumber extends AbstractCallNumber {
  private static final String GROUP1 = "([A-Za-z]+\\s*)";
  private static final String GROUP2 = "(\\d+)";
  private static final String GROUP3 = "(\\.(?:[A-Za-z]+\\d*|\\d+))";
  private static final String GROUP4 = "(/(?:[A-Za-z]+(?:\\d+(?:-\\d+)?)?|\\d+(?:-\\d+)?))?";
  private static final String GROUP5 = "(.*)";
  public static final String SU_DOC_PATTERN = "^(?:" + GROUP1 + GROUP2 + GROUP3 + GROUP4 + ")?:?" + GROUP5;
  protected static Pattern stemPattern = Pattern.compile(SU_DOC_PATTERN);
  protected String authorSymbol;
  protected String subordinateOffice;
  protected String series;
  protected String subSeries;

  protected String suffix;
  protected String shelfKey;


  public SuDocCallNumber(String callNumber) {
    this.parse(callNumber);
  }

  protected void init() {
    this.rawCallNum = null;
    this.authorSymbol = null;
    this.subordinateOffice = null;
    this.series = null;
    this.subSeries = null;
    this.suffix = null;
    this.shelfKey = null;
  }

  @Override
  public void parse(String s) {
    this.init();
    if (s == null) {
      this.rawCallNum = null;
    } else {
      this.rawCallNum = s.trim();
    }

    this.parse();
  }

  protected void parse() {
    if (this.rawCallNum != null) {
      this.parseCallNumber();
    }
  }

  protected void parseCallNumber() {
    var stemMatcher = stemPattern.matcher(rawCallNum);
    if (stemMatcher.matches()) {
      authorSymbol = stemMatcher.group(1);
      subordinateOffice = stemMatcher.group(2);
      series = stemMatcher.group(3);
      subSeries = stemMatcher.group(4);
      suffix = stemMatcher.group(5);
    } else {
      suffix = rawCallNum;
    }
  }

  @Override
  public String getShelfKey() {
    if (shelfKey == null) {
      buildShelfKey();
    }
    return shelfKey;
  }

  @Override
  public boolean isValid() {
    return authorSymbol != null;
  }

  private void buildShelfKey() {
    StringBuilder key = new StringBuilder();
    if (authorSymbol != null) {
      key.append(authorSymbol.trim());
    }

    appendWithSymbolIfNeeded(key, subordinateOffice);
    appendWithSymbolIfNeeded(key, series);
    appendWithSymbolIfNeeded(key, subSeries);
    appendWithSymbolIfNeeded(key, suffix);

    shelfKey = key.toString();
  }

  private void appendWithSymbolIfNeeded(StringBuilder key, String cnPart) {
    if (StringUtils.isBlank(cnPart)) {
      return;
    }

    if (cnPart.startsWith(".") || cnPart.startsWith("/") || cnPart.startsWith("-") || cnPart.startsWith(":")) {
      cnPart = cnPart.substring(1);
    }
    var parts = cnPart.split("[./ -]");
    for (String part : parts) {
      if (!key.isEmpty()) {
        key.append(' ');
      }
      part = part.trim();

      if (StringUtils.isBlank(part)) {
        continue;
      }
      if (Character.isAlphabetic(part.charAt(0))) {
        key.append(" !");
      } else if (part.length() >= 3) {
        key.append("!");
      }

      Utils.appendNumericallySortable(key, part);
    }
  }
}
