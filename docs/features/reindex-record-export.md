---
feature_id: reindex-record-export
title: Reindex Record Export
updated: 2026-02-27
---

# Reindex Record Export

## What it does
This feature adds an API to export a requested range of inventory records as NDJSON files to S3. After a successful export, the module publishes a Kafka event indicating that the file is ready for downstream processing. The export supports instance, item, and holdings record types.

## Why it exists
Reindex workflows need a file-based handoff for record ranges so downstream processors can consume batches without requesting each record directly. This flow provides a durable S3 artifact and an event signal that coordinates asynchronous reindex processing.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | /inventory-reindex-records/export | Exports records in the requested ID range and triggers file-ready notification on success |

## Business rules and constraints
- Request body must include `id`, `recordType`, and `recordIdsRange` with `from` and `to`; `traceId` is optional.
- Supported `recordType` values are `instance`, `item`, and `holdings`.
- Record selection uses inclusive range boundaries (`id >= from` and `id <= to`).
- For instance exports in non-central consortium tenants, records with `source` matching `CONSORTIUM-%` are excluded.
- Exported object key format is `{tenantId}/{recordType}/{traceId}/{rangeId}.ndjson`; when `traceId` is missing, it is generated.
- Export completion triggers a `reindex.file-ready` Kafka publish with tenant, record type, range, range id, trace id, bucket, and object key.

## Error behavior (if applicable)
- `400` when the request body is malformed.
- `401` when the caller is not authorized.
- `500` for internal server errors (including misconfiguration).

## Configuration (if applicable)
| Variable | Purpose |
|----------|---------|
| S3_REINDEX_URL | S3 endpoint used for reindex export uploads |
| S3_REINDEX_REGION | S3 region used by the reindex export client |
| S3_REINDEX_BUCKET | Bucket where exported NDJSON files are written |
| S3_REINDEX_ACCESS_KEY_ID | Optional access key for reindex S3 client authentication |
| S3_REINDEX_SECRET_ACCESS_KEY | Optional secret key for reindex S3 client authentication |
| S3_REINDEX_IS_AWS | Enables AWS SDK mode for the reindex S3 client |
| KAFKA_REINDEX_FILE_READY_TOPIC_NUM_PARTITIONS | Partition count for the `reindex.file-ready` topic |

## Dependencies and interactions (if applicable)
This feature writes NDJSON export files to S3 and then publishes a Kafka message to the tenant-qualified `reindex.file-ready` topic for downstream consumers.
