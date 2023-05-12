package org.folio.services.authority;

import static io.vertx.core.Promise.promise;
import static org.folio.rest.impl.AuthorityRecordsApi.AUTHORITY_TABLE;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
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

    var wholeStart = System.nanoTime();
    var readTimeMs = new AtomicLong();
    var parseTimeMs = new AtomicLong();
    log.info("Starting read/write for file {}", authoritiesFilePath);

    try (var reader = new BufferedReader(new FileReader(authoritiesFilePath))) {
      String line;
      var authorities = new LinkedList<Authority>();
      var batchCounter = 0;
      var recordCounter = 0;

      while ((line = executeTimed(() -> readLine(reader), readTimeMs)) != null) {
        var finalLine = line;
        var authority = executeTimed(() -> parseAuthority(finalLine), parseTimeMs);
        authorities.add(authority);

        if (authorities.size() == batchSize) {
          batchCounter++;
          recordCounter += updateAuthorities(authoritiesFilePath, batchCounter, authorities, updateFutures);
        }
      }
      if (!authorities.isEmpty()) {
        batchCounter++;
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
      return Future.failedFuture(new IllegalStateException("Failed to process records.", ex));
    }
    var startDb = System.nanoTime();

    var promise = Promise.<Void>promise();
    GenericCompositeFuture.join(updateFutures)
      .onComplete(result -> {
        if (result.succeeded()) {
          var endDb = System.nanoTime();
          var durationDbMs = TimeUnit.NANOSECONDS.toMillis(endDb - startDb);
          var durationTotal = TimeUnit.NANOSECONDS.toMillis(endDb - wholeStart);
          log.info("Success for file {}. Read time: {}s, parseTime: {}s, writeTime: {}s. Total time: {}",
            authoritiesFilePath, getSeconds(readTimeMs.get()), getSeconds(parseTimeMs.get()), getSeconds(durationDbMs),
            getSeconds(durationTotal));
          promise.complete();
        }

        log.warn("Db write failed for file {}: ", authoritiesFilePath, result.cause());
        promise.fail(result.cause());
      });
    return promise.future();
  }

  private Integer updateAuthorities(String authoritiesFilePath, Integer currentBatchNumber,
                                                List<Authority> authorities,
                                                LinkedList<Future<RowSet<Row>>> updateFutures) {

    var updateFuture = authorityRepository.update(List.copyOf(authorities))
      .onFailure(throwable -> {
        log.warn("Failed to write batch number {} to db for file {} with error ",
        currentBatchNumber, authoritiesFilePath, throwable);
        throw new IllegalStateException("Unable to write authorities batch to db.");
      });
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

  private String readLine(BufferedReader reader) {
    try {
      return reader.readLine();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read line.", e);
    }
  }

  private Authority parseAuthority(String json) {
    try {
      return objectMapper.readValue(json, Authority.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to parse authority.", e);
    }
  }

  private <T> T executeTimed(Supplier<T> supplier, AtomicLong counter) {
    var start = System.nanoTime();
    var result = supplier.get();
    var end = System.nanoTime();

    var durationMs = TimeUnit.NANOSECONDS.toMillis(end - start);
    counter.getAndAdd(durationMs);

    return result;
  }

  private double getSeconds(long millis) {
    return ((double) millis) / 1_000;
  }
}
