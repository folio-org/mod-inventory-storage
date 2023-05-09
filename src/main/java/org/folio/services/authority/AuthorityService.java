package org.folio.services.authority;

import static io.vertx.core.Promise.promise;
import static org.folio.rest.impl.AuthorityRecordsApi.AUTHORITY_TABLE;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.persist.AuthorityRepository;
import org.folio.rest.jaxrs.model.Authority;
import org.folio.rest.jaxrs.resource.AuthorityStorage;
import org.folio.services.domainevent.AuthorityDomainEventPublisher;
import org.folio.validator.CommonValidators;

public class AuthorityService {

  private static final Logger log = LogManager.getLogger();

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final AuthorityRepository authorityRepository;
  private final AuthorityDomainEventPublisher domainEventService;
  private final ObjectMapper objectMapper;

  public AuthorityService(Context vertxContext,
                          Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    domainEventService = new AuthorityDomainEventPublisher(vertxContext, okapiHeaders);
    authorityRepository = new AuthorityRepository(vertxContext, okapiHeaders);
    objectMapper = ObjectMapperTool.getMapper();
  }

  public Future<Response> createAuthority(Authority entity) {
    final Promise<Response> postResponse = promise();
    post(AUTHORITY_TABLE, entity, okapiHeaders, vertxContext,
      AuthorityStorage.PostAuthorityStorageAuthoritiesResponse.class, postResponse);
    return postResponse.future().onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> updateAuthority(String authorityId,
                                          Authority newAuthority) {
    return authorityRepository.getById(authorityId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(oldRecord -> {
        final Promise<Response> putResult = promise();

        put(AUTHORITY_TABLE, newAuthority, authorityId, okapiHeaders, vertxContext,
          AuthorityStorage.PutAuthorityStorageAuthoritiesByAuthorityIdResponse.class, putResult);

        return putResult.future()
          .onSuccess(domainEventService.publishUpdated(oldRecord));
      });
  }

  public Future<Void> updateAuthorities(String authoritiesFilePath, Integer batchSize) {
    var updateFutures = new LinkedList<Future<RowSet<Row>>>();

    log.info("Starting read/write for file {}", authoritiesFilePath);
    try (var reader = new BufferedReader(new FileReader(authoritiesFilePath))) {
      String line;
      var authorities = new LinkedList<Authority>();
      var batchCounter = 1;
      var recordCounter = 0;

      while ((line = reader.readLine()) != null) {
        authorities.add(objectMapper.readValue(line, Authority.class));

        if (authorities.size() == batchSize) {
          recordCounter += authorities.size();
          var currentBatchNumber = batchCounter;
          log.info("Writing batch number {} with batch size {} for file {}",
            batchCounter++, batchSize, authoritiesFilePath);

          recordCounter += updateAuthorities(authoritiesFilePath, currentBatchNumber, authorities, updateFutures);
        }
      }
      if (!authorities.isEmpty()) {
        recordCounter += updateAuthorities(authoritiesFilePath, batchCounter, authorities, updateFutures);
      }

      if (recordCounter == 0) {
        log.warn("File {} is empty", authoritiesFilePath);
        return Future.failedFuture(new IllegalArgumentException("File must not be empty."));
      }

      log.info("Submitted data write db tasks. Total record count: {}, batch count {} with batch size {}",
        recordCounter, batchCounter - 1, batchSize);
    } catch (Exception ex) {
      log.warn("Exception occurred during processing records for file {}: ", authoritiesFilePath, ex);
    }

    return GenericCompositeFuture.join(updateFutures)
      .onSuccess(compositeFuture -> log.info("Successfully finished writing data to db for file {}",
        authoritiesFilePath))
      .onFailure(throwable -> log.warn("Db write failed for file {}: ", authoritiesFilePath, throwable))
      .compose(compositeFuture -> Future.succeededFuture()); //todo: proper exception handling/passing
  }

  private Integer updateAuthorities(String authoritiesFilePath, Integer currentBatchNumber,
                                                List<Authority> authorities,
                                                LinkedList<Future<RowSet<Row>>> updateFutures) {

    var updateFuture = authorityRepository.update(List.copyOf(authorities))
      .onFailure(throwable -> log.warn("Failed to write batch number {} to db for file {} with error ",
        currentBatchNumber, authoritiesFilePath, throwable));
    updateFutures.add(updateFuture);
    var updated = authorities.size();
    authorities.clear();

    return updated;
  }

  public Future<Response> deleteAuthority(String authorityId) {
    return authorityRepository.getById(authorityId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(authority -> {
        final Promise<Response> deleteResult = promise();

        deleteById(AUTHORITY_TABLE, authorityId, okapiHeaders, vertxContext,
          AuthorityStorage.DeleteAuthorityStorageAuthoritiesByAuthorityIdResponse.class, deleteResult);

        return deleteResult.future()
          .onSuccess(domainEventService.publishRemoved(authority));
      });
  }

  public Future<Void> deleteAllAuthorities() {
    return authorityRepository.deleteAll()
      .compose(notUsed -> domainEventService.publishAllRemoved());
  }
}
