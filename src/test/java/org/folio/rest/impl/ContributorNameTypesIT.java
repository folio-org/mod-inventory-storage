package org.folio.rest.impl;

import static org.folio.rest.impl.ContributorNameTypeApi.CONTRIBUTOR_NAME_TYPE_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.ContributorNameType;
import org.folio.rest.jaxrs.model.ContributorNameTypes;
import org.folio.rest.jaxrs.model.Metadata;

class ContributorNameTypesIT extends BaseReferenceDataIntegrationTest<ContributorNameType, ContributorNameTypes> {

  @Override
  protected String referenceTable() {
    return CONTRIBUTOR_NAME_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/contributor-name-types";
  }

  @Override
  protected Class<ContributorNameType> targetClass() {
    return ContributorNameType.class;
  }

  @Override
  protected Class<ContributorNameTypes> collectionClass() {
    return ContributorNameTypes.class;
  }

  @Override
  protected ContributorNameType sampleRecord() {
    return new ContributorNameType().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<ContributorNameTypes, List<ContributorNameType>> collectionRecordsExtractor() {
    return ContributorNameTypes::getContributorNameTypes;
  }

  @Override
  protected List<Function<ContributorNameType, Object>> recordFieldExtractors() {
    return List.of(ContributorNameType::getName, ContributorNameType::getSource);
  }

  @Override
  protected Function<ContributorNameType, String> idExtractor() {
    return ContributorNameType::getId;
  }

  @Override
  protected Function<ContributorNameType, Metadata> metadataExtractor() {
    return ContributorNameType::getMetadata;
  }

  @Override
  protected UnaryOperator<ContributorNameType> recordModifyingFunction() {
    return contributorNameType -> contributorNameType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }
}
