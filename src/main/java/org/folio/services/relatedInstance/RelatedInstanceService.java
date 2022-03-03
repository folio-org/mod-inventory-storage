package org.folio.services.relatedInstance;

import static io.vertx.core.Promise.promise;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import org.folio.rest.jaxrs.resource.InstanceStorageRelatedInstances.PostInstanceStorageRelatedInstancesResponse;
import org.folio.rest.jaxrs.resource.InstanceStorageRelatedInstances.PutInstanceStorageRelatedInstancesByRelatedInstanceIdResponse;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Map;
import javax.ws.rs.core.Response;

import org.folio.persist.InstanceRepository;
import org.folio.persist.RelatedInstanceRepository;
import org.folio.persist.RelatedInstanceTypeRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.RelatedInstance;
import org.folio.rest.persist.PostgresClient;
import org.folio.validator.CommonValidators;


public class RelatedInstanceService {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final InstanceRepository instanceRepository;
  private final RelatedInstanceTypeRepository instanceTypeRepository;
  private final RelatedInstanceRepository relatedInstanceRepository;
  private final PostgresClient postgresClient;
  private final String RELATED_INSTANCE_TABLE = "related_instance";

  public RelatedInstanceService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    postgresClient = postgresClient(vertxContext, okapiHeaders);
    instanceTypeRepository = new RelatedInstanceTypeRepository(vertxContext, okapiHeaders);
    instanceRepository = new InstanceRepository(vertxContext, okapiHeaders);
    relatedInstanceRepository = new RelatedInstanceRepository(vertxContext, okapiHeaders);
  }

  public Future<Response> createRelatedInstance(RelatedInstance entity) {
    String relatedInstanceType = entity.getRelatedInstanceType().toString();
    return instanceRepository.getById(entity.getInstanceId())
      .compose(instanceRecord -> refuseIfNotFound(instanceRecord, instanceNotFoundMessage(entity.getInstanceId().toString())))
      .compose(instance -> instanceRepository.getById(entity.getRelatedInstanceId()))
      .compose(instanceRecord -> refuseIfNotFound(instanceRecord, instanceNotFoundMessage(entity.getInstanceId().toString())))
      .compose(instance -> instanceTypeRepository.getById(entity.getRelatedInstanceType()))
      .compose(instanceTypeRecord -> refuseIfNotFound(instanceTypeRecord, instanceTypeNotFoundMessage(relatedInstanceType)))
      .compose(instanceType -> {
        final Promise<Response> postResponse = promise();

        post(RELATED_INSTANCE_TABLE, entity, okapiHeaders, vertxContext,
        PostInstanceStorageRelatedInstancesResponse.class, postResponse);

        return postResponse.future();
      });
  }

  public Future<Response> updateRelatedInstance(RelatedInstance entity) {
    String relatedInstanceType = entity.getRelatedInstanceType().toString();
    return relatedInstanceRepository.getById(entity.getId())
      .compose(CommonValidators::refuseIfNotFound)
      .compose(relatedInstance -> instanceRepository.getById(entity.getInstanceId())
      .compose(instanceRecord -> refuseIfNotFound(instanceRecord, instanceNotFoundMessage(entity.getInstanceId().toString())))
      .compose(instance -> instanceRepository.getById(entity.getRelatedInstanceId()))
      .compose(instanceRecord -> refuseIfNotFound(instanceRecord, instanceNotFoundMessage(entity.getInstanceId().toString())))
      .compose(instance -> instanceTypeRepository.getById(entity.getRelatedInstanceType()))
      .compose(instanceTypeRecord -> refuseIfNotFound(instanceTypeRecord, instanceTypeNotFoundMessage(relatedInstanceType)))
      .compose(instanceType -> {
        final Promise<Response> postResponse = promise();

        put(RELATED_INSTANCE_TABLE, entity, entity.getId(), okapiHeaders, vertxContext,
        PutInstanceStorageRelatedInstancesByRelatedInstanceIdResponse.class, postResponse);

        return postResponse.future();
    }));
  }

  private String instanceNotFoundMessage(String instanceId) {
    return "Instance with UUID " + instanceId + " not found.";
  }

  private String instanceTypeNotFoundMessage(String instanceId) {
    return "Instance type with id " + instanceId + " not found.";
  }

  private static <T> Future<T> refuseIfNotFound(T record, String errorMessage) {
    return record != null ? succeededFuture(record) : failedFuture(new BadRequestException(errorMessage));
  }
}
