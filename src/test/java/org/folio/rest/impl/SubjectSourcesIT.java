package org.folio.rest.impl;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.model.SubjectSources;
import org.folio.services.subjectsource.SubjectSourceService;

public class SubjectSourcesIT extends BaseReferenceDataIntegrationTest<SubjectSource, SubjectSources>{

  @Override
  protected String referenceTable() {
    return SubjectSourceService.SUBJECT_SOURCE;
  }

  @Override
  protected String resourceUrl() {
    return "/subject-sources";
  }

  @Override
  protected Class<SubjectSource> targetClass() {
    return SubjectSource.class;
  }

  @Override
  protected Class<SubjectSources> collectionClass() {
    return SubjectSources.class;
  }

  @Override
  protected SubjectSource sampleRecord() {
    return new SubjectSource()
      .withId(UUID.randomUUID().toString())
      .withName("test_name")
      .withSource(SubjectSource.Source.LOCAL);
  }

  @Override
  protected Function<SubjectSources, List<SubjectSource>> collectionRecordsExtractor() {
    return SubjectSources::getSubjectSources;
  }

  @Override
  protected List<Function<SubjectSource, Object>> recordFieldExtractors() {
    return List.of(SubjectSource::getName, SubjectSource::getSource);
  }

  @Override
  protected Function<SubjectSource, String> idExtractor() {
    return SubjectSource::getId;
  }

  @Override
  protected Function<SubjectSource, Metadata> metadataExtractor() {
    return SubjectSource::getMetadata;
  }

  @Override
  protected UnaryOperator<SubjectSource> recordModifyingFunction() {
    return subjectSource -> subjectSource.withName("updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test_name");
  }
}
