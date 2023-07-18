package org.folio.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.marc4j.callnum.AbstractCallNumber;
import org.marc4j.callnum.Utils;

public class SuDocCallNumber extends AbstractCallNumber {
  public static final String STEM_PATTERN = "^([A-Za-z]+\\s*)(\\d+)(\\.[A-Za-z]*\\d*)(/*[A-Za-z]*\\d*-*\\d*)";
  protected static Pattern stemPattern =
    Pattern.compile(STEM_PATTERN + "(:*(.*))");
  protected static Pattern suffixPattern = Pattern.compile("^:(.*)");
  protected String authorSymbol;
  protected String subordinateOffice;
  protected String series;
  protected String subSeries;

  protected String stem;
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
    String everythingElse = null;
    var stemMatcher = stemPattern.matcher(rawCallNum);
    if (stemMatcher.matches()) {
      authorSymbol = stemMatcher.group(1) == null ? null : stemMatcher.group(1);
      subordinateOffice = stemMatcher.group(2) == null ? null : stemMatcher.group(2);
      series = stemMatcher.group(3) == null ? null : stemMatcher.group(3);
      subSeries = stemMatcher.group(4) == null ? null : stemMatcher.group(4);
      everythingElse = stemMatcher.group(5) == null ? null : stemMatcher.group(5);
    } else {
      everythingElse = rawCallNum;
    }

    if (everythingElse != null) {
      Matcher suffixMatcher = suffixPattern.matcher(everythingElse);
      if (suffixMatcher.find()) {
        int start = suffixMatcher.start(1);
        suffix = start > 0 ? suffixMatcher.group() : null;
      } else {
        suffix = everythingElse;
      }
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
    boolean valid = true;
    if (this.authorSymbol == null) {
      valid = false;
    } else {
      char firstChar = this.authorSymbol.charAt(0);
      // SuDoc call numbers can't begin with numbers
      if (Character.isDigit(firstChar)) {
        valid = false;
      }
    }
    return valid;
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
      if (key.length() > 0) {
        key.append(' ');
      }
      part = part.trim();
      if (Character.isAlphabetic(part.charAt(0))) {
        key.append(" !");
      } else if (part.length() >= 3) {
        key.append("!");
      }

      Utils.appendNumericallySortable(key, part);
    }
  }
}
