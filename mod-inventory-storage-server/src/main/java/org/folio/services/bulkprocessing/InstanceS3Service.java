package org.folio.services.bulkprocessing;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.bulkprocessing.InstanceBulkProcessingUtil.mapBulkInstanceRecordToInstance;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
import org.folio.rest.jaxrs.model.InstancePrecedingSucceedingTitle;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.services.instance.InstanceService;
import org.springframework.stereotype.Service;

/**
 * The service that interacts with S3-compatible storage to perform upsert operations on instances retrieved
 * from external file. The file to be processed is specified through {@link BulkUpsertRequest}.
 */
@Service
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
  private final InstanceRepository instanceRepository;

  public InstanceS3Service(Vertx vertx, InstanceService instanceService, InstanceRepository instanceRepository) {
    super(vertx);
    this.instanceService = instanceService;
    this.instanceRepository = instanceRepository;
  }

  @Override
  protected List<InstanceWrapper> mapToEntities(Stream<String> linesStream) {
    return linesStream
      .map(JsonObject::new)
      .map(json -> new InstanceWrapper(mapBulkInstanceRecordToInstance(json), extractPrecedingSucceedingTitles(json)))
      .toList();
  }

  private List<InstancePrecedingSucceedingTitle> extractPrecedingSucceedingTitles(JsonObject instanceJson) {
    List<InstancePrecedingSucceedingTitle> titles = new ArrayList<>();
    instanceJson.getJsonArray(PRECEDING_TITLES_FIELD).stream()
      .map(JsonObject.class::cast)
      .map(title -> title.mapTo(InstancePrecedingSucceedingTitle.class))
      .map(title -> title.withSucceedingInstanceId(instanceJson.getString(ID_FIELD)))
      .forEach(titles::add);

    instanceJson.getJsonArray(SUCCEEDING_TITLES_FIELD).stream()
      .map(JsonObject.class::cast)
      .map(title -> title.mapTo(InstancePrecedingSucceedingTitle.class))
      .map(title -> title.withPrecedingInstanceId(instanceJson.getString(ID_FIELD)))
      .forEach(titles::add);
    return titles;
  }

  @Override
  protected Future<Void> ensureEntitiesWithNonMarcControlledFieldsData(List<InstanceWrapper> instanceWrappers,
                                                                           Map<String, String> headers) {
    List<Instance> instances = instanceWrappers.stream().map(InstanceWrapper::instance).toList();

    return instanceRepository.getByIds(instances, Instance::getId, tenantId(headers)).compose(existingInstances -> {
      instances.forEach(instance -> {
        if (existingInstances.get(instance.getId()) != null) {
          InstanceBulkProcessingUtil.copyNonMarcControlledFields(instance, existingInstances.get(instance.getId()));
        }
      });
      return Future.succeededFuture();
    });
  }

  @Override
  protected Future<Void> upsert(List<InstanceWrapper> instanceWrappers, boolean publishEvents,
                                 Map<String, String> headers) {
    List<Instance> instances = instanceWrappers.stream().map(InstanceWrapper::instance).toList();

    return instanceService.createInstances(instances, APPLY_UPSERT, APPLY_OPTIMISTIC_LOCKING, publishEvents,
        conn -> updatePrecedingSucceedingTitles(conn, instanceWrappers), headers);
  }

  private Future<Void> updatePrecedingSucceedingTitles(Conn conn, List<InstanceWrapper> instances) {
    List<InstancePrecedingSucceedingTitle> precedingSucceedingTitles = instances.stream()
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

  public record InstanceWrapper(Instance instance, List<InstancePrecedingSucceedingTitle> precedingSucceedingTitles) {}
}
