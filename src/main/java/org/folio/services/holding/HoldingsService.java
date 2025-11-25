package org.folio.services.holding;

import static io.vertx.core.Promise.promise;
import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.DeleteHoldingsStorageHoldingsResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PostHoldingsStorageHoldingsResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PutHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.support.CollectionUtil.deepCopy;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.utils.ComparisonUtils.equalsIgnoringMetadata;
import static org.folio.validator.CommonValidators.validateUuidFormat;
import static org.folio.validator.CommonValidators.validateUuidFormatForList;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.InstanceRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.ResponseHandlerUtil;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.ConsortiumServiceImpl;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.services.item.ItemService;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;

public class HoldingsService {
  private static final Logger log = getLogger(HoldingsService.class);
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperTool.getMapper();
  private static final Pattern INSTANCEID_PATTERN = Pattern.compile(
    "^ *instanceId *== *\"?("
      // UUID
      + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
      + ")\"?"
      + " +sortBy"
      // allow any sub-set of the fields and allow the fields in any order
      + "(( +(effectiveLocation\\.name|callNumberPrefix|callNumber|callNumberSuffix))+) *$");
  private static final Pattern SPACE_REGEX = Pattern.compile(" +");

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final ItemService itemService;
  private final HoldingsRepository holdingsRepository;
  private final ItemDomainEventPublisher itemEventService;
  private final HoldingDomainEventPublisher domainEventPublisher;
  private final InstanceRepository instanceRepository;
  private final ConsortiumService consortiumService;
  private final ItemRepository itemRepository;
  private final ItemEffectiveValuesService effectiveValuesService;

  public HoldingsService(Context context, Map<String, String> okapiHeaders) {
    this.vertxContext = context;
    this.okapiHeaders = okapiHeaders;

    itemService = new ItemService(context, okapiHeaders);
    postgresClient = postgresClient(context, okapiHeaders);
    hridManager = new HridManager(postgresClient);
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    itemEventService = new ItemDomainEventPublisher(context, okapiHeaders);
    domainEventPublisher = new HoldingDomainEventPublisher(context, okapiHeaders);
    instanceRepository = new InstanceRepository(context, okapiHeaders);
    consortiumService = new ConsortiumServiceImpl(context.owner().createHttpClient(),
      context.get(ConsortiumDataCache.class.getName()));
    itemRepository = new ItemRepository(context, okapiHeaders);
    effectiveValuesService = new ItemEffectiveValuesService(context, okapiHeaders);
  }

  /**
   * Returns Response if the query is supported by instanceId query, null otherwise.
   *
   * <p>
   */
  public Future<Response> getByInstanceId(int offset, int limit, String query) {
    if (query == null) {
      return Future.succeededFuture();
    }
    var matcher = INSTANCEID_PATTERN.matcher(query);
    if (!matcher.find()) {
      return Future.succeededFuture();
    }
    var instanceId = matcher.group(1);
    var sortBy = SPACE_REGEX.split(matcher.group(2));
    return holdingsRepository.getByInstanceId(instanceId, sortBy, offset, limit)
      .map(row -> {
        var json = "{ \"holdingsRecords\": " + row.getString("holdings") + ",\n"
          + "  \"totalRecords\": " + row.getLong("total_records") + ",\n"
          + "  \"resultInfo\": { \n"
          + "    \"totalRecords\": " + row.getLong("total_records") + ",\n"
          + "    \"totalRecordsEstimated\": false\n"
          + "  }\n"
          + "}";
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
      })
      .onFailure(e -> log.error("getByInstanceId:: {}", e.getMessage(), e));
  }

  /**
   * Deletes all holdings but sends only a single domain event (Kafka) message "all records removed",
   * this is much faster than sending one message for each deleted holding.
   */
  public Future<Response> deleteAllHoldings() {
    return holdingsRepository.deleteAll()
      .onSuccess(notUsed -> domainEventPublisher.publishAllRemoved())
      .map(Response.noContent().build());
  }

  public Future<Response> updateHoldingRecord(String holdingId, HoldingsRecord holdingsRecord) {
    return holdingsRepository.getById(holdingId)
      .compose(existingHoldingsRecord -> {
        if (holdingsRecordFound(existingHoldingsRecord)) {
          return updateHolding(existingHoldingsRecord, holdingsRecord);
        } else {
          return createHolding(holdingsRecord);
        }
      });
  }

  public Future<Response> createHolding(HoldingsRecord entity) {
    entity.setEffectiveLocationId(calculateEffectiveLocation(entity));

    return validateUuidFormat(entity.getStatisticalCodeIds())
      .compose(v -> createShadowInstancesIfNeeded(List.of(entity)))
      .compose(v -> hridManager.populateHrid(entity))
      .compose(NotesValidators::refuseLongNotes)
      .compose(hr -> {
        final Promise<Response> postResponse = promise();

        post(HOLDINGS_RECORD_TABLE, hr, okapiHeaders, vertxContext,
          PostHoldingsStorageHoldingsResponse.class, postResponse);

        return postResponse.future()
          .onSuccess(domainEventPublisher.publishCreated());
      })
      .map(ResponseHandlerUtil::handleHridError);
  }

  private Future<Response> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    newHoldings.setEffectiveLocationId(calculateEffectiveLocation(newHoldings));

    return createShadowInstancesIfNeeded(List.of(newHoldings))
      .compose(v -> {
        try {
          var noChanges = equalsIgnoringMetadata(oldHoldings, newHoldings);
          if (noChanges) {
            return Future.succeededFuture()
              .map(res -> PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204());
          }
        } catch (Exception e) {
          return Future.failedFuture(e);
        }

        if (Integer.valueOf(-1).equals(newHoldings.getVersion())) {
          newHoldings.setVersion(null);  // enforce optimistic locking
        }

        return refuseWhenHridChanged(oldHoldings, newHoldings)
          .compose(notUsed -> NotesValidators.refuseLongNotes(newHoldings))
          .compose(notUsed -> postgresClient
            .withTrans(conn -> holdingsRepository.update(conn, oldHoldings.getId(), newHoldings)
              .compose(updateRes -> itemService.updateItemsOnHoldingChanged(conn, newHoldings)))
            .compose(itemsBeforeUpdate -> itemEventService
              .publishUpdated(oldHoldings, newHoldings, itemsBeforeUpdate))
            .<Response>map(res -> PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())
            .onSuccess(domainEventPublisher.publishUpdated(oldHoldings)));
      });
  }

  public Future<Response> deleteHolding(String hrId) {
    return holdingsRepository.getById(hrId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(hr -> {
        final Promise<Response> deleteResult = promise();

        deleteById(HOLDINGS_RECORD_TABLE, hrId, okapiHeaders, vertxContext,
          DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse.class, deleteResult);

        return deleteResult.future()
          .onSuccess(domainEventPublisher.publishRemoved(hr));
      });
  }

  public Future<Response> deleteHoldings(String cql) {
    if (StringUtils.isBlank(cql)) {
      return Future.succeededFuture(
        DeleteHoldingsStorageHoldingsResponse.respond400WithTextPlain(
          "Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAllHoldings();  // faster: sends only one domain event (Kafka) message
    }
    // do not add curly braces for readability, this is to comply with
    // https://sonarcloud.io/organizations/folio-org/rules?open=java%3AS1602&rule_key=java%3AS1602
    return holdingsRepository.delete(cql)
      .onSuccess(rowSet -> vertxContext.runOnContext(runLater ->
        rowSet.iterator().forEachRemaining(row -> {
            try {
              var holdingId = OBJECT_MAPPER.readTree(row.getString(1)).get("id").textValue();
              domainEventPublisher.publishRemoved(holdingId, row.getString(1));
            } catch (JsonProcessingException ex) {
              log.error("deleteHoldings:: Failed to parse json : {}", ex.getMessage(), ex);
              throw new IllegalArgumentException(ex.getCause());
            }
          }
        )
      ))
      .map(Response.noContent().build());
  }

  public Future<Response> createHoldings(List<HoldingsRecord> holdings, boolean upsert, boolean optimisticLocking) {
    for (var holdingsRecord : holdings) {
      holdingsRecord.setEffectiveLocationId(calculateEffectiveLocation(holdingsRecord));
    }

    return validateUuidFormatForList(holdings, HoldingsRecord::getStatisticalCodeIds)
      .compose(v -> createShadowInstancesIfNeeded(holdings))
      .compose(ar -> hridManager.populateHridForHoldings(holdings)
        .compose(NotesValidators::refuseHoldingLongNotes))
      .compose(validatedHoldings -> {
        if (!upsert) {
          // For create-only operations, use existing logic
          return buildBatchOperationContext(false, validatedHoldings,
            holdingsRepository, HoldingsRecord::getId, true)
            .compose(batchOperation -> postSync(HOLDINGS_RECORD_TABLE, validatedHoldings, MAX_ENTITIES,
              false, optimisticLocking, okapiHeaders, vertxContext, PostHoldingsStorageBatchSynchronousResponse.class)
              .onSuccess(domainEventPublisher.publishCreatedOrUpdated(batchOperation)));
        }

        // For upsert operations, use the new transaction-based approach
        return performUpsertWithItemUpdates(validatedHoldings, optimisticLocking);
      })
      .map(ResponseHandlerUtil::handleHridError);
  }

  private Future<Response> performUpsertWithItemUpdates(List<HoldingsRecord> holdings, boolean optimisticLocking) {
    if (optimisticLocking) {
      holdings.stream().filter(holding -> Objects.equals(-1, holding.getVersion()))
        .forEach(holding -> holding.setVersion(null));
    } else {
      if (!OptimisticLockingUtil.isSuppressingOptimisticLockingAllowed()) {
        return Future.succeededFuture(PostHoldingsStorageBatchSynchronousResponse.respond413WithTextPlain(
          "DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING environment variable doesn't allow to disable optimistic locking"));
      }
      holdings.forEach(holding -> holding.setVersion(-1));
    }
    try {
      MetadataUtil.populateMetadata(holdings, okapiHeaders);
    } catch (ReflectiveOperationException e) {
      log.warn("performUpsertWithItemUpdates:: Error during metadata population", e);
      return Future.failedFuture(e.getMessage());
    }

    return postgresClient.withTrans(conn -> upsertHoldingsAndGetOldContent(conn, holdings)
        .compose(upsertResult -> updateItemsForHoldingsChange(conn, holdings, upsertResult)
          .map(itemsBeforeUpdate -> Pair.of(upsertResult.getLeft(), itemsBeforeUpdate))))
      .onSuccess(oldData ->
        publishHoldingsAndItemEvents(holdings, oldData.getLeft(), oldData.getRight()))
      .map(v -> PostHoldingsStorageBatchSynchronousResponse.respond201());
  }

  private Future<Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>>> upsertHoldingsAndGetOldContent(
    Conn conn, List<HoldingsRecord> holdings) {
    if (holdings.isEmpty()) {
      return Future.succeededFuture(Pair.of(Map.of(), Map.of()));
    }

    var sqlAndParamsResult = buildUpsertSqlWithParams(holdings);
    if (sqlAndParamsResult.getLeft() == null) {
      return Future.failedFuture(sqlAndParamsResult.getRight());
    }

    var sqlAndParams = sqlAndParamsResult.getLeft();
    return conn.execute(sqlAndParams.getLeft(), sqlAndParams.getRight())
      .map(this::processUpsertResultSet);
  }

  private Pair<Pair<String, Tuple>, Exception> buildUpsertSqlWithParams(List<HoldingsRecord> holdings) {
    var sqlBuilder = new StringBuilder();
    var params = new ArrayList<>();
    var paramIndex = 1;

    sqlBuilder.append("WITH upsert_data AS (");
    for (int i = 0; i < holdings.size(); i++) {
      if (i > 0) {
        sqlBuilder.append(" UNION ALL ");
      }
      sqlBuilder.append("SELECT $").append(paramIndex++).append("::uuid as id, $")
        .append(paramIndex++).append("::jsonb as data");

      var holding = holdings.get(i);
      params.add(holding.getId());
      try {
        params.add(PostgresClient.pojo2JsonObject(holding));
      } catch (Exception e) {
        return Pair.of(null, e);
      }
    }

    var holdingsTableName = holdingsRepository.getFullTableName();
    var itemsTableName = itemRepository.getFullTableName();

    sqlBuilder.append("), ")
      .append(buildOldDataQueries(holdingsTableName, itemsTableName))
      .append(buildUpsertQuery(holdingsTableName))
      .append(buildCombinedResultsQuery())
      .append("SELECT id, old_holdings_content, old_item_content FROM combined_results");

    var sqlAndParams = Pair.of(sqlBuilder.toString(), Tuple.from(params));
    return Pair.of(sqlAndParams, null);
  }

  private String buildOldDataQueries(String holdingsTableName, String itemsTableName) {
    return "old_holdings_data AS ("
      + "  SELECT id, jsonb::text as old_content FROM " + holdingsTableName
      + "  WHERE id = ANY(SELECT id FROM upsert_data)"
      + "), "
      + "old_items_data AS ("
      + "  SELECT holdingsrecordid, jsonb::text as item_content FROM " + itemsTableName
      + "  WHERE holdingsrecordid = ANY(SELECT id FROM upsert_data)"
      + "), ";
  }

  private String buildUpsertQuery(String holdingsTableName) {
    return "updated AS ("
      + "  UPDATE " + holdingsTableName + " SET jsonb = upsert_data.data "
      + "  FROM upsert_data WHERE " + holdingsTableName + ".id = upsert_data.id "
      + "  RETURNING " + holdingsTableName + ".id"
      + "), "
      + "inserted AS ("
      + "  INSERT INTO " + holdingsTableName + " (id, jsonb) "
      + "  SELECT id, data FROM upsert_data "
      + "  WHERE id NOT IN (SELECT id FROM updated) "
      + "  RETURNING id"
      + "), "
      + "upserted AS ("
      + "  SELECT id FROM updated UNION ALL SELECT id FROM inserted"
      + "), ";
  }

  private String buildCombinedResultsQuery() {
    return "combined_results AS ("
      + "  SELECT "
      + "    u.id, "
      + "    COALESCE(oh.old_content, 'null') as old_holdings_content, "
      + "    oi.item_content as old_item_content"
      + "  FROM upserted u "
      + "  LEFT JOIN old_holdings_data oh ON u.id = oh.id"
      + "  LEFT JOIN old_items_data oi ON u.id = oi.holdingsrecordid"
      + ")";
  }

  private Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>> processUpsertResultSet(RowSet<Row> rowSet) {
    var oldHoldingsMap = new HashMap<String, HoldingsRecord>();
    var oldItemsMap = new HashMap<String, List<Item>>();

    for (var row : rowSet) {
      var id = row.getUUID(0).toString();
      var oldHoldingsContent = row.getString(1);
      var oldItemContent = row.getString(2);

      processOldHoldingsContent(id, oldHoldingsContent, oldHoldingsMap);
      processOldItemContent(id, oldItemContent, oldItemsMap);
    }

    return Pair.of(oldHoldingsMap, oldItemsMap);
  }

  private void processOldHoldingsContent(String id, String oldHoldingsContent,
                                         Map<String, HoldingsRecord> oldHoldingsMap) {
    if (!"null".equals(oldHoldingsContent) && !oldHoldingsMap.containsKey(id)) {
      try {
        var oldHolding = ObjectMapperTool.readValue(oldHoldingsContent, HoldingsRecord.class);
        oldHoldingsMap.put(id, oldHolding);
      } catch (Exception e) {
        log.warn("Failed to parse old holdings record content for id: {}", id, e);
      }
    }
  }

  private void processOldItemContent(String id, String oldItemContent,
                                     Map<String, List<Item>> oldItemsMap) {
    if (oldItemContent != null) {
      try {
        var oldItem = ObjectMapperTool.readValue(oldItemContent, Item.class);
        oldItemsMap.computeIfAbsent(id, k -> new ArrayList<>()).add(oldItem);
      } catch (Exception e) {
        log.warn("Failed to parse old item record content for holdings {}", id, e);
      }
    }
  }

  private Future<Map<String, List<Item>>> updateItemsForHoldingsChange(
    Conn conn,
    List<HoldingsRecord> newHoldings,
    Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>> upsertResult) {

    var oldHoldingsMap = upsertResult.getLeft();
    var oldItemsMap = upsertResult.getRight();

    var holdingsMap = newHoldings.stream()
      .collect(toMap(HoldingsRecord::getId, h -> h));

    var itemsBeforeUpdate = new HashMap<String, List<Item>>();
    var allItemsToUpdate = new ArrayList<Item>();

    for (var entry : oldItemsMap.entrySet()) {
      var holdingsId = entry.getKey();
      var items = entry.getValue();
      var newHolding = holdingsMap.get(holdingsId);
      var oldHolding = oldHoldingsMap.get(holdingsId);

      if (newHolding != null) {
        // Deep copy items for event publishing

        // Only update items if holdings actually changed
        try {
          var holdingsChanged = oldHolding == null || !equalsIgnoringMetadata(oldHolding, newHolding);
          if (holdingsChanged) {
            var itemsCopy = (List<Item>) deepCopy(items, Item.class);
            itemsBeforeUpdate.put(holdingsId, itemsCopy);
            for (var item : items) {
              itemService.populateItemFromHoldings(item, newHolding, effectiveValuesService);
              allItemsToUpdate.add(item);
            }
          }
        } catch (JsonProcessingException ex) {
          return Future.failedFuture(ex);
        }
      }
    }

    if (allItemsToUpdate.isEmpty()) {
      return Future.succeededFuture(itemsBeforeUpdate);
    }

    return itemRepository.updateBatch(allItemsToUpdate, conn)
      .map(notUsed -> itemsBeforeUpdate);
  }

  private Future<Void> publishHoldingsAndItemEvents(List<HoldingsRecord> newHoldings,
    Map<String, HoldingsRecord> oldHoldings, Map<String, List<Item>> itemsBeforeUpdate) {

    var itemEventFutures = publishItemEvents(newHoldings, oldHoldings, itemsBeforeUpdate);
    return Future.all(itemEventFutures)
      .onSuccess(v -> publishHoldingsEvents(newHoldings, oldHoldings))
      .mapEmpty();
  }

  private List<Future<Void>> publishItemEvents(List<HoldingsRecord> newHoldings,
    Map<String, HoldingsRecord> oldHoldings, Map<String, List<Item>> itemsBeforeUpdate) {

    var itemFutures = new ArrayList<Future<Void>>();

    for (var newHolding : newHoldings) {
      var holdingsId = newHolding.getId();
      var oldHolding = oldHoldings.get(holdingsId);
      var itemsBefore = itemsBeforeUpdate.get(holdingsId);

      if (oldHolding != null && itemsBefore != null) {
        itemFutures.add(itemEventService.publishUpdated(oldHolding, newHolding, itemsBefore));
      }
    }
    return itemFutures;
  }

  private void publishHoldingsEvents(List<HoldingsRecord> newHoldings, Map<String, HoldingsRecord> oldHoldingsMap) {
    var oldHoldings = new ArrayList<>(oldHoldingsMap.values());
    var createdHoldings = newHoldings.stream()
      .filter(entity -> !oldHoldingsMap.containsKey(entity.getId()))
      .toList();

    var batchContext = new BatchOperationContext<>(createdHoldings, oldHoldings, true);
    domainEventPublisher.publishCreatedOrUpdated(batchContext)
      .handle(Response.status(201).build());
  }

  private String calculateEffectiveLocation(HoldingsRecord holdingsRecord) {
    String permanentLocationId = holdingsRecord.getPermanentLocationId();
    String temporaryLocationId = holdingsRecord.getTemporaryLocationId();

    if (temporaryLocationId != null) {
      return temporaryLocationId;
    } else {
      return permanentLocationId;
    }
  }

  private Future<Void> createShadowInstancesIfNeeded(List<HoldingsRecord> holdingsRecords) {
    return consortiumService.getConsortiumData(okapiHeaders)
      .compose(consortiumDataOptional -> {
        if (consortiumDataOptional.isPresent()) {
          return createShadowInstancesIfNeeded(holdingsRecords, consortiumDataOptional.get());
        }
        return Future.succeededFuture();
      });
  }

  private Future<Void> createShadowInstancesIfNeeded(List<HoldingsRecord> holdingsRecords,
                                                     ConsortiumData consortiumData) {
    Map<String, Future<SharingInstance>> instanceFuturesMap = new HashMap<>();

    for (HoldingsRecord holdingRecord : holdingsRecords) {
      String instanceId = holdingRecord.getInstanceId();
      instanceFuturesMap.computeIfAbsent(instanceId, v -> createShadowInstanceIfNeeded(instanceId, consortiumData));
    }
    return Future.all(new ArrayList<>(instanceFuturesMap.values()))
      .mapEmpty();
  }

  private Future<SharingInstance> createShadowInstanceIfNeeded(String instanceId, ConsortiumData consortiumData) {
    return instanceRepository.exists(instanceId)
      .compose(exists -> Boolean.TRUE.equals(exists) ? Future.succeededFuture() :
        consortiumService.createShadowInstance(instanceId, consortiumData, okapiHeaders)
      );
  }

  private boolean holdingsRecordFound(HoldingsRecord holdingsRecord) {
    return holdingsRecord != null;
  }

  public Future<Void> publishReindexHoldingsRecords(String rangeId, String fromId, String toId) {
    return holdingsRepository.getReindexHoldingsRecords(fromId, toId)
      .compose(holdings -> domainEventPublisher.publishReindexHoldings(rangeId, holdings));
  }
}
