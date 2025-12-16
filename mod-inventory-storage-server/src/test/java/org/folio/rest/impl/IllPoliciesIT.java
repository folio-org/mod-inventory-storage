package org.folio.rest.impl;

import static org.folio.rest.impl.IllPolicyApi.ILL_POLICY_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.IllPolicies;
import org.folio.rest.jaxrs.model.IllPolicy;
import org.folio.rest.jaxrs.model.Metadata;

class IllPoliciesIT extends BaseReferenceDataIntegrationTest<IllPolicy, IllPolicies> {

  @Override
  protected String referenceTable() {
    return ILL_POLICY_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/ill-policies";
  }

  @Override
  protected Class<IllPolicy> targetClass() {
    return IllPolicy.class;
  }

  @Override
  protected Class<IllPolicies> collectionClass() {
    return IllPolicies.class;
  }

  @Override
  protected IllPolicy sampleRecord() {
    return new IllPolicy().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<IllPolicies, List<IllPolicy>> collectionRecordsExtractor() {
    return IllPolicies::getIllPolicies;
  }

  @Override
  protected List<Function<IllPolicy, Object>> recordFieldExtractors() {
    return List.of(IllPolicy::getName, IllPolicy::getSource);
  }

  @Override
  protected Function<IllPolicy, String> idExtractor() {
    return IllPolicy::getId;
  }

  @Override
  protected Function<IllPolicy, Metadata> metadataExtractor() {
    return IllPolicy::getMetadata;
  }

  @Override
  protected UnaryOperator<IllPolicy> recordModifyingFunction() {
    return policy -> policy.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }
}
