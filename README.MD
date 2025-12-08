# mod-inventory-storage

Copyright (C) 2016-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

<!-- TOC -->
* [mod-inventory-storage](#mod-inventory-storage)
* [Goal](#goal)
* [Prerequisites](#prerequisites)
* [Preparation](#preparation)
  * [Git Submodules](#git-submodules)
  * [Postgres](#postgres)
  * [Kafka](#kafka)
* [Building](#building)
* [Environment Variables](#environment-variables)
* [Local Deployment using Docker](#local-deployment-using-docker)
  * [Preparation](#preparation-1)
  * [Start the infrastructure](#start-the-infrastructure)
  * [Start the Module](#start-the-module)
  * [Stop the Module](#stop-the-module)
  * [Stop the infrastructure](#stop-the-infrastructure)
* [Local Deployment using Homebrew on macOS](#local-deployment-using-homebrew-on-macos)
* [Running](#running)
  * [Preparation](#preparation-2)
  * [Running Okapi](#running-okapi)
  * [Registration](#registration)
  * [Tenant Initialization](#tenant-initialization)
  * [Sample Data](#sample-data)
* [Making Requests](#making-requests)
  * [Okapi Root Address](#okapi-root-address)
* [Operating System Support](#operating-system-support)
* [Additional Information](#additional-information)
    * [Issue tracker](#issue-tracker)
    * [ModuleDescriptor](#moduledescriptor)
    * [API documentation](#api-documentation)
    * [Code analysis](#code-analysis)
    * [Download and configuration](#download-and-configuration)
* [Appendix 1 - Docker Information](#appendix-1---docker-information)
  * [When Using the Modules as Docker Containers](#when-using-the-modules-as-docker-containers)
    * [Finding a Routable Address](#finding-a-routable-address)
* [HRID Management](#hrid-management)
* [Inventory view endpoint](#inventory-view-endpoint)
* [Domain event pattern](#domain-event-pattern)
  * [Domain events for items](#domain-events-for-items)
  * [Domain events for delete all APIs](#domain-events-for-delete-all-apis)
  * [Reindex of instances](#reindex-of-instances)
  * [Iteration of instances](#iteration-of-instances)
<!-- TOC -->

# Goal

FOLIO compatible inventory storage module.

Provides PostgreSQL based storage to complement the [inventory module](http://github.com/folio-org/mod-inventory). Written in Java, using the raml-module-builder and uses Maven as its build system.

# Prerequisites

- Java 17 JDK
- Maven 3.3.9
- Docker ([minimum requirements](https://www.testcontainers.org/supported_docker_environment/))
- Postgres 12 (running and listening on localhost:5432, logged in user must have admin rights)
- Kafka 2.6 (running and listening on localhost:9092)
- Node 6.4.0 (for API linting and documentation generation)
- NPM 3.10.3 (for API linting and documentation generation)
- Python 3.6.0 (for un-registering module during managed deployment scripts)

# Preparation

## Git Submodules

There are some common RAML definitions that are shared between FOLIO projects via Git submodules.

To initialise these please run `git submodule init && git submodule update` in the root directory.

If these are not initialised, the module will fail to build correctly, and other operations may also fail.

More information is available on the [developer site](https://dev.folio.org/guides/developer-setup/#update-git-submodules).

## Postgres

Run the `setup-test-db.sh` script in the root directory to setup Postgres with a database to be used in tests.
This is only required to run tests against an external Postgres instance, the default is to use an embedded Postgres instance.

## Kafka

Mod-inventory-storage implements domain event pattern and requires kafka to be listening to
`localhost:9092`. You can override kafka port and host by setting `KAFKA_PORT` and
`KAFKA_HOST` environment variables.

For production deployments it is also required to set `REPLICATION_FACTOR`
env variable, this property has the following description:
> The replication factor controls how many servers will replicate each message that is written.
> If replication factor set to 3 then up to 2 servers can fail before access to the data will be lost.

The default configuration for this property is `1` for production environments
it is usually `3`.

There is another important property - `number of partitions` for a topic - it has the following
description:
> The partition count controls how many logs the topic will be sharded into.

These properties can be changed by setting env variable.

* `KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS` Default value - `50`
* `KAFKA_CLASSIFICATION_TYPE_TOPIC_NUM_PARTITIONS` Default value - `1`
* `KAFKA_CALL_NUMBER_TYPE_TOPIC_NUM_PARTITIONS` Default value - `1`
* `KAFKA_LOCATION_TOPIC_NUM_PARTITIONS` Default value - `1`
* `KAFKA_LIBRARY_TOPIC_NUM_PARTITIONS` Default value - `1`
* `KAFKA_CAMPUS_TOPIC_NUM_PARTITIONS` Default value - `1`
* `KAFKA_INSTITUTION_TOPIC_NUM_PARTITIONS` Default value - `1`
* `KAFKA_SUBJECT_TYPE_TOPIC_NUM_PARTITIONS` Default value - `1`
* `KAFKA_REINDEX_RECORDS_TOPIC_NUM_PARTITIONS` Default value - `16`
* `KAFKA_SUBJECT_SOURCE_TOPIC_NUM_PARTITIONS` Default value - `1`

There is also possibility for customizing through properties the Kafka Topic
`message retention` (in milliseconds) and `maximum message size` (in bytes). The default values of these configurations
for any topic are 604800000 milliseconds (or 1 week) and 1048576 bytes (or 1 MB) respectively

These are the defined topic properties for `message retention` and `maximum message size` for `reindex-records` topic

* `KAFKA_REINDEX_RECORDS_TOPIC_MESSAGE_RETENTION` Default value - `86400000` (1 day)
* `KAFKA_REINDEX_RECORDS_TOPIC_MAX_MESSAGE_SIZE` Default value - `10485760` (10 MB)

in case, any of these topic properties are changed for `reindex-records` the topic needs to be recreated and module needs to be reinstalled.

Changing maximum message size for kafka producer:
* `KAFKA_REINDEX_PRODUCER_MAX_REQUEST_SIZE_BYTES` Default value - `10485760` (10 MB)

# Building

run `mvn install` from the root directory.

To run the tests against both embedded and external databases, run `./build.sh` from the root directory.

# Environment Variables

These environment variables configure Kafka, for details see [Kafka](#kafka):
* `KAFKA_PORT`
* `KAFKA_HOST`
* `REPLICATION_FACTOR`
* `KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS`
* `KAFKA_CLASSIFICATION_TYPE_TOPIC_NUM_PARTITIONS`
* `KAFKA_CALL_NUMBER_TYPE_TOPIC_NUM_PARTITIONS`
* `KAFKA_SUBJECT_TYPE_TOPIC_NUM_PARTITIONS`
* `KAFKA_REINDEX_RECORDS_TOPIC_NUM_PARTITIONS`
* `KAFKA_SUBJECT_SOURCE_TOPIC_NUM_PARTITIONS`


These environment variables configure Kafka topic for specific business-related topics
* `KAFKA_CLASSIFICATION_TYPE_TOPIC_NUM_PARTITIONS`
* `KAFKA_CALL_NUMBER_TYPE_TOPIC_NUM_PARTITIONS`
* `KAFKA_LOCATION_TOPIC_NUM_PARTITIONS`
* `KAFKA_LIBRARY_TOPIC_NUM_PARTITIONS`
* `KAFKA_CAMPUS_TOPIC_NUM_PARTITIONS`
* `KAFKA_INSTITUTION_TOPIC_NUM_PARTITIONS`
* `KAFKA_SUBJECT_TYPE_TOPIC_NUM_PARTITIONS`
* `KAFKA_REINDEX_RECORDS_TOPIC_NUM_PARTITIONS`
* `KAFKA_SUBJECT_SOURCE_TOPIC_NUM_PARTITIONS`
* `KAFKA_REINDEX_RECORDS_TOPIC_MESSAGE_RETENTION`
* `KAFKA_REINDEX_RECORDS_TOPIC_MAX_MESSAGE_SIZE`

mod-inventory-storage also supports all Raml Module Builder (RMB) environment variables,
for details see [RMB](https://github.com/folio-org/raml-module-builder#environment-variables):
* `DB_HOST`
* `DB_PORT`
* `DB_USERNAME`
* `DB_PASSWORD`
* `DB_DATABASE`
* `DB_HOST_READER`
* `DB_PORT_READER`
* `DB_SERVER_PEM`
* `DB_QUERYTIMEOUT`
* `DB_CHARSET`
* `DB_MAXPOOLSIZE`
* `DB_MAXSHAREDPOOLSIZE`
* `DB_CONNECTIONRELEASEDELAY`
* `DB_RECONNECTATTEMPTS`
* `DB_RECONNECTINTERVAL`
* `DB_EXPLAIN_QUERY_THRESHOLD`
* `DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING`

These environment variables configure the module interaction with S3-compatible storage (AWS S3, Minio Server):
* `S3_URL` (default value - `https://s3.amazonaws.com`)
* `S3_REGION` (default value - `us-west-2`)
* `S3_BUCKET`
* `S3_ACCESS_KEY_ID` (default value is an empty string - "")
* `S3_SECRET_ACCESS_KEY` (default value is an empty string - "")
* `S3_IS_AWS` (default value - `false`)
* `S3_LOCAL_SUB_PATH` (default value - `mod-inventory-storage`)
* `ECS_TLR_FEATURE_ENABLED` (default value - `false`)

# Local Deployment using Docker

## Preparation

Execute `mvn clean package` to build the jar artefact needed for building a Docker image

## Start the infrastructure

Clone `https://github.com/folio-org/folio-tools/`

Navigate to the folder `infrastructure/local` directory within the cloned repository

Execute `docker compose up -d` to start the infrastructure containers needed to run the module

## Start the Module

In the root of this repository, execute `docker compose up -d` to deploy the module

## Stop the Module

In the root of this repository, execute `docker compose down` to undeploy the module

## Stop the infrastructure

Navigate to the folder `infrastructure/local` directory within the clone of the folio-tools repository

Execute `docker compose down` to stop the infrastructure containers

# Local Deployment using Homebrew on macOS

The GitHub Actions file [.github/workflows/mac.yml](.github/workflows/mac.yml) for macOS uses Homebrew
to setup the infrastructure, run the module, and install and check sample data.

# Running

## Preparation

## Running Okapi

Make sure that [Okapi](https://github.com/folio-org/okapi) is running on its default port of 9130 (see the [guide](https://github.com/folio-org/okapi/blob/master/doc/guide.md) for instructions).

A script for building and running Okapi is provided. Run `../mod-inventory-storage/start-okapi.sh` from the root of the Okapi source.

As this runs Okapi using Postgres storage, some database preparation is required. This can be achieved by running `./create-okapi-database.sh` from the root of this repository.

## Registration

To register the module with deployment instructions and activate it for a demo tenant, run `./start-managed-demo.sh` from the root directory.

To deactivate and unregister the module, run `./stop-managed-demo.sh` from the root directory.

## Tenant Initialization

The module supports v2.0 of the Okapi `_tenant` interface. This version of the interface allows Okapi to pass tenant initialization parameters using the `tenantParameters` key. Currently, the only parameters supported are the `loadReference` and `loadSample` keys, which will cause the module to load reference data and sample data respectively for the tenant if set to `true`.  Here is an example of passing the `loadReference` parameter to the module via Okapi's `/_/proxy/tenants/<tenantId>/install` endpoint:

    curl -w '\n' -X POST -d '[ { "id": "mod-inventory-storage-14.1.0", "action": "enable" } ]' http://localhost:9130/_/proxy/tenants/my-test-tenant/install?tenantParameters=loadReference%3Dtrue

This results in a post to the module's `_tenant` API with the following structure:

```json
{
  "module_to": "mod-inventory-storage-14.1.0",
  "parameters": [
    {
      "key": "loadReference",
      "value": "true"
    }
  ]
}
```

See the section [Install modules per tenant|https://github.com/folio-org/okapi/blob/master/doc/guide.md#install-modules-per-tenant] in the Okapi guide for more information.

## Sample Data

To load some sample data, the `loadSample` tenant initialization parameter can be passed when installing the module:

    curl -w '\n' -X POST -d '[ { "id": "mod-inventory-storage-14.1.0", "action": "enable" } ]' http://localhost:9130/_/proxy/tenants/my-test-tenant/install?tenantParameters=loadReference%3Dtrue%2CloadSample%3Dtrue

Please note that loading sample data will not work without also loading reference data.

The sample data that would be loaded can be found in the `sample-data/` folder in the root directory.

# Making Requests

These modules provide HTTP based APIs rather than any UI themselves.

As FOLIO is a multi-tenant system, many of the requests made to these modules are tenant aware (via the X-Okapi-Tenant header), which means most requests need to be made via a system which understands these headers (e.g. another module or UI built using [Stripes](https://github.com/folio-org/stripes-core)).

Therefore, it is suggested that requests to the API are made via tools such as curl or [postman](https://www.getpostman.com/), or via a browser plugin for adding headers, such as [Requestly](https://chrome.google.com/webstore/detail/requestly/mdnleldcmiljblolnjhpnblkcekpdkpa).

## Okapi Root Address

It is recommended that the modules are located via Okapi. Access via Okapi requires passing the X-Okapi-Tenant header (see the Okapi guide above for details).

http://localhost:9130/instance-storage
http://localhost:9130/item-storage

# Operating System Support

Most of the development for these modules, thus far, has been performed on OS X, with some on Ubuntu. Feedback for these, and particularly other operating systems is very welcome.

# Additional Information

Other [modules](https://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [MODINVSTOR](https://issues.folio.org/browse/MODINVSTOR)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-inventory-storage).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-inventory-storage).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-inventory-storage/).

# Appendix 1 - Docker Information

## When Using the Modules as Docker Containers

For the modules to communicate via Okapi Proxy, when running in Docker containers, the address for Okapi Proxy needs to be routable from inside the container.

This can be achieved by passing a parameter to the script used to start Okapi, as follows `../mod-metadata/start-okapi.sh http://192.168.X.X:9130`

Where 192.168.X.X is a routable IP address for the host from container instances and both repository clones are at the same directory level on your machine.

### Finding a Routable Address

Finding the appropriate IP address can be OS and Docker implementation dependent, so this is a very early guide rather than thorough treatment of the topic.

If these methods don't work for you, please do get in touch, so this section can be improved.

On Linux, `ifconfig docker0 | grep 'inet addr:'` should give output similar to `inet addr:192.168.X.X  Bcast:0.0.0.0  Mask:255.255.0.0`, , the first IP address is usually routable from within containers.

On Mac OS X (using Docker Native), `ifconfig en0 | grep 'inet '` should give output similar to `inet 192.168.X.X netmask 0xffffff00 broadcast 192.168.X.X`, the first IP address is usually routable from within containers.

# HRID Management

When instances, holdings records and items are added to inventory, they will be assigned a human
readable identifier (HRID) if one is not provided. The HRID is created using settings stored in and
managed by this module via the `/hrid-settings-storage/hrid-settings` API.

The default settings, on enabling the module, are:
|Type     |Prefix|Start Number|First HRID String|Max HRID String|
|---------|------|------------|-----------------|---------------|
|Instances|in    |1           |in00000001       |in99999999     |
|Holdings |ho    |1           |ho00000001       |ho99999999     |
|Items    |it    |1           |it00000001       |it99999999     |

The prefix is optional for each inventory type and is restricted to 10 alphanumeric characters as
well as `.` and `-`. The start number is required. A generated HRID will consist of the prefix,
if supplied, prepended to `0` padded 8 digit string starting from the start number. Every HRID
generated will increment the current number of that inventory type by 1. HRID strings are case
insensitive and must be unique or not present when adding a new inventory type.

Changing the start number to a number lower than the current number is not supported and will
likely lead to generation of duplicate HRIDs. If an inventory type is added that contains a
duplicate HRID, the module will reject the submission.

# Inventory view endpoint

Running a query against the `/inventory-view/instances` API writes this log message:

```
WARN  CQL2PgJSON           loadDbSchema loadDbSchema(): No configuration for table instance_holdings_item_view found, using defaults
```

This is a [known issue caused by RMB](https://issues.folio.org/browse/RMB-909) and can be ignored.

# Domain event pattern

The pattern means that every time when an instance/item is created/updated/removed
a message is posted to kafka topic:
* `inventory.instance` - for instances;
* `inventory.item` - for items;
* `inventory.holdings-record` - for holdings records.

The event payload has following structure:
```javascript
{
  "old": {...}, // the instance/item before update or delete
  "new": {...}, // the instance/item after update or create
  "type": "UPDATE|DELETE|CREATE|DELETE_ALL", // type of the event
  "tenant": "diku" // tenant name
}
```

`X-Okapi-Url` and `X-Okapi-Tenant` headers are set from the request to the kafka message.

Kafka partition key for all the events is instance id (for items it is retrieved from
associated holding record).

## Domain events for items

The `new` and `old` records also includes `instanceId` property,
on the same level with other item properties, which defined in the schema:
```javascript
{
  "instanceId": "<the instance id>",
  // all other properties that defined in the schema
}
```

## Domain events for delete all APIs

There are delete all APIs for items instances and holding records. For such
APIs we're issuing a special domain event:
* Partition key: `00000000-0000-0000-0000-000000000000`
* Event payload:
```javascript
{
  "type": "DELETE_ALL",
  "tenant": "<the tenant name>"
}
```

## Reindex of instances

Some consumers need to pull all instances from an existing database. There is
`instance-reindex` API for this. When a reindex job is submitted we initiate
streaming of all instance IDs and publishing domain events for them. The domain
event has following structure:
* Topic: `inventory.instance`
* Partition key: `The instance id`
* Payload:
```javascript
x-okapi-tenant: <tenant-id>
x-okapi-url: <okapi-url>

{
  "type": "REINDEX",
  "tenant": "<the-tenant-name>"
}
```

## Iteration of instances

There are business cases when the whole instance collection should be traversed to obtain existing instances
(and potentially related items/holdings) and processing them in asynchronous manner. Examples of such cases are:
* creating search indices;
* contributing instance catalog to external system.

To support the above there is _"Instance Iteration API"_ available via `/instance-storage/instances/iteration` URL.
It is similar to `instance-reindex` API but avoids some limitations of this API by:
* allowing to specify a target Kafka topic for produced events;
* allowing simultaneous execution of several jobs with different event types to serve the needs of
different business processes;
* making the interface client agnostic (naming says nothing about business intention of the caller, like re-indexing).

It's expected that Instance Iteration API will substitute Instance Reindex API in the future.

Iteration API provides the following methods:
* to start a new iteration job: `POST /instance-storage/instances/iteration`
* to get status of a running job by its id: `GET /instance-storage/instances/iteration/{jobId}`
* to cancel a job: `DELETE /instance-storage/instances/iteration/{jobId}`

When an iteration job is being submitted the client can specify target topic and event type (optional):
```javascript
{
  "eventType": "<event-type>",
  "topicName": "<target-topic-name>"
}
```
If event type is missing in the request it will be defaulted to `ITERATE`.

Once iteration job has been started, it initiates streaming of all instance IDs and publishing domain events for them.
The domain event has the following structure:
* Topic: <target-topic-name>
* Partition key: `The instance id`
* Payload:
```javascript
x-okapi-tenant: <tenant-id>
x-okapi-url: <okapi-url>
iteration-job-id: <job-id>
{
  "type": "<event-type>",
  "tenant": "<the-tenant-name>"
}
```
