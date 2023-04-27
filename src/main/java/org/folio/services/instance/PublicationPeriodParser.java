package org.folio.services.instance;

import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.folio.rest.jaxrs.model.Publication;
import org.folio.rest.jaxrs.model.PublicationPeriod;

public final class PublicationPeriodParser {
  private static final Pattern START_YEAR = compile("(?<year>\\d{4})\\w{0,2}(\\s?-|\\sand|\\s?,)");
  private static final Pattern END_YEAR = compile("(-\\s?|and\\s|,\\s?)\\w{0,2}(?<year>\\d{4})");
  private static final Pattern ANY_YEAR = compile("(?<year>\\d{4})");

  private PublicationPeriodParser() { }

  public static PublicationPeriod parsePublicationPeriod(List<Publication> publications) {
    if (publications == null || publications.isEmpty()) {
      return null;
    }

    var startYear = parseStartYear(publications);
    var endYear = parseEndYear(publications);
    if (startYear == null && endYear == null) {
      return null;
    }

    var endYearToUse = startYear == null || startBeforeEnd(startYear, endYear) ? endYear : null;
    return new PublicationPeriod().withStart(startYear).withEnd(endYearToUse);
  }

  private static Integer parseStartYear(List<Publication> publications) {
    return parseYear(publications.get(0), START_YEAR);
  }

  private static Integer parseEndYear(List<Publication> publications) {
    var firstEndYear = parseYear(publications.get(0), END_YEAR);
    if (publications.size() == 1) {
      return firstEndYear;
    }

    var lastEndYear = parseYear(publications.get(publications.size() - 1), END_YEAR);
    return lastEndYear != null ? lastEndYear : firstEndYear;
  }

  private static Integer parseYear(Publication publication, Pattern pattern) {
    var dateOfPublication = publication.getDateOfPublication();
    if (isBlank(dateOfPublication)) {
      return null;
    }

    var year = getYear(pattern.matcher(dateOfPublication));
    return year != null ? year : getYear(ANY_YEAR.matcher(dateOfPublication));
  }

  private static Integer getYear(Matcher matcher) {
    return matcher.find() ? Integer.valueOf(matcher.group("year")) : null;
  }

  private static boolean startBeforeEnd(Integer start, Integer end) {
    return start != null && end != null && start < end;
  }
}
