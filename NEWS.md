## v27.2.0 In progress
### Breaking changes
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### New APIs versions
* Provides `API_NAME vX.Y`
* Requires `API_NAME vX.Y`

### Features
* Implement domain event production for location create/update/delete ([MODINVSTOR-1181](https://issues.folio.org/browse/MODINVSTOR-1181))
* Add a new boolean field ecsRequestRouting to the service point schema ([MODINVSTOR-1179](https://issues.folio.org/browse/MODINVSTOR-1179))

### Bug fixes
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### Tech Dept
* Description ([ISSUE_NUMBER](https://issues.folio.org/browse/ISSUE_NUMBER))

### Dependencies
* Bump `LIB_NAME` from `OLD_VERSION` to `NEW_VERSION`
* Add `LIB_NAME` `2.7.4`
* Remove `LIB_NAME`

## v27.1.0 2024-03-19
### New APIs versions
* Provides `item-storage v10.1`
* Provides `inventory-hierarchy 0.5`

### Features
* Provide inventory hierarchy response with new displaySummary field ([MODINVSTOR-1170](https://folio-org.atlassian.net/browse/MODINVSTOR-1170))
* Add location properties and material type id to inventory-hierarchy items-and-holdings response ([MODINVSTOR-1133](https://issues.folio.org/browse/MODINVSTOR-1133))
* Add new field "Display summary" for the item schema ([MODINVSTOR-1154](https://issues.folio.org/browse/MODINVSTOR-1154))
* Add new Identifier type Cancelled LCCN ([MODINVSTOR-1156](https://folio-org.atlassian.net/browse/MODINVSTOR-1156))
* Add source field to contributor name type ([MODINVSTOR-1143](https://folio-org.atlassian.net/browse/MODINVSTOR-1143))
* Classification-types: Publish domain events on changes ([MODINVSTOR-1171](https://folio-org.atlassian.net/browse/MODINVSTOR-1171))

### Bug fixes
* Fix reference and sample data ([MODINVSTOR-1116](https://issues.folio.org/browse/MODINVSTOR-1116), [MODINVSTOR-1115](https://issues.folio.org/browse/MODINVSTOR-1115))

### Tech Dept
* Prevent virtual fields populating for holdings records ([MODINVSTOR-1094](https://issues.folio.org/browse/MODINVSTOR-1094))
* Create base for reference data APIs integration tests ([MODINVSTOR-1164](https://issues.folio.org/browse/MODINVSTOR-1164))
* Make response message more informative for hrid exceptions ([MODINVSTOR-1100](https://issues.folio.org/browse/MODINVSTOR-1100))


### Dependencies
* Bump `vertx` from `4.3.5` to `4.5.5`
* Bump `log4j` from `2.17.2` to `2.23.1`
* Bump `domain-models-runtime` from `35.0.4` to `35.2.0`
* Bump `folio-kafka-wrapper` from `3.0.0` to `3.1.0`
* Bump `caffeine` from `3.1.3` to `3.1.5`
* Bump `lombok` from `1.18.24` to `1.18.30`
* Bump `marc4j` from `2.9.4` to `2.9.5`
* Bump `commons-lang3` from `3.12.0` to `3.14.0`
* Bump `aspectj` from `1.9.19` to `1.9.21.2`

---

## v 27.0.0 2023-10-13
### Breaking changes
* Migrate to Java 17 [MODINVSTOR-1079](https://issues.folio.org/browse/MODINVSTOR-1079)
* Disables `authority-storage`, `authority-source-files`, `authority-note-types` and `authority-reindex` [MODINVSTOR-1099](https://issues.folio.org/browse/MODINVSTOR-1099)

### New APIs versions
* Provides `hrid-settings-storage 1.3` [MODINVSTOR-921](https://issues.folio.org/browse/MODINVSTOR-921)
* Provides `loan-types 2.3`
* Provides `electronic-access-relationships 1.1`
* Provides `inventory-hierarchy 0.3`
* Required `user-tenants 1.0`
* Optional `consortia 1.0`

### Features
* Added new column complete_updated_date into INSTANCE table that will be used in mod-oai-pmh module: [MODINVSTOR-1105](https://issues.folio.org/browse/MODINVSTOR-1105)
* Shadow Instance Synchronization [MODINVSTOR-1076](https://issues.folio.org/browse/MODINVSTOR-1076)
* Convert Local Instance to Shared Instance [MODINVSTOR-1073](https://issues.folio.org/browse/MODINVSTOR-1073)
* Ad hoc Shadow Instance creation when adding a Holdings to a Shared Instance [MODINVSTOR-1103](https://issues.folio.org/browse/MODINVSTOR-1103)
* Use instanceRepository.getById(id) for GET instance by id [MODINVSTOR-1038](https://issues.folio.org/browse/MODINVSTOR-1038)
* Handle deleted holdings/items in harvest [MODINVSTOR-1048](https://issues.folio.org/browse/MODINVSTOR-1048)
* Add concept of system call number type [MODINVSTOR-1046](https://issues.folio.org/browse/MODINVSTOR-1046)
* Create endpoints to retrieve all reindex jobs [MODINVSTOR-990](https://issues.folio.org/browse/MODINVSTOR-990)
* Create API endpoint to get the current maximum assigned HRID [MODINVSTOR-921](https://issues.folio.org/browse/MODINVSTOR-921)
* Publish domain event when service point is updated [MODINVSTOR-1043](https://issues.folio.org/browse/MODINVSTOR-1043)
* Publish domain event when service point is deleted [MODINVSTOR-1077](https://issues.folio.org/browse/MODINVSTOR-1077)
* Support NLM call-numbers for shelf order generation [MODINVSTOR-1066](https://issues.folio.org/browse/MODINVSTOR-1066)
* Support SuDoc call-numbers for shelf order generation [MODINVSTOR-1069](https://issues.folio.org/browse/MODINVSTOR-1069)
* Handle bound-with items [MODINVSTOR-1070](https://issues.folio.org/browse/MODINVSTOR-1070)

### Bug fixes
* Remove Related Instance Type Instantiation During Tenant Instantiation ([MODINVSTOR-1039](https://issues.folio.org/browse/MODINVSTOR-1039))
* Fix of metadata update on call number changes via the holdings record ([MODINVSTOR-1053](https://issues.folio.org/browse/MODINVSTOR-1053))
* Items with multiple circulation notes that do not have an id cannot be updated ([MODINVSTOR-1096](https://issues.folio.org/browse/MODINVSTOR-1096))

### Tech Dept
* Supporting source in /inventory-hierarchy/updated-instance-ids ([MODINVSTOR-1045](https://issues.folio.org/browse/MODINVSTOR-1045))
* Unify API response in case of ValidationException ([MODINVSTOR-1055](https://issues.folio.org/browse/MODINVSTOR-1055))
* Add missing item field descriptions ([MODINVSTOR-400](https://issues.folio.org/browse/MODINVSTOR-400))
* Fix creating duplicate fkeys on instance_source_marc ([MODINVSTOR-1086](https://issues.folio.org/browse/MODINVSTOR-1086))

---

## 26.0.0 2023-02-14
* New PUT API where a single item ID and a list of holdings IDs can be created ([MODINVSTOR-1022](https://issues.folio.org/browse/MODINVSTOR-1022))
* Extend subjects, alternativeTitles, series with authorityId ([MODINVSTOR-1010](https://issues.folio.org/browse/MODINVSTOR-1010))
* API to get the current maximum assigned HRID ([MODINVSTOR-921](https://issues.folio.org/browse/MODINVSTOR-921))
* provides `inventory-view 2.0`
* provides `inventory-view-instance-set 2.0`
* provides `item-storage-dereferenced 1.0`
* provides `instance-storage 10.0`
* provides `instance-storage-batch 2.0`
* provides `instance-storage-batch-sync 2.0`
* provides `instance-storage-batch-sync-unsafe 2.0`
* provides `bound-with-parts-storage 1.1` [MODINVSTOR-1022](https://issues.folio.org/browse/MODINVSTOR-1022)
* provides `hrid-settings-storage 1.3` [MODINVSTOR-921](https://issues.folio.org/browse/MODINVSTOR-921)
* disables `authority-storage`, `authority-source-files`, `authority-note-types` and `authority-reindex` APIs. They are not supported by this module anymore. [MODINVSTOR-1099](https://issues.folio.org/browse/MODINVSTOR-1099)
* Added new column complete_updated_date into INSTANCE table that will be used in mod-oai-pmh module: [MODINVSTOR-1105](https://issues.folio.org/browse/MODINVSTOR-1105)
* Shadow Instance Synchronization [MODINVSTOR-1076](https://issues.folio.org/browse/MODINVSTOR-1076)
* Ad hoc Shadow Instance creation when adding a Holdings to a Shared Instance [MODINVSTOR-1103](https://issues.folio.org/browse/MODINVSTOR-1103)

## 25.0.0 2022-10-25

* Upgraded RMB to 35.0.0 ([MODINVSTOR-965](https://issues.folio.org/browse/MODINVSTOR-965))
* Extend authority schema with Authority Natural ID ([MODINVSTOR-955](https://issues.folio.org/browse/MODINVSTOR-955))
* Create a pre-defined Authority Source file list, extend authority schema with sourceFileId ([MODINVSTOR-892](https://issues.folio.org/browse/MODINVSTOR-892))
* Fixed effective location migration script for holdings records ([MODINVSTOR-940](https://issues.folio.org/browse/MODINVSTOR-940))
* Adds integrity checks for statistical code types during upgrade ([MODINVSTOR-935] (https://issues.folio.org/browse/MODINVSTOR-935))
* GET Instance Set by CQL ([MODINVSTOR-918](https://issues.folio.org/browse/MODINVSTOR-918))
* Added Variant title and Former title to alternative title default types ([MODINVSTOR-389] (https://issues.folio.org/browse/MODINVSTOR-389))
* Extend instance contributors schema with Authority ID ([MODINVSTOR-950](https://issues.folio.org/browse/MODINVSTOR-950))
* Require CQL query for bulk delete of instances/holdings/items ([MODINVSTOR-576](https://issues.folio.org/browse/MODINVSTOR-576), [MODINVSTOR-901](https://issues.folio.org/browse/MODINVSTOR-901))
* Batch update with optimistic locking disabled ([MODINVSTOR-924](https://issues.folio.org/browse/MODINVSTOR-924))
* Support filtering by instance field when searching holdings ([MODINVSTOR-890](https://issues.folio.org/browse/MODINVSTOR-890))
* GET /inventory-view/instances warns "No configuration for table instance_holdings_item_view" ([MODINVSTOR-929](https://issues.folio.org/browse/MODINVSTOR-929))
* Speed up setEffectiveHoldingsLocation.sql migration ([MODINVSTOR-949](https://issues.folio.org/browse/MODINVSTOR-949))
* Remove `DB_*_READER` environment variables from ModuleDescriptor ([MODINVSTOR-974](https://issues.folio.org/browse/MODINVSTOR-974))
* provides `authority-storage 1.1`
* provides `authority-source-files 1.0`
* provides `inventory-view-instance-set 1.0`
* provides `instance-storage 9.0`
* provides `holdings-storage 6.0`
* provides `item-storage 10.0`
* provides `instance-storage-batch-sync-unsafe 1.0`
* provides `holdings-storage-batch-sync-unsafe 1.0`
* provides `item-storage-batch-sync-unsafe 1.0`

## 24.0.3 2022-08-17

* Restrict cancellation of already finished reindex job ([MODINVSTOR-936] (https://issues.folio.org/browse/MODINVSTOR-936))
* Remove shelfKey pattern check for call-number ([MODINVSTOR-942] (https://issues.folio.org/browse/MODINVSTOR-942))

## 24.0.2 2022-08-10

* Fixing when migration script to populate holdings effective locations runs ([MODINVSTOR-940] (https://issues.folio.org/browse/MODINVSTOR-940))

## 24.0.1 2022-07-18

* Improve populating shelfKey from callNumber ([MODINVSTOR-932](https://issues.folio.org/browse/MODINVSTOR-932))
* POST /item-storage/items error message on non-UUID statistical code ID ([MODINVSTOR-755](https://issues.folio.org/browse/MODINVSTOR-755))

## 24.0.0 2022-07-01

* Upgraded RMB to 34.0.0 ([MODINVSTOR-915](https://issues.folio.org/browse/MODINVSTOR-915))
* Added integrity checks to statisticalCodeIds in instance records ([MODINVSTOR-885](https://issues.folio.org/browse/MODINVSTOR-885))
* Removed UUID contraint on statisticalCodeIds in instance Records ([MODINVSTOR-885](https://issues.folio.org/browse/MODINVSTOR-885))
* Combined calls to retrieve HRID settings and getting sequence values ([MODINVSTOR-894](https://issues.folio.org/browse/MODINVSTOR-894))
* Allow response to be returned to the api client without waiting for domain event publishing during instance creation ([MODINVSTOR-894](https://issues.folio.org/browse/MODINVSTOR-894))
* Enable optimistic locking 'failOnConflict' for authorities ([MODINVSTOR-909](https://issues.folio.org/browse/MODINVSTOR-909))
* publicationPeriod not changed after record update ([MODINVSTOR-874](https://issues.folio.org/browse/MODINVSTOR-874))
* Store Shelving Order in item (Dewey call numbers) ([MODINVSTOR-876](https://issues.folio.org/browse/MODINVSTOR-876))
* Store Shelving Order in item (Other scheme) ([MODINVSTOR-877](https://issues.folio.org/browse/MODINVSTOR-877))
* Undefined permission 'finance.order-transaction-summaries.item.get', ... ([MODINVSTOR-882](https://issues.folio.org/browse/MODINVSTOR-882))
* bad data in item.statisticalCodeIds stops harvest ([MODINVSTOR-895](https://issues.folio.org/browse/MODINVSTOR-895))
* add GET for /inventory-storage/migrations/jobs ([MODINVSTOR-896](https://issues.folio.org/browse/MODINVSTOR-896))
* PUT inventory/instances fails when relatedInstances is present ([MODINVSTOR-898](https://issues.folio.org/browse/MODINVSTOR-898))
* Spurious fails in ItemStorageTest.canSearchForItemsByBarcode ([MODINVSTOR-904](https://issues.folio.org/browse/MODINVSTOR-904))
* Optimistic locking makes Inventory-Batch APIs upsert loads fail ([MODINVSTOR-910](https://issues.folio.org/browse/MODINVSTOR-910))
* Send Kafka message after returning API response ([MODINVSTOR-911](https://issues.folio.org/browse/MODINVSTOR-911))
* Enhance inventory-hierarchy view to make it return instances by date search criteria that have holdings only ([MODINVSTOR-912](https://issues.folio.org/browse/MODINVSTOR-912))
* provides `item-storage-dereferenced 0.2`
* provides `holdings-storage 5.1`
* provides `holdings-storage-batch-sync 1.1`
* provides `instance-storage 8.1`
* provides `instance-storage-batch 1.1`
* provides `instance-storage-batch-sync 1.1`
* provides `inventory-view 1.1`

## 23.0.0 2022-02-22

* Added trigger to prevent statistical code deletion when in use ([MODINVSTOR-829](https://issues.folio.org/browse/MODINVSTOR-829))
* Added administrative notes to item, instance, and holdings records (MODINVSTOR-834, MODINVSTOR-833, [MODINVSTOR-832](https://issues.folio.org/browse/MODINVSTOR-832))
* Upgrade to RMB 33.1.3, 33.2.4 (CVE-2021-44228) (MODINVSTOR-851, [MODINVSTOR-868](https://issues.folio.org/browse/MODINVSTOR-868))
* Introduces validation against RAML API specs for 147 APIs ([MODINVSTOR-24](https://issues.folio.org/browse/MODINVSTOR-24))
* Adds related-instances endpoint ([MODINVSTOR-861](https://issues.folio.org/browse/MODINVSTOR-861))

## 22.0.0 2021-10-06

* Added new identifiers for ISMN and UPC ([MODINVSTOR-770](https://issues.folio.org/browse/MODINVSTOR-770))
* Keyword searches now search alternate title fields ([MODINVSTOR-719](https://issues.folio.org/browse/MODINVSTOR-719))
* Kafka topic names now include environment and tenant ID ([MODINVSTOR-738](https://issues.folio.org/browse/MODINVSTOR-738))
* Includes missing changes to `get_items_and_holdings_view` function during upgrade ([MODINVSTOR-759](https://issues.folio.org/browse/MODINVSTOR-759))
* Enforce item barcode uniqueness ([MODINVSTOR-523](https://issues.folio.org/browse/MODINVSTOR-523))
* Provides endpoint to update all preceding / succeeding titles together ([MODINVSTOR-742](https://issues.folio.org/browse/MODINVSTOR-742))
* Provides an endpoint for iterating through all instances ([MODINVSTOR-763](https://issues.folio.org/browse/MODINVSTOR-763))
* Implement a new endpoint for dereferenced item records ([MODINVSTOR-769](https://issues.folio.org/browse/MODINVSTOR-769))
* Update RMB to 33.1.1 and Vert.x to 4.1.4 ([MODINVSTOR-793](https://issues.folio.org/browse/MODINVSTOR-793))
* Provides `item-storage 9.0`
* Provides `item-storage-batch-sync 1.0`
* Provides `holdings-storage 5.0`
* Provides `holdings-storage-batch-sync 1.0`
* Provides `instance-storage 8.0`
* Provides `instance-preceding-succeeding-titles 0.2`
* Provides `instance-storage-batch 1.0`
* Provides `instance-storage-batch-sync 1.0`
* Provides `instance-iteration 0.1`

## 21.0.0 2021-06-10

* Introduces `publication period` for `instances` ([MODINVSTOR-723](https://issues.folio.org/browse/MODINVSTOR-723))
* Determines `publication year` based upon `publication period` ([MODINVSTOR-724](https://issues.folio.org/browse/MODINVSTOR-724))
* Introduces `bound with parts` record type ([MODINVSTOR-702](https://issues.folio.org/browse/MODINVSTOR-702))
* Adds `effective location` attribute to holdings record and inventory-hierarchy API (MODINVSTOR-669, [MODINVSTOR-670](https://issues.folio.org/browse/MODINVSTOR-670))
* Defaults instance.previouslyHeld to false ([MODINVSTOR-454](https://issues.folio.org/browse/MODINVSTOR-454))
* Adds full-text index for classifications field in instance table ([MODINVSTOR-716](https://issues.folio.org/browse/MODINVSTOR-716))
* Closes database connections when encountering errors in the /inventory-hierarchy endpoint ([MODINVSTOR-677](https://issues.folio.org/browse/MODINVSTOR-677))
* `embed_postgres` command line option is no longer supported ([MODINVSTOR-728](https://issues.folio.org/browse/MODINVSTOR-728))
* Logs when messages cannot be published to Kafka ([MODINVSTOR-663](https://issues.folio.org/browse/MODINVSTOR-663))
* Upgrades to RAML Module Builder 33.0.0 ([MODINVSTOR-728](https://issues.folio.org/browse/MODINVSTOR-728))
* Upgrades to Vert.x 4.1.0.CR1 ([MODINVSTOR-728](https://issues.folio.org/browse/MODINVSTOR-728))
* Provides `bound-with-parts-storage 1.0`  ([MODINVSTOR-702](https://issues.folio.org/browse/MODINVSTOR-702))
* Provides `holdings-storage 4.6` ([MODINVSTOR-669](https://issues.folio.org/browse/MODINVSTOR-669))
* Provides `instance-storage 7.8` ([MODINVSTOR-723](https://issues.folio.org/browse/MODINVSTOR-723))
* Provides `inventory-hierarchy 0.2` ([MODINVSTOR-670](https://issues.folio.org/browse/MODINVSTOR-670))

## 20.2.0 2021-04-23

* Determines shelving order for existing items during tenant upgrade ([MODINVSTOR-521](https://issues.folio.org/browse/MODINVSTOR-521))

## 20.1.0 2021-03-25

* Introduces `contributorsNames` CQL index for searching `instances` ([MODINVSTOR-705](https://issues.folio.org/browse/MODINVSTOR-705))
* Introduces `REPLICATION_FACTOR` environment variable for configuring Kafka topic replication ([MODINVSTOR-694](https://issues.folio.org/browse/MODINVSTOR-694))

## 20.0.0 2021-03-11

* Determines effective shelving order for items (MODINVSTOR-381, [MODINVSTOR-679](https://issues.folio.org/browse/MODINVSTOR-679))
* Statistical codes must have an unique name ([MODINVSTOR-596](https://issues.folio.org/browse/MODINVSTOR-596))
* Adds identifier type `Cancelled system control number` ([MODINVSTOR-636](https://issues.folio.org/browse/MODINVSTOR-636))
* Status date set on newly created instance records ([MODINVSTOR-509](https://issues.folio.org/browse/MODINVSTOR-509))
* Item status date is set for newly created item records ([MODINVSTOR-508](https://issues.folio.org/browse/MODINVSTOR-508))
* HRID can be generated with and without leading zeroes ([MODINVSTOR-661](https://issues.folio.org/browse/MODINVSTOR-661))
* Publishes domain events to Kafka to support searching and remote storage (MODINVSTOR-639, MODINVSTOR-640, MODINVSTOR-644, MODINVSTOR-649, MODINVSTOR-654, MODINVSTOR-662, [MODINVSTOR-664](https://issues.folio.org/browse/MODINVSTOR-664))
* Detects potential optimistic concurrency collisions for instances, holdings records and items ([MODINVSTOR-656](https://issues.folio.org/browse/MODINVSTOR-656))
* Upgrades to RAML Module Builder 32.1.0
* Upgrades to Vert.x 4.0.0 ([MODINVSTOR-624](https://issues.folio.org/browse/MODINVSTOR-624))
* Provides `item-storage 8.9`
* Provides `item-storage-batch-sync 0.6`
* Provides `holdings-storage 4.5`
* Provides `instance-storage 7.6`
* Provides `inventory-record-bulk 1.0`
* Provides `hrid-settings-storage 1.2`
* Provides `_tenant 2.0`
* Provides `inventory-view 1.0`
* Provides `instance-reindex 0.1`
* No longer provides `instance-bulk`
* Requires Kafka 2.6

## 19.4.0 2020-10-08

* Introduces public and staff only holdings statements notes ([MODINVSTOR-543](https://issues.folio.org/browse/MODINVSTOR-543))
* Introduces `Unknown` item status ([MODINVSTOR-588](https://issues.folio.org/browse/MODINVSTOR-588))
* Introduces holdings record `source` property (MODINVSTOR-542, [MODINVSTOR-590](https://issues.folio.org/browse/MODINVSTOR-590))
* Introduces instance `match key` property ([MODINVSTOR-587](https://issues.folio.org/browse/MODINVSTOR-587))
* Includes `location code` in hierarchy API ([MODINVSTOR-589](https://issues.folio.org/browse/MODINVSTOR-589))
* Requires JDK 11 ([MODINVSTOR-555](https://issues.folio.org/browse/MODINVSTOR-555))
* Upgrades to RAML Module Builder 31.1.0 (MODINVSTOR-542, MODINVSTOR-557, [MODINVSTOR-569](https://issues.folio.org/browse/MODINVSTOR-569))
* Provides `item-storage 8.6`
* Provides `holdings-storage 4.4`
* Provides `holdings-storage-batch-sync 0.3`
* Provides `instance-storage 7.5`

## 19.3.0 2020-07-28

* Add 'Aged to lost' status to allowed item statuses list ([MODINVSTOR-503](https://issues.folio.org/browse/MODINVSTOR-503))
* Solving issue with GBV by making migration for preceeding and succeeding titles to be possible to run several times without failing ([MODINVSTOR-541](https://issues.folio.org/browse/MODINVSTOR-541))
* Separate filtering from response generation in oai-pmh view ([MODINVSTOR-536](https://issues.folio.org/browse/MODINVSTOR-536))
* Item record. Effective call number (item), eye readable. Search on exact data in Call number data element ([MODINVSTOR-481](https://issues.folio.org/browse/MODINVSTOR-481))
* Holdings record. Call number, eye readable. Search on exact data in Call number data element ([MODINVSTOR-480](https://issues.folio.org/browse/MODINVSTOR-480))
* Search queries without database index ([MODINVSTOR-472](https://issues.folio.org/browse/MODINVSTOR-472))
* Upgrade to raml-module-builder (RMB) 30.2.4 ([MODINVSTOR-532](https://issues.folio.org/browse/MODINVSTOR-532))
* Expand oai-pmh view with additional fields ([MODINVSTOR-498](https://issues.folio.org/browse/MODINVSTOR-498))
* OAI-PMH view doesn't respond in Bug Fest ([MODINVSTOR-527](https://issues.folio.org/browse/MODINVSTOR-527))
* Change permissions location for oai-pmh view ([MODINVSTOR-524](https://issues.folio.org/browse/MODINVSTOR-524))
* Add Index for Instances Full-text Subjects Search ([MODINVSTOR-499](https://issues.folio.org/browse/MODINVSTOR-499))
* Postgres requires special permissions to disable triggers for migrations ([MODINVSTOR-476](https://issues.folio.org/browse/MODINVSTOR-476))
* Fixing inconsistent hit counts (totalRecords) estimation ([MODINVSTOR-519](https://issues.folio.org/browse/MODINVSTOR-519))
* Bulk download Instance UUIDs OOM ([MODINVSTOR-465](https://issues.folio.org/browse/MODINVSTOR-465))
* There were upgraded these interfaces:
  * "item-storage" to version 8.5
  * "item-storage-batch-sync" to version 0.5
  * "oaipmhview" to version 1.1

## 19.2.2 2020-06-17

* Disable triggers for migrations without requiring special permissions ([MODINVSTOR-476](https://issues.folio.org/browse/MODINVSTOR-476))

## 19.2.1 2020-06-15

* Upgrade to RAML Module Builder 30.0.3 ([MODINVSTOR-519](https://issues.folio.org/browse/MODINVSTOR-519)):
  * Use where-only clause for the "count query" for consistent hit count estimations ([RMB-645](https://issues.folio.org/browse/RMB-645))
  * Fix sorby title and limit=0 gives zero hits ([RMB-640](https://issues.folio.org/browse/RMB-640))

## 19.2.0 2020-06-08

* Introduces normalised ISBN and invalid ISBN indexes (MODINVSTOR-413, [MODINVSTOR-474](https://issues.folio.org/browse/MODINVSTOR-474))
* Introduces normalised call number indexes (MODINVSTOR-485, [MODINVSTOR-488](https://issues.folio.org/browse/MODINVSTOR-488))
* Introduces `Withdrawn` and `Lost and paid`  item statuses (MODINVSTOR-461, [MODINVSTOR-494](https://issues.folio.org/browse/MODINVSTOR-494))
* Uses consistent record count estimation irrespective of sorting (MODINVSTOR-468, [MODINVSTOR-513](https://issues.folio.org/browse/MODINVSTOR-513))
* Introduces API to support OAI-PMH integration (MODINVSTOR-477, MODINVSTOR-486, [MODINVSTOR-492](https://issues.folio.org/browse/MODINVSTOR-492))
* Adds `upsert` support in batch APIs (MODINVSTOR- 478)
* Removes instance relationships replaced by preceding / succeeding titles records ([MODINVSTOR-451](https://issues.folio.org/browse/MODINVSTOR-451))
* Adds indexes for `staffSuppress` ,  `suppressFromDiscovery`  and `createdDate` properties (MODINVSTOR-473, [MODINVSTOR-479](https://issues.folio.org/browse/MODINVSTOR-479))
* Sets derived effective call number  components in item batch API ([MODINVSTOR-458](https://issues.folio.org/browse/MODINVSTOR-458))
* provides `item-storage 8.4`
* provides `item-storage-batch-sync 0.4`
* provides `holdings-storage 4.2`
* provides `holdings-storage-batch-sync 0.2`
* provides `instance-storage-batch-sync 0.3`
* provides `oaipmhview 1.0`
* Upgrades to RAML Module Builder 30.0.2 (MODINVSTOR-468, MODINVSTOR-487, [MODINVSTOR-513](https://issues.folio.org/browse/MODINVSTOR-513))
  * Rebuilds many database indexes during upgrade
  * Rebuilds database statistics during upgrade

## 19.1.0 2020-03-14

* Improves performance of `keyword` search ([MODINVSTOR-455](https://issues.folio.org/browse/MODINVSTOR-455))
* Upgrades to RAML Module Builder 29.3.1

## 19.0.0 2020-03-09

* Restricts item statuses (MODINVSTOR-283, [MODINVSTOR-416](https://issues.folio.org/browse/MODINVSTOR-416))
* Introduces `Claimed returned` item status ([MODINVSTOR-433](https://issues.folio.org/browse/MODINVSTOR-433))
* Makes item status required (MODINVSTOR-425, [MODINVSTOR-416](https://issues.folio.org/browse/MODINVSTOR-416))
* Makes item status date read-only (MODINVSTOR-392, [MODINVSTOR-416](https://issues.folio.org/browse/MODINVSTOR-416))
* Allows only one copy number for an item (MODINVSTOR-332, [MODINVSTOR-416](https://issues.folio.org/browse/MODINVSTOR-416))
* Stores effective call number type for item ([MODINVSTOR-361](https://issues.folio.org/browse/MODINVSTOR-361))
* Introduces preceding and succeeding titles (MODINVSTOR-441, [MODINVSTOR-447](https://issues.folio.org/browse/MODINVSTOR-447))
* Changes reference mode of issuances ([MODINVSTOR-431](https://issues.folio.org/browse/MODINVSTOR-431))
* Streams responses when getting items, holdings and instances ([MODINVSTOR-438](https://issues.folio.org/browse/MODINVSTOR-438))
* Provides an API to get only `id` property of all records matching a CQL query ([MODINVSTOR-439](https://issues.folio.org/browse/MODINVSTOR-439))
* Provides access to the JSON schemas used by the module ([MODINVSTOR-404](https://issues.folio.org/browse/MODINVSTOR-404))
* Defaults suppress from discovery to false ([MODINVSTOR-447](https://issues.folio.org/browse/MODINVSTOR-447))
* Adds foreign key constraint for effective location ([MODINVSTOR-407](https://issues.folio.org/browse/MODINVSTOR-407))
* Adds indexes for `call number` and `accession number`  (MODINVSTOR-435, [MODINVSTOR-444](https://issues.folio.org/browse/MODINVSTOR-444))
* Ensures update triggers run in the correct order ([MODINVSTOR-415](https://issues.folio.org/browse/MODINVSTOR-415))
* Upgrades to RAML Module Builder 29.3.0 (MODINVSTOR-139, MODINVSTOR-379, MODINVSTOR-405. MODINVSTOR-418. MODINVSTOR-429, [MODINVSTOR-430](https://issues.folio.org/browse/MODINVSTOR-430))
* Provides `item-storage 8.2`
* Provides `item-storage-batch-sync 0.3`
* Provides `holdings-storage 4.1`
* Provides `instance-storage 7.4`
* Provides `instance-storage-batch-sync 0.2`
* Provides `instance-bulk 0.1`
* Provides `instance-preceeding-succeeding-titles 0.1`
* Provides `_jsonSchemas 1.0`

## 18.2.0 2019-12-20

* Increase maximum number of digits for human readable IDs (HRID) (MODINVSTOR-410, MODINVSTOR-411, [MODINVSTOR-412](https://issues.folio.org/browse/MODINVSTOR-412))
* Include HRID in sample records for instances, items and holdings ([MODINVSTOR-397](https://issues.folio.org/browse/MODINVSTOR-397))
* Improve performance of searching by effective location (MODINVSTOR-407, [MODINVSTOR-409](https://issues.folio.org/browse/MODINVSTOR-409))


## 18.1.0 2019-12-06

* Upgrades RAML Module Builder (RMB) to version [29.1.0](https://github.com/folio-org/raml-module-builder/blob/v29.1.0/NEWS.md) (was 28.1.0) ([MODINVSTOR-403](https://issues.folio.org/browse/MODINVSTOR-403))

Most notable RAML Module Builder changes:
* Estimate hit counts [(RMB-506](https://issues.folio.org/browse/RMB-506))
* Bugfix that break clients that do not comply with the interface spec: POST /\_/tenant requires JSON body with module_to ([RMB-510](https://issues.folio.org/browse/RMB-510))

## 18.0.0 2019-11-29

* Generates `HRID`s for `instance`, `holdings` and `item` records (MODINVSTOR-363, MODINVSTOR-373, MODINVSTOR-374, [MODINVSTOR-375](https://issues.folio.org/browse/MODINVSTOR-375))
* Derives `effective location` for `item` records ([MODINVSTOR-348](https://issues.folio.org/browse/MODINVSTOR-348))
* Derives `effective call number, suffix and prefix` for `item` records (MODINVSTOR-357, MODINVSTOR-358, MODINVSTOR-360, [MODINVSTOR-391](https://issues.folio.org/browse/MODINVSTOR-391))
* Introduces `keyword` CQL index for `instance` records ([MODINVSTOR-349](https://issues.folio.org/browse/MODINVSTOR-349))
* Introduces `last check in date` property  for `item` records ([MODINVSTOR-386](https://issues.folio.org/browse/MODINVSTOR-386))
* Introduces `preceding-succeeding` instance relationship ([MODINVSTOR-343](https://issues.folio.org/browse/MODINVSTOR-343))
* Introduces `Uniform title` alternative title type  ([MODINVSTOR-350](https://issues.folio.org/browse/MODINVSTOR-350))
* Introduces `LC (local)` and `SUDOC` classification types  ([MODINVSTOR-351](https://issues.folio.org/browse/MODINVSTOR-351))
* Sample `instance` records now use  `FOLIO` as the `source`  property ([MODINVSTOR-337](https://issues.folio.org/browse/MODINVSTOR-337))
* Makes `permanent location` a required property for `holdings` ([MODINVSTOR-364](https://issues.folio.org/browse/MODINVSTOR-364))
* Makes `code` a required properties for `institution`, `campus` and `library` location units ([MODINVSTOR-315](https://issues.folio.org/browse/MODINVSTOR-315))
* Applies stricter validation on UUID properties in `instance` and `holdings` records (MODINVSTOR-297, [MODINVSTOR-370](https://issues.folio.org/browse/MODINVSTOR-370))
* Introduces synchronous batch APIs for `items`, `holdings` and `instances` ([MODINVSTOR-353](https://issues.folio.org/browse/MODINVSTOR-353))
* Upgrades RAML Module Builder to version 27.0.0 (MODINVSTOR-368, MODINVSTOR-383, [MODINVSTOR-385](https://issues.folio.org/browse/MODINVSTOR-385))
* Generates  `metadata` property for instances created using batch API ([MODINVSTOR-387](https://issues.folio.org/browse/MODINVSTOR-387))
* Fixes bug with `item status date` being changed even when status has not changed ([MODINVSTOR-376](https://issues.folio.org/browse/MODINVSTOR-376))
* Fixes bug with `instance status date` not being set when status changes ([MODINVSTOR-367](https://issues.folio.org/browse/MODINVSTOR-367))
* Changes container memory management (MODINVSTOR-396, [FOLIO-2358](https://issues.folio.org/browse/FOLIO-2358))
* Provides `item-storage 7.8`
* Provides `holdings-storage 4.0`
* Provides `location-units 2.0`
* Provides `hrid-settings-storage 1.0`
* Provides `item-storage-batch-sync 0.1`
* Provides `holdings-storage-batch-sync 0.1`
* Provides `instance-storage-batch-sync 0.1`

## 17.0.0 2019-09-09

* Adds tags to `items records` ([MODINVSTOR-322](https://issues.folio.org/browse/MODINVSTOR-322))
* Adds tags to `holdings records` ([MODINVSTOR-324](https://issues.folio.org/browse/MODINVSTOR-324))
* Adds tags to `instances` ([MODINVSTOR-323](https://issues.folio.org/browse/MODINVSTOR-323))
* Adds nature of content terms to `instances` ([MODINVSTOR-327](https://issues.folio.org/browse/MODINVSTOR-327))
* Introduces `item damaged statuses` ([MODINVSTOR-286](https://issues.folio.org/browse/MODINVSTOR-286))
* Introduces preview `instance storage batch` API ([MODINVSTOR-291](https://issues.folio.org/browse/MODINVSTOR-291))
* Limits number of database connections when processing instance batches ([MODINVSTOR-330](https://issues.folio.org/browse/MODINVSTOR-330))
* Disallows deletion of in use `instance types` ([MODINVSTOR-301](https://issues.folio.org/browse/MODINVSTOR-301))
* Disallows creation of `holdings type` with existing name ([MODINVSTOR-318](https://issues.folio.org/browse/MODINVSTOR-318))
* Fixes failures when combining CQL array index and other indexes ([MODINVSTOR-319](https://issues.folio.org/browse/MODINVSTOR-319))
* Use sub-queries rather than views for cross-record searching (MODINVSTOR-339, [MODINVSTOR-347](https://issues.folio.org/browse/MODINVSTOR-347))
* Provides `item-storage` interface version 7.5 ([MODINVSTOR-322](https://issues.folio.org/browse/MODINVSTOR-322))
* Provides `holdings-storage` interface version 3.1 ([MODINVSTOR-324](https://issues.folio.org/browse/MODINVSTOR-324))
* Provides `instance-storage` interface version 7.2 (MODINVSTOR-323, [MODINVSTOR-327](https://issues.folio.org/browse/MODINVSTOR-327))
* Provides `instance-storage-batch` interface version 0.2 ([MODINVSTOR-291](https://issues.folio.org/browse/MODINVSTOR-291))
* Provides `item-damaged-statuses` interface version 1.0 ([MODINVSTOR-286](https://issues.folio.org/browse/MODINVSTOR-286))
* Upgrades RAML Module Builder to version 27.0.0 ([MODINVSTOR-342](https://issues.folio.org/browse/MODINVSTOR-342))

## 16.0.0 2019-07-23

* Provides `instance-note-types` interface version 1.0 ([MODINVSTOR-300](https://issues.folio.org/browse/MODINVSTOR-300))
* Provides `instance-storage` interface version 7.0 (MODINVSTOR-312,MODINVSTOR-297)
* Provides `nature-of-content-terms` interface version 1.0 ([MODINVSTOR-309](https://issues.folio.org/browse/MODINVSTOR-309))
* Provides `item-storage` interface version 7.4 ([MODINVSTOR-310](https://issues.folio.org/browse/MODINVSTOR-310))
* Provides `identifier-types` interface version 1.2 ([MODINVSTOR-305](https://issues.folio.org/browse/MODINVSTOR-305))
* Provides `classification-types` interface version 1.2 ([MODINVSTOR-306](https://issues.folio.org/browse/MODINVSTOR-306))
* Provides `modes-of-issuance` interface version 1.1 ([MODINVSTOR-307](https://issues.folio.org/browse/MODINVSTOR-307))
* Changes structure of instance.notes ([MODINVSTOR-312](https://issues.folio.org/browse/MODINVSTOR-312))
* Adds date and source fields to circulation notes ([MODINVSTOR-310](https://issues.folio.org/browse/MODINVSTOR-310))
* Validates UUID properties with pattern ([MODINVSTOR-297](https://issues.folio.org/browse/MODINVSTOR-297))
* Adds property `source` to identifier type, classification type, mode of issuance (MODINVSTOR-305, MODINVSTOR-306, [MODINVSTOR-307](https://issues.folio.org/browse/MODINVSTOR-307))
* Sets array modifiers for contributors, identifiers ([MODINVSTOR-311](https://issues.folio.org/browse/MODINVSTOR-311))
* Populate identifiers reference table with more identifiers ([MODINVSTOR-313](https://issues.folio.org/browse/MODINVSTOR-313))
* Aligns barcode index (on `item`) with the SQL generated
* Upgrades RAML Module Builder to version 26.2.2 ([MODINVSTOR-285](https://issues.folio.org/browse/MODINVSTOR-285))
* Improves test coverage, error logging, sample data.

## 15.5.1 2019-06-09

* Performance improvement ([MODINVSTOR-255](https://issues.folio.org/browse/MODINVSTOR-255))
* Bug fix ([MODINVSTOR-289](https://issues.folio.org/browse/MODINVSTOR-289))

## 15.4.0 2019-05-08

* Upgrades RAML Module Builder to version 24.0.0 and aligns MODINVSTOR-278
* Adds reference data for locations, location units, service points MODINVSTOR 279

## 15.3.1 2019-03-23

* Align sample data cross-module: Item statuses to match loan samples in circulation

## 15.3.0 2019-03-15

* Remove branch tag for mod-users submodule

## 15.2.0 2019-03-15

* Provides service point users sample data ([MODINVSTOR-270](https://issues.folio.org/browse/MODINVSTOR-270))
* Loads sample data with `loadSample` ([MODINVSTOR-264](https://issues.folio.org/browse/MODINVSTOR-264))
* Performance optimization, search by barcode ([MODINVSTOR-269](https://issues.folio.org/browse/MODINVSTOR-269))
* Makes sample service points available for pickup ([MODINVSTOR-268](https://issues.folio.org/browse/MODINVSTOR-268))

## 15.1.0 2019-02-19

* Provides `item-storage` interface version 7.3 ([MODINVSTOR-252](https://issues.folio.org/browse/MODINVSTOR-252))
* Provides `service-points` interface version 3.2 ([MODINVSTOR-251](https://issues.folio.org/browse/MODINVSTOR-251))
* Adds `holdShelfExpiryPeriod` to `servicepoint` schema ([MODINVSTOR-251](https://issues.folio.org/browse/MODINVSTOR-251))
* Adds `status.date` to `item` schema ([MODINVSTOR-252](https://issues.folio.org/browse/MODINVSTOR-252))
* Improves performance:
*   Fixes slow identifier search ([MODINVSTOR-266](https://issues.folio.org/browse/MODINVSTOR-266))
*   Use sort or search index depending on result size ([MODINVSTOR-215](https://issues.folio.org/browse/MODINVSTOR-215))
* Adds update of reference data on module upgrade ([MODINVSTOR-263](https://issues.folio.org/browse/MODINVSTOR-263))
* Bugfix: No longer supports <> relation for ID properties ([MODINVSTOR-267](https://issues.folio.org/browse/MODINVSTOR-267))

## 15.0.0 2019-02-01

* Provides `service-points` interface version 3.1 ([MODINVSTOR-235](https://issues.folio.org/browse/MODINVSTOR-235))
* Provides `item-storage` interface version 7.2 ([MODINVSTOR-249](https://issues.folio.org/browse/MODINVSTOR-249))
* Adds `staffSlips` to `servicepoint` schema ([MODINVSTOR-235](https://issues.folio.org/browse/MODINVSTOR-235))
* Adds property `ServicePoint.printByDefault` ([MODINVSTOR-235](https://issues.folio.org/browse/MODINVSTOR-235))
* Adds `circulationNotes` to `item` schema ([MODINVSTOR-249](https://issues.folio.org/browse/MODINVSTOR-249))
* Adds sequences for generating human readable identifiers ([MODINVSTOR-170](https://issues.folio.org/browse/MODINVSTOR-170))
* Improves performance for items by barcode ([MODINVSTOR-247](https://issues.folio.org/browse/MODINVSTOR-247))
* Improves performance for items queried by ID ([MODINVSTOR-248](https://issues.folio.org/browse/MODINVSTOR-248))
* Improves performance for PUT of `Instance` ([MODINVSTOR-254](https://issues.folio.org/browse/MODINVSTOR-254))
* Adds property `Item.purchaseOrderLineIdentifier` ([MODINVSTOR-245](https://issues.folio.org/browse/MODINVSTOR-245))
* Miscellaneous bug-fixes (MODINVSTOR-253, [MODINVSTOR-243](https://issues.folio.org/browse/MODINVSTOR-243))


## 14.0.0 2018-11-30

* Provides `item-storage` interface version 7.0 ([MODINVSTOR-205](https://issues.folio.org/browse/MODINVSTOR-205))
* Provides `holdings-storage` interface version 3.0 ([MODINVSTOR-209](https://issues.folio.org/browse/MODINVSTOR-209))
* Provides `instance-storage` interface version 6.0 (MODINVNSTOR-232, [MODINVSTOR-200](https://issues.folio.org/browse/MODINVSTOR-200))
* Renames `item` property `pieceIdentifiers` to `copyNumbers` ([MODINVSTOR-205](https://issues.folio.org/browse/MODINVSTOR-205))
* Removes property `electronicLocation` from holdingsRecord schema ([MODINVSTOR-227](https://issues.folio.org/browse/MODINVSTOR-227))
* Removes obsolete reference end-point `platforms` ([MODINVSTOR-226](https://issues.folio.org/browse/MODINVSTOR-226))
* Changes structure of property `alternativeTitles` ([MODINVSTOR-200](https://issues.folio.org/browse/MODINVSTOR-200))
* Changes/renames property statisticalCodes in Instance ([MODINVSTOR-241](https://issues.folio.org/browse/MODINVSTOR-241))
* Changes structure of `holdingsStatements` ([MODINVSTOR-228](https://issues.folio.org/browse/MODINVSTOR-228))
* Disallows additional properties in `holdingsRecord` ([MODINVSTOR-229](https://issues.folio.org/browse/MODINVSTOR-229))
* Changes structure of `notes` in `item` ([MODINVSTOR-154](https://issues.folio.org/browse/MODINVSTOR-154))
* Adds 19 new properties to `item` ([MODINVSTOR-154](https://issues.folio.org/browse/MODINVSTOR-154))

## 13.2.0 2018-11-24

* Adds an in transit destination to a item ([MODINVSTOR-230](https://issues.folio.org/browse/MODINVSTOR-230))
* Provides `item-storage` interface version 6.1 ([MODINVSTOR-230](https://issues.folio.org/browse/MODINVSTOR-230))

## 13.1.0 2018-11-19

* Provides `holdings-storage` interface 2.1 ([MODINVSTOR-153](https://issues.folio.org/browse/MODINVSTOR-153) etc)
* Provides `statistical-codes` 1.0 ([MODINVSTOR-221](https://issues.folio.org/browse/MODINVSTOR-221))
* Provides `holdings-note-types` interface 1.0 ([MODINVSTOR-220](https://issues.folio.org/browse/MODINVSTOR-220))
* Provides `item-note-types` interface 1.0 ([MODINVSTOR-220](https://issues.folio.org/browse/MODINVSTOR-220))
* Provides `alternative-title-types` interface 1.0 ([MODINVSTOR-200](https://issues.folio.org/browse/MODINVSTOR-200))
* Provides `call-number-types` interface 1.0 ([MODINVSTOR-210](https://issues.folio.org/browse/MODINVSTOR-210))
* Provides `holdings-types` interface 1.0 ([MODINVSTOR-210](https://issues.folio.org/browse/MODINVSTOR-210))
* Provides `ill-policies` interface 1.0 ([MODINVSTOR-210](https://issues.folio.org/browse/MODINVSTOR-210))
* Extends holdingsRecord with 20+ new optional properties ([MODINVSTOR-153](https://issues.folio.org/browse/MODINVSTOR-153))
* Uses full-text indexes for `instanceType` and `language` (MODINVSTOR-184, [MODINVSTOR-188](https://issues.folio.org/browse/MODINVSTOR-188))

## 13.0.1 2018-10-12

* Extends locations reference data with new required properties ([MODINVSTOR-177](https://issues.folio.org/browse/MODINVSTOR-177))

## 13.0.0 2018-10-10

* Provides `electronic-access-relationships` interface 1.0 ([MODINVSTOR-190](https://issues.folio.org/browse/MODINVSTOR-190))
* Documents `instance` properties ([MODINVSTOR-179](https://issues.folio.org/browse/MODINVSTOR-179))
* Converts to RAML 1.0 ([MODINVSTOR-193](https://issues.folio.org/browse/MODINVSTOR-193))
* Add foreign keys item->holdings_record, holdings_record->instance ([MODINVSTOR-135](https://issues.folio.org/browse/MODINVSTOR-135))
* Removes index on `item.title` ([MODINVSTOR-135](https://issues.folio.org/browse/MODINVSTOR-135))
* Provides `instance-storage` interface 5.0 ([MODINVSTOR-187](https://issues.folio.org/browse/MODINVSTOR-187))
* Provides `holdings-storage` interface 2.0 ([MODINVSTOR-187](https://issues.folio.org/browse/MODINVSTOR-187))
* Provides `item-storage` interface 6.0 ([MODINVSTOR-187](https://issues.folio.org/browse/MODINVSTOR-187))
* Change `instance.edition` to repeatable `editions` ([MODINVSTOR-171](https://issues.folio.org/browse/MODINVSTOR-171))
* Change `instance.formatId` to repeatable `formatIds` ([MODINVSTOR-195](https://issues.folio.org/browse/MODINVSTOR-195))
* Removes property `instance.urls` ([MODINVSTOR-180](https://issues.folio.org/browse/MODINVSTOR-180))
* Removes property `instance.catalogingLevelId` ([MODINVSTOR-186](https://issues.folio.org/browse/MODINVSTOR-186))
* Removes end-point `cataloging-levels`, reference table `cataloging_level` ([MODINVSTOR-186](https://issues.folio.org/browse/MODINVSTOR-186))
* Renames `instance.electronicAccess.relationship` to `relationshipId` ([MODINVSTOR-191](https://issues.folio.org/browse/MODINVSTOR-191))
* Add primary service point for location property ([MODINVSTOR-177](https://issues.folio.org/browse/MODINVSTOR-177))
* Reversed the relationship of ServicePoint to Location ([MODINVSTOR-177](https://issues.folio.org/browse/MODINVSTOR-177))

## 12.8.2 2018-09-16

* Enable UUID syntax check for POST instance and POST holding ([MODINVSTOR-172](https://issues.folio.org/browse/MODINVSTOR-172))
* Uses RMB 19.4.4, fixing fulltext bug with trailing spaces and * ([MODINVSTOR-175](https://issues.folio.org/browse/MODINVSTOR-175))

## 12.8.1 2018-09-13

* Uses RMB 19.4.3, which uses the 'simple' dictionary for fulltext,
  getting around the stopword problem. ([MODINVSTOR-168](https://issues.folio.org/browse/MODINVSTOR-168))

## 12.8.0 2018-09-13

* Adds more properties to instance ([MODINVSTOR-152](https://issues.folio.org/browse/MODINVSTOR-152))
* Provides `instance-storage` interface 4.6 ([MODINVSTOR-152](https://issues.folio.org/browse/MODINVSTOR-152))
* Provides `statistical-code-types` interface 1.0 ([MODINVSTOR-152](https://issues.folio.org/browse/MODINVSTOR-152))
* Provides `cataloging-levels` interface 1.0 ([MODINVSTOR-152](https://issues.folio.org/browse/MODINVSTOR-152))
* Provides `instance-statuses` interface 1.0 ([MODINVSTOR-152](https://issues.folio.org/browse/MODINVSTOR-152))
* Provides `modes-of-issuance` interface 1.0 ([MODINVSTOR-152](https://issues.folio.org/browse/MODINVSTOR-152))

## 12.7.0 2018-09-12

* Uses full text indexing for instance `title` searching ([MODINVSTOR-159](https://issues.folio.org/browse/MODINVSTOR-159))
* Upgrades to RAML Module Builder 19.4.2 ([MODINVSTOR-159](https://issues.folio.org/browse/MODINVSTOR-159))
* Fixes inability to specify page `limit` higher than 100 ([MODINVSTOR-164](https://issues.folio.org/browse/MODINVSTOR-164))

## 12.6.0 2018-09-10

* Adds instance relationship storage ([MODINVSTOR-147](https://issues.folio.org/browse/MODINVSTOR-147))
* Provides `instance-storage` interface 4.5 ([MODINVSTOR-147](https://issues.folio.org/browse/MODINVSTOR-147))
* Provides `instance-relationship-types` interface 1.0 ([MODINVSTOR-147](https://issues.folio.org/browse/MODINVSTOR-147))

## 12.5.1 2018-08-15

* Fixes updating of existing MARC JSON source record ([MODINVSTOR-144](https://issues.folio.org/browse/MODINVSTOR-144))

## 12.5.0 2018-08-02

* Add `service-points-users` endpoint ([MODINVSTOR-132](https://issues.folio.org/browse/MODINVSTOR-132))
* Provides `service-points-users` interface 1.0 ([MODINVSTOR-132](https://issues.folio.org/browse/MODINVSTOR-132))

## 12.4.0 2018-07-26

* Item `status` now defaults to 'Available' ([MODINVSTOR-137](https://issues.folio.org/browse/MODINVSTOR-137))

## 12.3.0 2018-07-24

* Add MARC JSON source record endpoint ([MODINVSTOR-26](https://issues.folio.org/browse/MODINVSTOR-26))
* Provides `instance-storage` interface 4.4 ([MODINVSTOR-26](https://issues.folio.org/browse/MODINVSTOR-26))

## 12.2.0 2018-07-16

* Added locationIds array to service point ([MODINVSTOR-127](https://issues.folio.org/browse/MODINVSTOR-127))
* Provides `service-points` interface 2.1 ([MODINVSTOR-127](https://issues.folio.org/browse/MODINVSTOR-127))

## 12.1.0 2018-07-10

* Upgrade RAML Module Builder to 19.1.5 ([MODINVSTOR-128](https://issues.folio.org/browse/MODINVSTOR-128))

## 12.0.0 2018-07-06

* Upgrade RAML Module Builder to 19.1.3 ([MODINVSTOR-126](https://issues.folio.org/browse/MODINVSTOR-126))
* Provides v2.0 of instance-types, instance-formats, contributor-types (MODINVSTOR-115,-116,-123)
* Add `source`, `code`, `metadata` to instance type ([MODINVSTOR-115](https://issues.folio.org/browse/MODINVSTOR-115))
* Add `source`, `code`, `metadata` to instance format ([MODINVSTOR-116](https://issues.folio.org/browse/MODINVSTOR-116))
* Add `source`, `code`, `metadata` to contributor type ([MODINVSTOR-123](https://issues.folio.org/browse/MODINVSTOR-123))

## 11.1.0 2018-06-25

* Adds `temporaryLocationId` property to holdings records ([MODINVSTOR-97](https://issues.folio.org/browse/MODINVSTOR-97))
* Adds `permanentLocationId` property to item records ([MODINVSTOR-97](https://issues.folio.org/browse/MODINVSTOR-97))
* Provides `item-storage` interface 5.3 ([MODINVSTOR-97](https://issues.folio.org/browse/MODINVSTOR-97))
* Provides `holdings-storage` interface 1.3 ([MODINVSTOR-97](https://issues.folio.org/browse/MODINVSTOR-97))

## 11.0.0 2018-06-21

* Removes `feeFineOwner` property from `service-points` record ([MODINVSTOR-114](https://issues.folio.org/browse/MODINVSTOR-114))
* Fix proxy registration for GET individual `service-point` in module descriptor ([MODINVSTOR-110](https://issues.folio.org/browse/MODINVSTOR-110))
* Provides `service-points` interface 2.0 ([MODINVSTOR-114](https://issues.folio.org/browse/MODINVSTOR-114))
* Change item `id` index to be case insensitive and to remove accents to improve CQL search performance ([MODINVSTOR-121](https://issues.folio.org/browse/MODINVSTOR-121))

## 10.2.0 2018-04-25

* Add 'description' and 'discoveryDisplayName' to locations ([MODINVSTOR-113](https://issues.folio.org/browse/MODINVSTOR-113))
* Provides `locations` interface to 2.1 ([MODINVSTOR-113](https://issues.folio.org/browse/MODINVSTOR-113))

## 10.1.0 2018-04-25

* Add /service-points API ([MODINVSTOR-95](https://issues.folio.org/browse/MODINVSTOR-95))
* Rename `parking` property to `details` in locations ([MODINVSTOR-96](https://issues.folio.org/browse/MODINVSTOR-96))
* Foreign keys in items and holdings for locations (MODINVSTOR-107, [MODINVSTOR-92](https://issues.folio.org/browse/MODINVSTOR-92))
* Rename 'parking' to 'details' in locations ([MODINVSTOR-96](https://issues.folio.org/browse/MODINVSTOR-96))
* Use proper foreign keys in location units ([MODINVSTOR-92](https://issues.folio.org/browse/MODINVSTOR-92))
* Add metadata to locations and location units (MODINVSTOR-101, MODINVSTOR-102, MODINVSTOR-103, [MODINVSTOR-104](https://issues.folio.org/browse/MODINVSTOR-104))
* Provides `locations` interface to 2.0 (MODINVSTOR-96, [MODINVSTOR-104](https://issues.folio.org/browse/MODINVSTOR-104))
* Provides `location-units` interface to 1.1 (MODINVSTOR-101, MODINVSTOR-102, [MODINVSTOR-103](https://issues.folio.org/browse/MODINVSTOR-103))
* Provides `service-points` interface 1.0 ([MODINVSTOR-95](https://issues.folio.org/browse/MODINVSTOR-95))

## 9.0.1 2018-04-04

* GET requests to `/shelf-locations` proxy records from new location model ([MODINVSTOR-85](https://issues.folio.org/browse/MODINVSTOR-85))
* POST/PUT/DELETE requests to `/shelf-locations` are rejected ([MODINVSTOR-85](https://issues.folio.org/browse/MODINVSTOR-85))
* Adds a gin index on `holdingsRecordId` for items ([MODINVSTOR-63](https://issues.folio.org/browse/MODINVSTOR-63))
* Adds a gin index on `id` for material types ([MODINVSTOR-63](https://issues.folio.org/browse/MODINVSTOR-63))

## 8.5.0 2018-03-27

* Add optional field `contributorTypeText` to `instance.contributors` ([MODINVSTOR-93](https://issues.folio.org/browse/MODINVSTOR-93))
* Adds metadata generation (dates and update user) to item, holding, material type and loan type records ([MODINVSTOR-71](https://issues.folio.org/browse/MODINVSTOR-71))
* Removing SQ warnings and improving test coverage in the new locations and location-units ([MODINVSTOR-89](https://issues.folio.org/browse/MODINVSTOR-89))
* Stops hiding database related errors when creating instances or holdings ([MODINVSTOR-72](https://issues.folio.org/browse/MODINVSTOR-72))
* Introduces multi-level (institution, campus, library and location) location model (MODINVSTOR-70, [MODINVSTOR-91](https://issues.folio.org/browse/MODINVSTOR-91))
* Extend the `offset` and `limit` paging query parameters to allow maximum integer values ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `instance-storage` 4.3 interface (MODINVSTOR-93, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `item-storage` 5.2 interface (MODINVSTOR-71, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `holdings-storage` 1.2 interface (MODINVSTOR-71, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `loan-types` 2.2 interface (MODINVSTOR-71, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `material-types` 2.2 interface (MODINVSTOR-71, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `locations` 1.1 interface (MODINVSTOR-70, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `location-units` 1.1 interface (MODINVSTOR-70, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `contributor-name-types` 1.2 interface (MODINVSTOR-66, [MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `contributor-types` 1.1 interface ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `shelf-locations` 1.1 interface ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `instance-types` 1.1 interface ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `identifier-types` 1.1 interface ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `instance-formats` 1.1 interface ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `classification-types` 1.1 interface ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))
* Provides `platforms` 1.1 interface ([MODINVSTOR-62](https://issues.folio.org/browse/MODINVSTOR-62))

## 8.0.0 2018-03-07

* Upgrades RAML Module Builder to version 19.0.0 (RMB-130, [MODINVSTOR-65](https://issues.folio.org/browse/MODINVSTOR-65))
* Uses generated sources for generated code (RMB-130, [MODINVSTOR-65](https://issues.folio.org/browse/MODINVSTOR-65))
* Uses `PgExceptionUtil` from RAML Module Builder to handle database exceptions (May change some server error response messages, [MODINVSTOR-52](https://issues.folio.org/browse/MODINVSTOR-52))
* Uses embedded PostgreSQL 10.1 during tests ([MODINVSTOR-65](https://issues.folio.org/browse/MODINVSTOR-65))

## 7.2.2 2018-03-02

* Adds the following GIN indexes for `instances` ([MODINVSTOR-48](https://issues.folio.org/browse/MODINVSTOR-48)):
  - `contributors`
  - `identifiers`
  - `instanceTypeId`
  - `languages`
  - `classifications`
  - `subjects`
* Adds the following b-tree indexes for `instances` ([MODINVSTOR-48](https://issues.folio.org/browse/MODINVSTOR-48)):
  - `contributors`
  - `publication`
* Uses CQL to get instance, item or holding by ID (in order to use available index, [MODINVSTOR-48](https://issues.folio.org/browse/MODINVSTOR-48))
* Introduces searching for instances with an item with a given barcode (e.g. `item.barcode==683029605940`, [MODINVSTOR-49](https://issues.folio.org/browse/MODINVSTOR-49))
* Searching (which includes a barcode) includes instances that do not have a holding or a item ([MODINVSTOR-55](https://issues.folio.org/browse/MODINVSTOR-55))
* Searching containing barcode (or other item properties) is currently only supported on small sets of records

## 7.1.0 2018-01-08

* Adds metadata generation (dates and update user) to instance records ([MODINVSTOR-37](https://issues.folio.org/browse/MODINVSTOR-37))
* Provides `instance-storage` 4.1 interface ([MODINVSTOR-37](https://issues.folio.org/browse/MODINVSTOR-37))

## 7.0.0 2018-01-03

* Require `holdingsRecordId` from `items` ([MODINVSTOR-44](https://issues.folio.org/browse/MODINVSTOR-44))
* Removes `creators` from `instances` ([MODINVSTOR-33](https://issues.folio.org/browse/MODINVSTOR-33))
* Removes `title` from `items` ([MODINVSTOR-29](https://issues.folio.org/browse/MODINVSTOR-29))
* Removes `instanceId` from `items` ([MODINVSTOR-29](https://issues.folio.org/browse/MODINVSTOR-29))
* Removes `permanentLocationId` from `items` ([MODINVSTOR-29](https://issues.folio.org/browse/MODINVSTOR-29))
* Adds `contributorNameTypeId` to `contributors` in `instances` ([MODINVSTOR-33](https://issues.folio.org/browse/MODINVSTOR-33))
* No longer provides `creator-types` 1.0 interface ([MODINVSTOR-33](https://issues.folio.org/browse/MODINVSTOR-33))
* Provides `contributor-name-types` 1.0 interface ([MODINVSTOR-33](https://issues.folio.org/browse/MODINVSTOR-33))
* Provides `instance-storage` 4.0 interface ([MODINVSTOR-33](https://issues.folio.org/browse/MODINVSTOR-33))
* Provides `item-storage` 5.0 interface ([MODINVSTOR-29](https://issues.folio.org/browse/MODINVSTOR-29))

## 6.0.0 2017-12-20

* Adds optional property `electronicLocation` to `holdingsRecord`. Makes permanentLocationId optional (MODINVSTOR-35, [UIIN-15](https://issues.folio.org/browse/UIIN-15))
* Adds optional properties `enumeration`, `chronology`, `pieceIdentifiers`, `numberOfPieces`, `notes` to `item` ([MODINVSTOR-34](https://issues.folio.org/browse/MODINVSTOR-34))
* `title` is now optional for an `item` ([MODINVSTOR-31](https://issues.folio.org/browse/MODINVSTOR-31))
* Provides `holdings-storage` 1.0 interface ([MODINVSTOR-25](https://issues.folio.org/browse/MODINVSTOR-25))
* Adds `holdingsRecordId` to item ([MODINVSTOR-25](https://issues.folio.org/browse/MODINVSTOR-25))
* Provides `instance-storage` 3.0 interface ([MODINVSTOR-17](https://issues.folio.org/browse/MODINVSTOR-17))
* Instances: Add controlled vocabularies, providing following interfaces: ([MODINVSTOR-17](https://issues.folio.org/browse/MODINVSTOR-17))
*   `identifier-types` 1.0
*   `contributor-types` 1.0
*   `creator-types` 1.0
*   `instance-formats` 1.0
*   `instance-types` 1.0
*   `classification-types` 1.0
* Instances: `identifiers` property refactored, multiple changes ([MODINVSTOR-17](https://issues.folio.org/browse/MODINVSTOR-17))
* Instances: Fields added: source (mandatory), alternativeTitles, creators (mandatory),
*  contributors, subjects, classifications, publication, urls,
*  instanceTypeId (mandatory) instanceFormatId, physicalDescriptions,
*  languages, notes. ([MODINVSTOR-17](https://issues.folio.org/browse/MODINVSTOR-17))
* Removes `location` property from Item record, and store a UUID for a (permanent and temporary) location record instead
* Implement `/shelf-locations` endpoint for CRUD of location records
* Provides `item-storage` 4.1 interface ([MODINVSTOR-31](https://issues.folio.org/browse/MODINVSTOR-31))
* Provides `shelf-locations` 1.0 interface
* Upgrades to RAML Module Builder v16.0.3 (MODINVSTOR-20, MODINVSTOR-18, MODINVSTOR-38, [MODINVSTOR-43](https://issues.folio.org/browse/MODINVSTOR-43))
* Fixes sorting by title for instances ([MODINVSTOR-43](https://issues.folio.org/browse/MODINVSTOR-43))
* Generates Descriptors at build time from templates in ./descriptors ([FOLIO-701](https://issues.folio.org/browse/FOLIO-701))
* Adds mod- prefix to names of the built artifacts ([FOLIO-813](https://issues.folio.org/browse/FOLIO-813))

## 5.1.0 2017-08-03

* [MODINVSTOR-12](https://issues.folio.org/browse/MODINVSTOR-12) Searching and sorting on material type properties (e.g. materialType.name)
* Upgrade RAML Module Builder to version 13.0.2
* Include implementation version in `id` in Module Descriptor
* Provides item-storage interface version 3.1 (notes additional CQL indexes in comments)

## 5.0.0 2017-06-07

* Disallow additional properties in item requests
* Disallow additional properties in instance requests
* Disallow additional properties in loan type requests
* Disallow additional properties in material type requests
* Items do not require relating to an instance (instanceId is optional)
* Items do not require a barcode
* Items require a title (between 1 and 255 characters)
* Items require a reference to a material type
* Items require a reference to a permanent loan type
* Provides item-storage interface version 3.0
* Provides instance-storage interface version 2.0
* Provides material-types interface version 2.0
* Provides loan-types interface version 2.0
* Upgrade to RAML Module Builder 12.1.2

## 4.2.0 2017-05-08

* Provide permanent and temporary loan type associations for items

## 4.1.0 2017-05-01

* Provide loan type controlled vocabulary (see [METADATA-59](https://issues.folio.org/browse/METADATA-59))

## 4.0.0 2017-04-25

* Use UUID to reference material types in inventory storage module

## 3.0.0 2017-04-04

* Required permissions for requests

## 2.0.0 2017-04-03

* Material type controlled vocabulary
