package org.folio.services.instance;

import static org.folio.services.instance.PublicationPeriodParser.parsePublicationPeriod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.folio.rest.jaxrs.model.Publication;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class PublicationPeriodParserTest {

  @Test
  @Parameters({
    "1990, 1990, null",
    "[1990], 1990, null",
    "©1990, 1990, null",
    "[1999?], 1999, null",
    "Cop 1990, 1990, null",
    "[2003]\\\\, ©2001, 2003, null",
    "2001\\\\, 2003, 2001, 2003",
    "2001- 2003, 2001, 2003",
    "between 2001 and 2003, 2001, 2003",
  })
  public void shouldParseDateOfPublicationForSinglePublication(
    @Nullable String dateOfPublication, @Nullable Integer start, @Nullable Integer end) {

    var publication = new Publication().withDateOfPublication(dateOfPublication);

    var publicationPeriod = parsePublicationPeriod(List.of(publication));

    assertThat(publicationPeriod.getStart(), is(start));
    assertThat(publicationPeriod.getEnd(), is(end));
  }

  @Test
  @Parameters({
    "1990, null, 1990, null",
    "[1990], null, 1990, null",
    "©1990, null, 1990, null",
    "[1999?], null, 1999, null",
    "Cop 1990, null, 1990, null",
    "©2001, [2003], 2001, 2003",
    "©2001, [2001], 2001, null",
    "2001, 2003, 2001, 2003",
    "2001- 2003, null, 2001, 2003",
    "2003-2001, 2003, 2003, null",
    "2000-2010, 2012-2020, 2000, 2020",
    "null, 1999, null, 1999",
  })
  public void shouldParseDateOfPublicationForMultiplePublications(
    @Nullable String dateOfPublicationFirst, @Nullable String dateOfPublicationLast,
    @Nullable Integer start, @Nullable Integer end) {

    var firstPublication = new Publication().withDateOfPublication(dateOfPublicationFirst);
    var lastPublication = new Publication().withDateOfPublication(dateOfPublicationLast);

    var publicationPeriod = parsePublicationPeriod(List.of(firstPublication, lastPublication));

    assertThat(publicationPeriod.getStart(), is(start));
    assertThat(publicationPeriod.getEnd(), is(end));
  }

  @Test
  @Parameters({"[19uu]", "hafniae MCMLXX", "null"})
  public void shouldReturnNullPeriodForSinglePublication(@Nullable String dateOfPublication) {
    var publication = new Publication().withDateOfPublication(dateOfPublication);

    var publicationPeriod = parsePublicationPeriod(List.of(publication));

    assertThat(publicationPeriod, nullValue());
  }

  @Test
  @Parameters({"[19uu], null", "hafniae MCMLXX, null", "null, null"})
  public void shouldReturnNullPeriodForMultiplePublications(
    @Nullable String firstDate, @Nullable String lastDate) {
    var firstPublication = new Publication().withDateOfPublication(firstDate);
    var lastPublication = new Publication().withDateOfPublication(lastDate);

    var publicationPeriod = parsePublicationPeriod(List.of(firstPublication, lastPublication));

    assertThat(publicationPeriod, nullValue());
  }
}
