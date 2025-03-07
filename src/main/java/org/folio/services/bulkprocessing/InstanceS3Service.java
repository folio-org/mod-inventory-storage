package org.folio.services.bulkprocessing;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.rest.support.InstanceBulkProcessingUtil.mapBulkInstanceRecordToInstance;
import static org.folio.rest.support.ResponseUtil.isCreateSuccessResponse;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.ServerErrorException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitle;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.InstanceBulkProcessingUtil;
import org.folio.services.instance.InstanceService;
import org.folio.services.s3storage.FolioS3ClientFactory;

/**
 * The service that interacts with S3-compatible storage to perform upsert operations on instances retrieved
 * from external file. The file to be processed is specified through {@link BulkUpsertRequest}.
 */
public class InstanceS3Service extends AbstractEntityS3Service<InstanceS3Service.InstanceWrapper, Instance> {

  private static final Logger log = LogManager.getLogger(InstanceS3Service.class);
  private static final String PRECEDING_SUCCEEDING_TITLES_CQL_BUILD_ERROR_MSG =
    "buildPrecedingSucceedingTitlesCql:: Failed to build CQL wrapper for preceding and succeeding titles search";
  private static final String PRECEDING_SUCCEEDING_TITLES_BY_INSTANCE_IDS_CQL =
    "succeedingInstanceId==(%1$s) or precedingInstanceId==(%1$s)";
  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";
  private static final String PRECEDING_TITLES_FIELD = "precedingTitles";
  private static final String SUCCEEDING_TITLES_FIELD = "succeedingTitles";
  private static final String OR_OPERATOR = " or ";
  private static final String ID_FIELD = "id";
  private static final boolean APPLY_UPSERT = true;
  private static final boolean APPLY_OPTIMISTIC_LOCKING = true;

  private final InstanceService instanceService;
  private final PostgresClient postgresClient;
  private final InstanceRepository instanceRepository;

  public InstanceS3Service(FolioS3ClientFactory folioS3ClientFactory, Vertx vertx, Map<String, String> okapiHeaders) {
    super(folioS3ClientFactory, vertx);
    this.instanceService = new InstanceService(vertx.getOrCreateContext(), okapiHeaders);
    this.instanceRepository = new InstanceRepository(vertx.getOrCreateContext(), okapiHeaders);
    this.postgresClient = PgUtil.postgresClient(vertx.getOrCreateContext(), okapiHeaders);
  }

  @Override
  protected List<InstanceWrapper> mapToEntities(Stream<String> linesStream) {
    return linesStream
      .map(JsonObject::new)
      .map(json -> new InstanceWrapper(mapBulkInstanceRecordToInstance(json), extractPrecedingSucceedingTitles(json)))
      .toList();
  }

  private List<PrecedingSucceedingTitle> extractPrecedingSucceedingTitles(JsonObject instanceJson) {
    List<PrecedingSucceedingTitle> titles = new ArrayList<>();
    instanceJson.getJsonArray(PRECEDING_TITLES_FIELD).stream()
      .map(JsonObject.class::cast)
      .map(title -> title.mapTo(PrecedingSucceedingTitle.class))
      .map(title -> title.withSucceedingInstanceId(instanceJson.getString(ID_FIELD)))
      .forEach(titles::add);

    instanceJson.getJsonArray(SUCCEEDING_TITLES_FIELD).stream()
      .map(JsonObject.class::cast)
      .map(title -> title.mapTo(PrecedingSucceedingTitle.class))
      .map(title -> title.withPrecedingInstanceId(instanceJson.getString(ID_FIELD)))
      .forEach(titles::add);
    return titles;
  }

  @Override
  protected Future<Void> ensureEntitiesWithNonMarcControlledFieldsData(List<InstanceWrapper> instanceWrappers) {
    List<Instance> instances = instanceWrappers.stream().map(InstanceWrapper::instance).toList();

    return instanceRepository.getById(instances, Instance::getId).compose(existingInstances -> {
      instances.forEach(instance -> {
        if (existingInstances.get(instance.getId()) != null) {
          InstanceBulkProcessingUtil.copyNonMarcControlledFields(instance, existingInstances.get(instance.getId()));
        }
      });
      return Future.succeededFuture();
    });
  }

  @Override
  protected Future<Void> upsert(List<InstanceWrapper> instanceWrappers, boolean publishEvents) {
    List<Instance> instances = instanceWrappers.stream().map(InstanceWrapper::instance).toList();

    return instanceService.createInstances(instances, APPLY_UPSERT, APPLY_OPTIMISTIC_LOCKING, publishEvents,
        conn -> updatePrecedingSucceedingTitles(conn, instanceWrappers))
      .compose(response -> {
        if (!isCreateSuccessResponse(response)) {
          String msg = String.format("Failed to update instances, status: '%s', message: '%s'",
            response.getStatus(), Json.encode(response.getEntity()));
          return Future.failedFuture(msg);
        }
        return Future.succeededFuture();
      });
  }

  private Future<Void> updatePrecedingSucceedingTitles(Conn conn, List<InstanceWrapper> instances) {
    List<PrecedingSucceedingTitle> precedingSucceedingTitles = instances.stream()
      .map(InstanceWrapper::precedingSucceedingTitles)
      .flatMap(Collection::stream)
      .toList();

    if (precedingSucceedingTitles.isEmpty()) {
      return Future.succeededFuture();
    }

    CQLWrapper cqlQuery = buildPrecedingSucceedingTitlesCql(instances);
    return conn.delete(PRECEDING_SUCCEEDING_TITLE_TABLE, cqlQuery)
      .compose(v -> conn.saveBatch(PRECEDING_SUCCEEDING_TITLE_TABLE, precedingSucceedingTitles))
      .mapEmpty();
  }

  private CQLWrapper buildPrecedingSucceedingTitlesCql(List<InstanceWrapper> instances) {
    try {
      String idsValue = instances.stream()
        .map(InstanceWrapper::instance)
        .map(Instance::getId)
        .collect(Collectors.joining(OR_OPERATOR));

      String cql = String.format(PRECEDING_SUCCEEDING_TITLES_BY_INSTANCE_IDS_CQL, idsValue);
      return new CQLWrapper(new CQL2PgJSON(PRECEDING_SUCCEEDING_TITLE_TABLE + ".jsonb"), cql);
    } catch (FieldException e) {
      log.warn(PRECEDING_SUCCEEDING_TITLES_CQL_BUILD_ERROR_MSG, e);
      throw new ServerErrorException(e.getMessage(), INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  protected Instance provideEntityRepresentationForWritingErrors(InstanceWrapper instanceWrapper) {
    return instanceWrapper.instance();
  }

  @Override
  protected String extractEntityId(Instance instance) {
    return instance.getId();
  }

  record InstanceWrapper(Instance instance, List<PrecedingSucceedingTitle> precedingSucceedingTitles) {}
}
