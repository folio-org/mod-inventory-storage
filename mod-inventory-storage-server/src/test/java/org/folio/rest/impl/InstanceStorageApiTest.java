package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.rest.support.PostgresClientFactory.getInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.RowSet;
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
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.services.instance.InstanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstanceStorageApiTest {

  private static final String INSTANCE_TABLE = "instance";
  private static final String INSTANCES_ELEMENT = "instances";
  private static final String NOT_SHADOW_COPY_FILTER =
    " and source <> \"CONSORTIUM-MARC\" and source <> \"CONSORTIUM-FOLIO\"";
  private static final int OFFSET = 5;
  private static final int LIMIT = 10;
  private static final String INSTANCE_ID = "6b4a1a8e-0f0d-4f0b-a4a0-6a0d2c1d9e11";
  private static final String RELATIONSHIP_ID = "0d0a8a2e-6b1c-4d6a-9d0e-5c6f7a8b9c10";
  private static final String NOT_IMPLEMENTED = "Not implemented yet.";
  private static final int SC_OK = 200;
  private static final int SC_CREATED = 201;
  private static final int SC_NO_CONTENT = 204;
  private static final int SC_BAD_REQUEST = 400;
  private static final int SC_NOT_FOUND = 404;
  private static final int SC_SERVER_ERROR = 500;

  private final InstanceStorageApi api = new InstanceStorageApi();
  private final Map<String, String> okapiHeaders = Map.of();
  private final AtomicReference<Response> captured = new AtomicReference<>();
  private final Handler<AsyncResult<Response>> handler = result -> captured.set(result.result());

  @Mock
  private RoutingContext routingContext;
  @Mock
  private Context vertxContext;

  @ParameterizedTest
  @MethodSource("queriesWithShadowCopiesExcluded")
  @DisplayName("should exclude shadow copies from the executed query when includeShadowCopies is false")
  void shouldExcludeShadowCopiesFromQuery_whenIncludeShadowCopiesIsFalse(String query, String expectedQuery) {
    try (var pgUtil = mockStatic(PgUtil.class)) {
      api.getInstanceStorageInstances(false, null, OFFSET, LIMIT, query, routingContext, okapiHeaders, null,
        vertxContext);

      pgUtil.verify(() -> PgUtil.streamGet(eq(INSTANCE_TABLE), eq(Instance.class), eq(expectedQuery), eq(OFFSET),
        eq(LIMIT), any(), eq(INSTANCES_ELEMENT), eq(routingContext), eq(okapiHeaders), eq(vertxContext)));
    }
  }

  @ParameterizedTest
  @MethodSource("queriesWithShadowCopiesIncluded")
  @DisplayName("should pass the query through unchanged when includeShadowCopies is true")
  void shouldNotModifyQuery_whenIncludeShadowCopiesIsTrue(String query) {
    try (var pgUtil = mockStatic(PgUtil.class)) {
      api.getInstanceStorageInstances(true, null, OFFSET, LIMIT, query, routingContext, okapiHeaders, null,
        vertxContext);

      pgUtil.verify(() -> PgUtil.streamGet(eq(INSTANCE_TABLE), eq(Instance.class), eq(query), eq(OFFSET),
        eq(LIMIT), any(), eq(INSTANCES_ELEMENT), eq(routingContext), eq(okapiHeaders), eq(vertxContext)));
    }
  }

  @Test
  @DisplayName("should return the service response when creating an instance succeeds")
  void shouldReturnServiceResponse_whenCreateInstanceSucceeds() {
    var created = Response.status(SC_CREATED).build();
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.createInstance(any()))
        .thenReturn(Future.succeededFuture(created)))) {

      api.postInstanceStorageInstances(new Instance(), routingContext, okapiHeaders, handler, vertxContext);

      assertThat(captured.get()).isSameAs(created);
    }
  }

  @Test
  @DisplayName("should map the failure to a 404 response when creating an instance fails with not found")
  void shouldReturn404_whenCreateInstanceFailsWithNotFound() {
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.createInstance(any()))
        .thenReturn(Future.failedFuture(new NotFoundException("missing"))))) {

      api.postInstanceStorageInstances(new Instance(), routingContext, okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
    }
  }

  @Test
  @DisplayName("should return the service response when updating an instance succeeds")
  void shouldReturnServiceResponse_whenUpdateInstanceSucceeds() {
    var noContent = Response.noContent().build();
    var entity = new Instance();
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.updateInstance(INSTANCE_ID, entity))
        .thenReturn(Future.succeededFuture(noContent)))) {

      api.putInstanceStorageInstancesByInstanceId(INSTANCE_ID, entity, okapiHeaders, handler, vertxContext);

      assertThat(captured.get()).isSameAs(noContent);
    }
  }

  @Test
  @DisplayName("should map the failure to a 500 response when updating an instance fails unexpectedly")
  void shouldReturn500_whenUpdateInstanceFails() {
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.updateInstance(any(), any()))
        .thenReturn(Future.failedFuture(new IllegalStateException("boom"))))) {

      api.putInstanceStorageInstancesByInstanceId(INSTANCE_ID, new Instance(), okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    }
  }

  @Test
  @DisplayName("should return the service response when patching an instance succeeds")
  void shouldReturnServiceResponse_whenPatchInstanceSucceeds() {
    var noContent = Response.noContent().build();
    var request = new InstancePatchRequest();
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.patchInstance(INSTANCE_ID, request))
        .thenReturn(Future.succeededFuture(noContent)))) {

      api.patchInstanceStorageInstancesByInstanceId(INSTANCE_ID, request, okapiHeaders, handler, vertxContext);

      assertThat(captured.get()).isSameAs(noContent);
    }
  }

  @Test
  @DisplayName("should map the failure to a 400 response when patching an instance fails with bad request")
  void shouldReturn400_whenPatchInstanceFails() {
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.patchInstance(any(), any()))
        .thenReturn(Future.failedFuture(new BadRequestException("bad"))))) {

      api.patchInstanceStorageInstancesByInstanceId(INSTANCE_ID, new InstancePatchRequest(), okapiHeaders, handler,
        vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_BAD_REQUEST);
    }
  }

  @Test
  @DisplayName("should return the service response when deleting an instance succeeds")
  void shouldReturnServiceResponse_whenDeleteInstanceSucceeds() {
    var noContent = Response.noContent().build();
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.deleteInstance(INSTANCE_ID))
        .thenReturn(Future.succeededFuture(noContent)))) {

      api.deleteInstanceStorageInstancesByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

      assertThat(captured.get()).isSameAs(noContent);
    }
  }

  @Test
  @DisplayName("should map the failure to a 404 response when deleting an instance fails with not found")
  void shouldReturn404_whenDeleteInstanceFails() {
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.deleteInstance(any()))
        .thenReturn(Future.failedFuture(new NotFoundException("missing"))))) {

      api.deleteInstanceStorageInstancesByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
    }
  }

  @Test
  @DisplayName("should return the service response when deleting instances by query succeeds")
  void shouldReturnServiceResponse_whenDeleteInstancesByQuerySucceeds() {
    var noContent = Response.noContent().build();
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.deleteInstances("title=foo"))
        .thenReturn(Future.succeededFuture(noContent)))) {

      api.deleteInstanceStorageInstances("title=foo", routingContext, okapiHeaders, handler, vertxContext);

      assertThat(captured.get()).isSameAs(noContent);
    }
  }

  @Test
  @DisplayName("should map the failure to a response when deleting instances by query fails")
  void shouldReturnErrorResponse_whenDeleteInstancesByQueryFails() {
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.deleteInstances(any()))
        .thenReturn(Future.failedFuture(new IllegalStateException("boom"))))) {

      api.deleteInstanceStorageInstances("title=foo", routingContext, okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    }
  }

  @Test
  @DisplayName("should return the service response when getting an instance by id succeeds")
  void shouldReturnServiceResponse_whenGetInstanceSucceeds() {
    var ok = Response.ok().build();
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.getInstance(INSTANCE_ID))
        .thenReturn(Future.succeededFuture(ok)))) {

      api.getInstanceStorageInstancesByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

      assertThat(captured.get()).isSameAs(ok);
    }
  }

  @Test
  @DisplayName("should map the failure to a 404 response when getting an instance by id fails with not found")
  void shouldReturn404_whenGetInstanceFails() {
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.getInstance(any()))
        .thenReturn(Future.failedFuture(new NotFoundException("missing"))))) {

      api.getInstanceStorageInstancesByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
    }
  }

  @Test
  @DisplayName("should return the service response when getting an instance summary succeeds")
  void shouldReturnServiceResponse_whenGetInstanceSummarySucceeds() {
    var ok = Response.ok().build();
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.getInstanceSummary(INSTANCE_ID))
        .thenReturn(Future.succeededFuture(ok)))) {

      api.getInstanceStorageInstancesSummaryByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

      assertThat(captured.get()).isSameAs(ok);
    }
  }

  @Test
  @DisplayName("should map the failure to a 404 response when getting an instance summary fails with not found")
  void shouldReturn404_whenGetInstanceSummaryFails() {
    try (var ignored = mockConstruction(InstanceService.class,
      (mock, ctx) -> when(mock.getInstanceSummary(any()))
        .thenReturn(Future.failedFuture(new NotFoundException("missing"))))) {

      api.getInstanceStorageInstancesSummaryByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
    }
  }

  @Test
  @DisplayName("should stream instances with the request's own query and paging when retrieving")
  void shouldStreamInstances_whenRetrieving() {
    var request = new RetrieveEntitiesRequest().withQuery("id==1").withOffset(OFFSET).withLimit(LIMIT);
    try (var pgUtil = mockStatic(PgUtil.class)) {
      api.postInstanceStorageInstancesRetrieve(request, routingContext, okapiHeaders, handler, vertxContext);

      pgUtil.verify(() -> PgUtil.streamGet(eq(INSTANCE_TABLE), eq(Instance.class), eq("id==1"), eq(OFFSET),
        eq(LIMIT), any(), eq(INSTANCES_ELEMENT), eq(routingContext), eq(okapiHeaders), eq(vertxContext)));
    }
  }

  @Test
  @DisplayName("should delegate to PgUtil when creating an instance relationship")
  void shouldDelegateToPgUtil_whenPostingInstanceRelationship() {
    var entity = new InstanceRelationship();
    try (var pgUtil = mockStatic(PgUtil.class)) {
      api.postInstanceStorageInstanceRelationships(entity, okapiHeaders, handler, vertxContext);

      pgUtil.verify(() -> PgUtil.post(eq("instance_relationship"), eq(entity), eq(okapiHeaders), eq(vertxContext),
        any(), eq(handler)));
    }
  }

  // ---- PgUtil delegation ----

  @Test
  @DisplayName("should delegate to PgUtil when deleting an instance source record")
  void shouldDelegateToPgUtil_whenDeletingSourceRecord() {
    try (var pgUtil = mockStatic(PgUtil.class)) {
      api.deleteInstanceStorageInstancesSourceRecordByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

      pgUtil.verify(() -> PgUtil.deleteById(eq("instance_source_marc"), eq(INSTANCE_ID), eq(okapiHeaders),
        eq(vertxContext), any(), eq(handler)));
    }
  }

  @Test
  @DisplayName("should delegate to PgUtil when deleting an instance source record marc json")
  void shouldDelegateToPgUtil_whenDeletingSourceRecordMarcJson() {
    try (var pgUtil = mockStatic(PgUtil.class)) {
      api.deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, okapiHeaders, handler,
        vertxContext);

      pgUtil.verify(() -> PgUtil.deleteById(eq("instance_source_marc"), eq(INSTANCE_ID), eq(okapiHeaders),
        eq(vertxContext), any(), eq(handler)));
    }
  }

  @Test
  @DisplayName("should delegate to PgUtil when getting an instance source record marc json")
  void shouldDelegateToPgUtil_whenGettingSourceRecordMarcJson() {
    try (var pgUtil = mockStatic(PgUtil.class)) {
      api.getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, okapiHeaders, handler,
        vertxContext);

      pgUtil.verify(() -> PgUtil.getById(eq("instance_source_marc"), eq(MarcJson.class), eq(INSTANCE_ID),
        eq(okapiHeaders), eq(vertxContext), any(), eq(handler)));
    }
  }

  @Test
  @DisplayName("should respond 500 not implemented when getting the mods source record")
  void shouldReturn500_whenGettingModsSourceRecord() {
    api.getInstanceStorageInstancesSourceRecordModsByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    assertThat(captured.get().getEntity()).isEqualTo(NOT_IMPLEMENTED);
  }

  @Test
  @DisplayName("should respond 500 not implemented when putting the mods source record")
  void shouldReturn500_whenPuttingModsSourceRecord() {
    api.putInstanceStorageInstancesSourceRecordModsByInstanceId(INSTANCE_ID, okapiHeaders, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    assertThat(captured.get().getEntity()).isEqualTo(NOT_IMPLEMENTED);
  }

  @Test
  @DisplayName("should throw when getting an instance relationship by id, which is not supported")
  void shouldThrow_whenGettingRelationshipById() {
    assertThatThrownBy(() -> api.getInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID,
      okapiHeaders, handler, vertxContext)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("should respond 204 when deleting an instance relationship succeeds")
  void shouldReturn204_whenDeleteRelationshipSucceeds() {
    var postgresClient = mock(PostgresClient.class);
    completeWith(postgresClient, Future.succeededFuture(mock(RowSet.class)))
      .delete(any(String.class), eq(RELATIONSHIP_ID), any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.deleteInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, okapiHeaders, handler,
        vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
    }
  }

  @Test
  @DisplayName("should respond 500 when deleting an instance relationship fails")
  void shouldReturn500_whenDeleteRelationshipFails() {
    var postgresClient = mock(PostgresClient.class);
    completeWith(postgresClient, Future.failedFuture("db down"))
      .delete(any(String.class), eq(RELATIONSHIP_ID), any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.deleteInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, okapiHeaders, handler,
        vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
      assertThat(captured.get().getEntity()).isEqualTo("db down");
    }
  }

  // ---- PostgresClient based handlers ----

  @Test
  @DisplayName("should respond 204 and default the id when updating an instance relationship succeeds")
  void shouldReturn204_whenPutRelationshipSucceeds() {
    var postgresClient = mock(PostgresClient.class);
    var updated = mock(RowSet.class);
    when(updated.rowCount()).thenReturn(1);
    runOnContextImmediately();
    completeWith(postgresClient, Future.succeededFuture(updated))
      .update(any(String.class), any(Object.class), eq(RELATIONSHIP_ID), any());
    var entity = new InstanceRelationship();

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.putInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, entity, okapiHeaders, handler,
        vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
      assertThat(entity.getId()).isEqualTo(RELATIONSHIP_ID);
    }
  }

  @Test
  @DisplayName("should respond 404 when updating an instance relationship changes no rows")
  void shouldReturn404_whenPutRelationshipUpdatesNothing() {
    var postgresClient = mock(PostgresClient.class);
    var updated = mock(RowSet.class);
    when(updated.rowCount()).thenReturn(0);
    runOnContextImmediately();
    completeWith(postgresClient, Future.succeededFuture(updated))
      .update(any(String.class), any(Object.class), eq(RELATIONSHIP_ID), any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.putInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, new InstanceRelationship(),
        okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
    }
  }

  @Test
  @DisplayName("should respond 500 when updating an instance relationship fails")
  void shouldReturn500_whenPutRelationshipFails() {
    var postgresClient = mock(PostgresClient.class);
    runOnContextImmediately();
    completeWith(postgresClient, Future.failedFuture(new IllegalStateException("boom")))
      .update(any(String.class), any(Object.class), eq(RELATIONSHIP_ID), any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.putInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, new InstanceRelationship(),
        okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    }
  }

  @Test
  @DisplayName("should respond 400 when updating an instance relationship fails with a bad request cause")
  void shouldReturn400_whenPutRelationshipFailsWithBadRequestCause() {
    var postgresClient = mock(PostgresClient.class);
    runOnContextImmediately();
    completeWith(postgresClient, Future.failedFuture(new IllegalStateException("boom")))
      .update(any(String.class), any(Object.class), eq(RELATIONSHIP_ID), any());

    try (var ignored = mockPostgresClientFactory(postgresClient);
         var pgException = mockStatic(PgExceptionUtil.class)) {
      pgException.when(() -> PgExceptionUtil.badRequestMessage(any())).thenReturn("invalid");

      api.putInstanceStorageInstanceRelationshipsByRelationshipId(RELATIONSHIP_ID, new InstanceRelationship(),
        okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_BAD_REQUEST);
      assertThat(captured.get().getEntity()).isEqualTo("invalid");
    }
  }

  @Test
  @DisplayName("should respond 204 when upserting the marc json source record succeeds")
  void shouldReturn204_whenPutMarcJsonSucceeds() {
    var postgresClient = mock(PostgresClient.class);
    completeWith(postgresClient, Future.succeededFuture("id"))
      .upsert(eq("instance_source_marc"), eq(INSTANCE_ID), any(Object.class), ArgumentMatchers.any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, new MarcJson(), okapiHeaders,
        handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NO_CONTENT);
    }
  }

  @Test
  @DisplayName("should respond 404 when upserting the marc json source record violates the instance foreign key")
  void shouldReturn404_whenPutMarcJsonViolatesForeignKey() {
    var postgresClient = mock(PostgresClient.class);
    completeWith(postgresClient, Future.failedFuture("violates foreign key on instance_source_marc"))
      .upsert(eq("instance_source_marc"), eq(INSTANCE_ID), any(Object.class), any());

    try (var ignored = mockPostgresClientFactory(postgresClient);
         var pgException = mockStatic(PgExceptionUtil.class)) {
      pgException.when(() -> PgExceptionUtil.isForeignKeyViolation(any())).thenReturn(true);

      api.putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, new MarcJson(), okapiHeaders,
        handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_NOT_FOUND);
    }
  }

  @Test
  @DisplayName("should respond 500 when upserting the marc json source record fails for another reason")
  void shouldReturn500_whenPutMarcJsonFails() {
    var postgresClient = mock(PostgresClient.class);
    completeWith(postgresClient, Future.failedFuture("db down"))
      .upsert(eq("instance_source_marc"), eq(INSTANCE_ID), any(Object.class), any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(INSTANCE_ID, new MarcJson(), okapiHeaders,
        handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
      assertThat(captured.get().getEntity()).isEqualTo("db down");
    }
  }

  @Test
  @DisplayName("should respond 500 when the relationships query cannot be scheduled on the context")
  void shouldReturn500_whenGetRelationshipsCannotRunOnContext() {
    doAnswer(invocation -> {
      throw new IllegalStateException("closed");
    }).when(vertxContext).runOnContext(any());

    api.getInstanceStorageInstanceRelationships(null, OFFSET, LIMIT, "id=1", okapiHeaders, handler, vertxContext);

    assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
    assertThat(captured.get().getEntity()).isEqualTo("closed");
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
    var postgresClient = mock(PostgresClient.class);
    runOnContextImmediately();
    completeWith(postgresClient, Future.succeededFuture(results))
      .get(any(String.class), eq(InstanceRelationship.class), any(String[].class), any(CQLWrapper.class), eq(true),
        eq(false), ArgumentMatchers.<Handler<AsyncResult<Results<InstanceRelationship>>>>any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.getInstanceStorageInstanceRelationships(null, OFFSET, LIMIT, "id=1", okapiHeaders, handler, vertxContext);

      var body = (InstanceRelationships) captured.get().getEntity();
      assertThat(captured.get().getStatus()).isEqualTo(SC_OK);
      assertThat(body.getInstanceRelationships()).containsExactly(relationship);
      assertThat(body.getTotalRecords()).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("should respond 500 when the relationships query fails")
  void shouldReturn500_whenGetRelationshipsQueryFails() {
    var postgresClient = mock(PostgresClient.class);
    runOnContextImmediately();
    completeWith(postgresClient, Future.failedFuture("db down"))
      .get(any(String.class), eq(InstanceRelationship.class), any(String[].class), any(CQLWrapper.class), eq(true),
        eq(false), ArgumentMatchers.<Handler<AsyncResult<Results<InstanceRelationship>>>>any());

    try (var ignored = mockPostgresClientFactory(postgresClient)) {
      api.getInstanceStorageInstanceRelationships(null, OFFSET, LIMIT, "id=1", okapiHeaders, handler, vertxContext);

      assertThat(captured.get().getStatus()).isEqualTo(SC_SERVER_ERROR);
      assertThat(captured.get().getEntity()).isEqualTo("db down");
    }
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

  private void runOnContextImmediately() {
    doAnswer(invocation -> {
      Handler<Void> task = invocation.getArgument(0);
      task.handle(null);
      return null;
    }).when(vertxContext).runOnContext(any());
  }

  private PostgresClient completeWith(PostgresClient postgresClient, Future<?> result) {
    var stubbing = doAnswer(invocation -> {
      Handler<AsyncResult<?>> callback = invocation.getArgument(invocation.getArguments().length - 1);
      callback.handle(result);
      return null;
    });
    return stubbing.when(postgresClient);
  }

  private org.mockito.MockedStatic<PostgresClientFactory> mockPostgresClientFactory(PostgresClient postgresClient) {
    var factory = mockStatic(PostgresClientFactory.class);
    factory.when(() -> getInstance(any(Context.class), ArgumentMatchers.<Map<String, String>>any()))
      .thenReturn(postgresClient);
    return factory;
  }
}
