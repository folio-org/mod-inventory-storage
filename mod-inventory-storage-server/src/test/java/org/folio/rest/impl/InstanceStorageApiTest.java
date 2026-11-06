package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstancePatchRequest;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.jaxrs.model.RetrieveEntitiesRequest;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.interfaces.Results;
import org.folio.services.instance.InstanceService;
import org.folio.services.referencedata.ReferenceDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstanceStorageApiTest {

  private static final String NOT_SHADOW_COPY_FILTER =
    " and source <> \"CONSORTIUM-MARC\" and source <> \"CONSORTIUM-FOLIO\"";
  private static final int OFFSET = 5;
  private static final int LIMIT = 10;
  private static final String INSTANCE_ID = "6b4a1a8e-0f0d-4f0b-a4a0-6a0d2c1d9e11";
  private static final String RELATIONSHIP_ID = "0d0a8a2e-6b1c-4d6a-9d0e-5c6f7a8b9c10";
  private static final int SC_OK = 200;
  private static final int SC_CREATED = 201;
  private static final int SC_NO_CONTENT = 204;
  private static final int SC_BAD_REQUEST = 400;
  private static final int SC_NOT_FOUND = 404;
  private static final int SC_SERVER_ERROR = 500;

  private final Map<String, String> headers = Map.of();
  private final AtomicReference<Response> captured = new AtomicReference<>();
  private final Handler<AsyncResult<Response>> handler = result -> captured.set(result.result());

  @Mock
  private RoutingContext routingContext;
  @Mock
  private Context vertxContext;
  @Mock
  private InstanceService instanceService;
  @Mock
  private ReferenceDataService referenceDataService;
  @InjectMocks
  private InstanceStorageApi api;

  @ParameterizedTest
  @MethodSource("queriesWithShadowCopiesExcluded")
  @DisplayName("should exclude shadow copies from the executed query when includeShadowCopies is false")
  void shouldExcludeShadowCopiesFromQuery_whenIncludeShadowCopiesIsFalse(String query, String expectedQuery) {
    stubStreamFailure();

    api.getInstanceStorageInstances(false, null, OFFSET, LIMIT, query, routingContext, headers, null,
      vertxContext);

    verify(instanceService).streamInstances(expectedQuery, OFFSET, LIMIT, headers);
  }

  @ParameterizedTest
  @MethodSource("queriesWithShadowCopiesIncluded")
  @DisplayName("should pass the query through unchanged when includeShadowCopies is true")
  void shouldNotModifyQuery_whenIncludeShadowCopiesIsTrue(String query) {
    stubStreamFailure();

    api.getInstanceStorageInstances(true, null, OFFSET, LIMIT, query, routingContext, headers, null,
      vertxContext);

    verify(instanceService).streamInstances(query, OFFSET, LIMIT, headers);
  }

  @Test
  @DisplayName("should respond 201 when creating an instance succeeds")
  void shouldRespondCreated_whenCreateInstanceSucceeds() {
    when(instanceService.createInstance(any(), any()))
      .thenReturn(Future.succeededFuture(new Instance().withId(INSTANCE_ID)));

    api.postInstanceStorageInstances(new Instance(), routingContext, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should map the failure to a 404 response when creating an instance fails with not found")
  void shouldReturn404_whenCreateInstanceFailsWithNotFound() {
    when(instanceService.createInstance(any(), any()))
      .thenReturn(Future.failedFuture(new NotFoundException("missing")));

    api.postInstanceStorageInstances(new Instance(), routingContext, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should respond 204 when updating an instance succeeds")
  void shouldRespondNoContent_whenUpdateInstanceSucceeds() {
    var entity = new Instance();
    when(instanceService.updateInstance(INSTANCE_ID, entity, headers))
      .thenReturn(Future.succeededFuture());

    api.putInstanceStorageInstancesByInstanceId(INSTANCE_ID, entity, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should map the failure to a 500 response when updating an instance fails unexpectedly")
  void shouldReturn500_whenUpdateInstanceFails() {
    when(instanceService.updateInstance(any(), any(), any()))
      .thenReturn(Future.failedFuture(new IllegalStateException("boom")));

    api.putInstanceStorageInstancesByInstanceId(INSTANCE_ID, new Instance(), headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
  }

  @Test
  @DisplayName("should respond 204 when patching an instance succeeds")
  void shouldRespondNoContent_whenPatchInstanceSucceeds() {
    var request = new InstancePatchRequest();
    when(instanceService.patchInstance(INSTANCE_ID, request, headers))
      .thenReturn(Future.succeededFuture());

    api.patchInstanceStorageInstancesByInstanceId(INSTANCE_ID, request, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should map the failure to a 400 response when patching an instance fails with bad request")
  void shouldReturn400_whenPatchInstanceFails() {
    when(instanceService.patchInstance(any(), any(), any()))
      .thenReturn(Future.failedFuture(new BadRequestException("bad")));

    api.patchInstanceStorageInstancesByInstanceId(INSTANCE_ID, new InstancePatchRequest(), headers, handler,
      vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should respond 204 when deleting an instance succeeds")
  void shouldRespondNoContent_whenDeleteInstanceSucceeds() {
    when(instanceService.deleteInstance(INSTANCE_ID, headers))
      .thenReturn(Future.succeededFuture());

    api.deleteInstanceStorageInstancesByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should map the failure to a 404 response when deleting an instance fails with not found")
  void shouldReturn404_whenDeleteInstanceFails() {
    when(instanceService.deleteInstance(any(), any()))
      .thenReturn(Future.failedFuture(new NotFoundException("missing")));

    api.deleteInstanceStorageInstancesByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should respond 204 when deleting instances by query succeeds")
  void shouldRespondNoContent_whenDeleteInstancesByQuerySucceeds() {
    when(instanceService.deleteInstances("title=foo", headers))
      .thenReturn(Future.succeededFuture());

    api.deleteInstanceStorageInstances("title=foo", routingContext, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should map the failure to a response when deleting instances by query fails")
  void shouldReturnErrorResponse_whenDeleteInstancesByQueryFails() {
    when(instanceService.deleteInstances(any(), any()))
      .thenReturn(Future.failedFuture(new IllegalStateException("boom")));

    api.deleteInstanceStorageInstances("title=foo", routingContext, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
  }

  @Test
  @DisplayName("should respond 200 when getting an instance by id succeeds")
  void shouldRespondOk_whenGetInstanceSucceeds() {
    when(instanceService.getInstance(INSTANCE_ID, headers))
      .thenReturn(Future.succeededFuture(new Instance()));

    api.getInstanceStorageInstancesByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_OK);
  }

  @Test
  @DisplayName("should map the failure to a 404 response when getting an instance by id fails with not found")
  void shouldReturn404_whenGetInstanceFails() {
    when(instanceService.getInstance(any(), any()))
      .thenReturn(Future.failedFuture(new NotFoundException("missing")));

    api.getInstanceStorageInstancesByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should respond 200 when getting an instance summary succeeds")
  void shouldRespondOk_whenGetInstanceSummarySucceeds() {
    when(instanceService.getInstanceSummary(INSTANCE_ID, headers))
      .thenReturn(Future.succeededFuture("{}"));

    api.getInstanceStorageInstancesSummaryByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_OK);
  }

  @Test
  @DisplayName("should map the failure to a 404 response when getting an instance summary fails with not found")
  void shouldReturn404_whenGetInstanceSummaryFails() {
    when(instanceService.getInstanceSummary(any(), any()))
      .thenReturn(Future.failedFuture(new NotFoundException("missing")));

    api.getInstanceStorageInstancesSummaryByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should stream instances with the request's own query and paging when retrieving")
  void shouldStreamInstances_whenRetrieving() {
    var request = new RetrieveEntitiesRequest().withQuery("id==1").withOffset(OFFSET).withLimit(LIMIT);
    stubStreamFailure();

    api.postInstanceStorageInstancesRetrieve(request, routingContext, headers, handler, vertxContext);

    verify(instanceService).streamInstances("id==1", OFFSET, LIMIT, headers);
  }

  @Test
  @DisplayName("should respond 201 when creating an instance relationship")
  void shouldRespondCreated_whenPostingInstanceRelationship() {
    var entity = new InstanceRelationship().withId(RELATIONSHIP_ID);
    when(referenceDataService.create("instance_relationship", InstanceRelationship.class, entity, headers))
      .thenReturn(Future.succeededFuture(entity));

    api.postInstanceStorageInstanceRelationships(entity, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should respond 204 when deleting an instance source record")
  void shouldRespondNoContent_whenDeletingSourceRecord() {
    when(referenceDataService.delete("instance_source_marc", MarcJson.class, INSTANCE_ID, headers))
      .thenReturn(Future.succeededFuture());

    api.deleteInstanceStorageInstancesSourceRecordByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should respond 204 when deleting an instance source record marc json")
  void shouldRespondNoContent_whenDeletingSourceRecordMarcJson() {
    when(referenceDataService.delete("instance_source_marc", MarcJson.class, INSTANCE_ID, headers))
      .thenReturn(Future.succeededFuture());

    api.deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, headers, handler,
      vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should respond 200 when getting an instance source record marc json")
  void shouldRespondOk_whenGettingSourceRecordMarcJson() {
    when(referenceDataService.getById("instance_source_marc", MarcJson.class, INSTANCE_ID, headers))
      .thenReturn(Future.succeededFuture(new MarcJson()));

    api.getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_OK);
  }

  @Test
  @DisplayName("should throw when getting an instance relationship by id, which is not supported")
  void shouldThrow_whenGettingRelationshipById() {
    assertThatThrownBy(() -> api.getInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID,
      headers, handler, vertxContext)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("should respond 204 when deleting an instance relationship succeeds")
  void shouldReturn204_whenDeleteRelationshipSucceeds() {
    when(referenceDataService.deleteIfExists("instance_relationship", InstanceRelationship.class,
      RELATIONSHIP_ID, headers)).thenReturn(Future.succeededFuture());

    api.deleteInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should respond 500 when deleting an instance relationship fails")
  void shouldReturn500_whenDeleteRelationshipFails() {
    when(referenceDataService.deleteIfExists("instance_relationship", InstanceRelationship.class,
      RELATIONSHIP_ID, headers)).thenReturn(Future.failedFuture("db down"));

    api.deleteInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    assertThat(captured.get().getEntity()).isEqualTo("db down");
  }

  @Test
  @DisplayName("should respond 204 and default the id when updating an instance relationship succeeds")
  void shouldReturn204_whenPutRelationshipSucceeds() {
    var entity = new InstanceRelationship();
    when(referenceDataService.update("instance_relationship", InstanceRelationship.class, RELATIONSHIP_ID, entity,
      headers)).thenReturn(Future.succeededFuture());

    api.putInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, entity, headers, handler,
      vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
    assertThat(entity.getId()).isEqualTo(RELATIONSHIP_ID);
  }

  @Test
  @DisplayName("should respond 404 when updating an instance relationship changes no rows")
  void shouldReturn404_whenPutRelationshipUpdatesNothing() {
    when(referenceDataService.update(any(), any(), any(), any(), any()))
      .thenReturn(Future.failedFuture(new NotFoundException("Not found")));

    api.putInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, new InstanceRelationship(),
      headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should respond 500 when updating an instance relationship fails")
  void shouldReturn500_whenPutRelationshipFails() {
    when(referenceDataService.update(any(), any(), any(), any(), any()))
      .thenReturn(Future.failedFuture(new IllegalStateException("boom")));

    api.putInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, new InstanceRelationship(),
      headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
  }

  @Test
  @DisplayName("should respond 204 when upserting the marc json source record succeeds")
  void shouldReturn204_whenPutMarcJsonSucceeds() {
    when(referenceDataService.upsert(eq("instance_source_marc"), eq(MarcJson.class), eq(INSTANCE_ID), any(), any()))
      .thenReturn(Future.succeededFuture());

    api.putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, new MarcJson(), headers,
      handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should respond 404 when upserting the marc json source record violates the instance foreign key")
  void shouldReturn404_whenPutMarcJsonViolatesForeignKey() {
    when(referenceDataService.upsert(eq("instance_source_marc"), eq(MarcJson.class), eq(INSTANCE_ID), any(), any()))
      .thenReturn(Future.failedFuture("violates foreign key on instance_source_marc"));

    try (var pgException = mockStatic(PgExceptionUtil.class)) {
      pgException.when(() -> PgExceptionUtil.isForeignKeyViolation(any())).thenReturn(true);

      api.putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, new MarcJson(), headers,
        handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
    }
  }

  @Test
  @DisplayName("should respond 500 when upserting the marc json source record fails for another reason")
  void shouldReturn500_whenPutMarcJsonFails() {
    when(referenceDataService.upsert(eq("instance_source_marc"), eq(MarcJson.class), eq(INSTANCE_ID), any(), any()))
      .thenReturn(Future.failedFuture("db down"));

    api.putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, new MarcJson(), headers,
      handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    assertThat(captured.get().getEntity()).isEqualTo("db down");
  }

  @Test
  @DisplayName("should respond 200 with the relationships and total when the relationships query succeeds")
  void shouldReturn200WithRelationships_whenGetRelationshipsSucceeds() {
    var relationship = new InstanceRelationship().withId(RELATIONSHIP_ID);
    var resultInfo = new ResultInfo();
    resultInfo.setTotalRecords(1);
    var results = new Results<InstanceRelationship>();
    results.setResults(List.of(relationship));
    results.setResultInfo(resultInfo);
    when(referenceDataService.query("instance_relationship", InstanceRelationship.class, "id=1", OFFSET, LIMIT,
      null, headers)).thenReturn(Future.succeededFuture(results));

    api.getInstanceStorageInstanceRelationships(null, OFFSET, LIMIT, "id=1", headers, handler, vertxContext);

    var body = (InstanceRelationships) captured.get().getEntity();
    assertThat(captured.get().getStatus()).isEqualTo(SC_OK);
    assertThat(body.getInstanceRelationships()).containsExactly(relationship);
    assertThat(body.getTotalRecords()).isEqualTo(1);
  }

  @Test
  @DisplayName("should respond 500 when the relationships query fails")
  void shouldReturn500_whenGetRelationshipsQueryFails() {
    when(referenceDataService.query(any(), any(), any(), anyInt(), anyInt(), any(), any()))
      .thenReturn(Future.failedFuture("db down"));

    api.getInstanceStorageInstanceRelationships(null, OFFSET, LIMIT, "id=1", headers, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
  }

  private void stubStreamFailure() {
    when(instanceService.streamInstances(nullable(String.class), anyInt(), anyInt(), any()))
      .thenReturn(Future.failedFuture(new BadRequestException("stub")));
    var mockResponse = mock(HttpServerResponse.class);
    when(routingContext.response()).thenReturn(mockResponse);
  }

  private static Stream<Arguments> queriesWithShadowCopiesExcluded() {
    return Stream.of(
      Arguments.of("title=foo", "(title=foo)" + NOT_SHADOW_COPY_FILTER),
      Arguments.of("title=a or title=b", "(title=a or title=b)" + NOT_SHADOW_COPY_FILTER),
      Arguments.of(null, "(cql.allRecords=1)" + NOT_SHADOW_COPY_FILTER),
      Arguments.of("", "(cql.allRecords=1)" + NOT_SHADOW_COPY_FILTER),
      Arguments.of("  ", "(cql.allRecords=1)" + NOT_SHADOW_COPY_FILTER)
    );
  }

  private static Stream<String> queriesWithShadowCopiesIncluded() {
    return Stream.of("title=foo", "cql.allRecords=1", null);
  }
}
