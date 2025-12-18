package org.folio.rest.impl;

import static org.folio.rest.impl.ModeOfIssuanceApi.MODE_OF_ISSUANCE_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.IssuanceModes;
import org.folio.rest.jaxrs.model.Metadata;

class ModesOfIssuanceIT extends BaseReferenceDataIntegrationTest<IssuanceMode, IssuanceModes> {

  @Override
  protected String referenceTable() {
    return MODE_OF_ISSUANCE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/modes-of-issuance";
  }

  @Override
  protected Class<IssuanceMode> targetClass() {
    return IssuanceMode.class;
  }

  @Override
  protected Class<IssuanceModes> collectionClass() {
    return IssuanceModes.class;
  }

  @Override
  protected IssuanceMode sampleRecord() {
    return new IssuanceMode().withName("test-term").withSource("test-source");
  }

  @Override
  protected Function<IssuanceModes, List<IssuanceMode>> collectionRecordsExtractor() {
    return IssuanceModes::getIssuanceModes;
  }

  @Override
  protected List<Function<IssuanceMode, Object>> recordFieldExtractors() {
    return List.of(IssuanceMode::getName, IssuanceMode::getSource);
  }

  @Override
  protected Function<IssuanceMode, String> idExtractor() {
    return IssuanceMode::getId;
  }

  @Override
  protected Function<IssuanceMode, Metadata> metadataExtractor() {
    return IssuanceMode::getMetadata;
  }

  @Override
  protected UnaryOperator<IssuanceMode> recordModifyingFunction() {
    return mode -> mode.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-term", "source=test-source");
  }
}
