package org.folio.rest.impl;

import static org.folio.rest.impl.NatureOfContentTermApi.NATURE_OF_CONTENT_TERM_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.rest.jaxrs.model.NatureOfContentTerms;

class NatureOfContentTermsIT extends BaseReferenceDataIntegrationTest<NatureOfContentTerm, NatureOfContentTerms> {

  @Override
  protected String referenceTable() {
    return NATURE_OF_CONTENT_TERM_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/nature-of-content-terms";
  }

  @Override
  protected Class<NatureOfContentTerm> targetClass() {
    return NatureOfContentTerm.class;
  }

  @Override
  protected Class<NatureOfContentTerms> collectionClass() {
    return NatureOfContentTerms.class;
  }

  @Override
  protected NatureOfContentTerm sampleRecord() {
    return new NatureOfContentTerm().withName("test-term").withSource("test-source");
  }

  @Override
  protected Function<NatureOfContentTerms, List<NatureOfContentTerm>> collectionRecordsExtractor() {
    return NatureOfContentTerms::getNatureOfContentTerms;
  }

  @Override
  protected List<Function<NatureOfContentTerm, Object>> recordFieldExtractors() {
    return List.of(NatureOfContentTerm::getName, NatureOfContentTerm::getSource);
  }

  @Override
  protected Function<NatureOfContentTerm, String> idExtractor() {
    return NatureOfContentTerm::getId;
  }

  @Override
  protected Function<NatureOfContentTerm, Metadata> metadataExtractor() {
    return NatureOfContentTerm::getMetadata;
  }

  @Override
  protected UnaryOperator<NatureOfContentTerm> recordModifyingFunction() {
    return term -> term.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-term", "source=test-source");
  }
}
