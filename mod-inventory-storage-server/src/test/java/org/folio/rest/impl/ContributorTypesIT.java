package org.folio.rest.impl;

import static org.folio.rest.impl.ContributorTypeApi.CONTRIBUTOR_TYPE_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.ContributorType;
import org.folio.rest.jaxrs.model.ContributorTypes;
import org.folio.rest.jaxrs.model.Metadata;

class ContributorTypesIT extends BaseReferenceDataIntegrationTest<ContributorType, ContributorTypes> {

  @Override
  protected String referenceTable() {
    return CONTRIBUTOR_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/contributor-types";
  }

  @Override
  protected Class<ContributorType> targetClass() {
    return ContributorType.class;
  }

  @Override
  protected Class<ContributorTypes> collectionClass() {
    return ContributorTypes.class;
  }

  @Override
  protected ContributorType sampleRecord() {
    return new ContributorType().withName("test-type").withCode("contr-code").withSource("test-source");
  }

  @Override
  protected Function<ContributorTypes, List<ContributorType>> collectionRecordsExtractor() {
    return ContributorTypes::getContributorTypes;
  }

  @Override
  protected List<Function<ContributorType, Object>> recordFieldExtractors() {
    return List.of(ContributorType::getName, ContributorType::getSource);
  }

  @Override
  protected Function<ContributorType, String> idExtractor() {
    return ContributorType::getId;
  }

  @Override
  protected Function<ContributorType, Metadata> metadataExtractor() {
    return ContributorType::getMetadata;
  }

  @Override
  protected UnaryOperator<ContributorType> recordModifyingFunction() {
    return contributorType -> contributorType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }
}
