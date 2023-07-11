package org.folio.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.marc4j.callnum.AbstractCallNumber;

public class SuDocCallNumber extends AbstractCallNumber {

  protected static Pattern stemPattern =
    Pattern.compile("^([A-Za-z]+)\\s*(\\d*.*\\d*)(\\d*-*\\d*)(\\d*/*\\d*)(:([A-Za-z]*\\s*\\S*))");
  protected static Pattern suffixPattern = Pattern.compile("^:([A-Za-z]*\\s*\\d*/*\\d*\\S*)");
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
      if (firstChar == '0' || firstChar == '1' || firstChar == '2'
        || firstChar == '3' || firstChar == '4' || firstChar == '5' || firstChar == '6'
        || firstChar == '7' || firstChar == '8' || firstChar == '9') {
        valid = false;
      }
    }
    return valid;
  }

  private void buildShelfKey() {
    StringBuilder key = new StringBuilder();
    if (authorSymbol != null) {
      key.append(authorSymbol);
    }

    if (subordinateOffice != null) {
      key.append(subordinateOffice);
    }

    if (series != null) {
      key.append(series);
    }

    if (subSeries != null) {
      key.append(subSeries);
    }

    if (suffix != null) {
      key.append(suffix);
    }

    shelfKey = key.toString();
  }
}
