## 18.2.3 2020-01-10

* Upgrades to RAML Module Builder 29.2.2 to upgrade indexes on module upgrade ([MODINVSTOR-430](https://issues.folio.org/browse/MODINVSTOR-430), [RMB-550](https://issues.folio.org/browse/RMB-550))

## 18.2.2 2020-01-09

* Upgrades to RAML Module Builder 29.2.1 (MODINVSTOR-429, RMB-549)

## 18.2.1 2019-12-23

* Improve performance of searching by keyword index (MODINVSTOR-424)
* Truncate b-tree indexes to avoid buffer or index size problems (MODINVSTOR-390, MODINVSTOR-395, MODINVSTOR-379, MODINVSTOR-139)
* Upgrades to RAML Module Builder 29.1.5 (MODINVSTOR-418)

## 18.2.0 2019-12-20

* Increase maximum number of digits for human readable IDs (HRID) (MODINVSTOR-410, MODINVSTOR-411, MODINVSTOR-412)
* Include HRID in sample records for instances, items and holdings (MODINVSTOR-397)
* Improve performance of searching by effective location (MODINVSTOR-407, MODINVSTOR-409)

## 18.1.0 2019-12-06

* Upgrades RAML Module Builder (RMB) to version [29.1.0](https://github.com/folio-org/raml-module-builder/blob/v29.1.0/NEWS.md) (was 28.1.0) ([MODINVSTOR-403](https://issues.folio.org/browse/MODINVSTOR-403))

Most notable RAML Module Builder changes:
* Estimate hit counts [(RMB-506](https://issues.folio.org/browse/RMB-506))
* Bugfix that break clients that do not comply with the interface spec: POST /\_/tenant requires JSON body with module_to ([RMB-510](https://issues.folio.org/browse/RMB-510))

## 18.0.0 2019-11-29

* Generates `HRID`s for `instance`, `holdings` and `item` records (MODINVSTOR-363, MODINVSTOR-373, MODINVSTOR-374, MODINVSTOR-375)
* Derives `effective location` for `item` records (MODINVSTOR-348)
* Derives `effective call number, suffix and prefix` for `item` records (MODINVSTOR-357, MODINVSTOR-358, MODINVSTOR-360, MODINVSTOR-391)
* Introduces `keyword` CQL index for `instance` records (MODINVSTOR-349)
* Introduces `last check in date` property  for `item` records (MODINVSTOR-386)
* Introduces `preceding-succeeding` instance relationship (MODINVSTOR-343)
* Introduces `Uniform title` alternative title type  (MODINVSTOR-350)
* Introduces `LC (local)` and `SUDOC` classification types  (MODINVSTOR-351)
* Sample `instance` records now use  `FOLIO` as the `source`  property (MODINVSTOR-337)
* Makes `permanent location` a required property for `holdings` (MODINVSTOR-364)
* Makes `code` a required properties for `institution`, `campus` and `library` location units (MODINVSTOR-315)
* Applies stricter validation on UUID properties in `instance` and `holdings` records (MODINVSTOR-297, MODINVSTOR-370)
* Introduces synchronous batch APIs for `items`, `holdings` and `instances` (MODINVSTOR-353)
* Upgrades RAML Module Builder to version 27.0.0 (MODINVSTOR-368, MODINVSTOR-383, MODINVSTOR-385)
* Generates  `metadata` property for instances created using batch API (MODINVSTOR-387)
* Fixes bug with `item status date` being changed even when status has not changed (MODINVSTOR-376)
* Fixes bug with `instance status date` not being set when status changes (MODINVSTOR-367)
* Changes container memory management (MODINVSTOR-396, FOLIO-2358)
* Provides `item-storage 7.8`
* Provides `holdings-storage 4.0`
* Provides `location-units 2.0`
* Provides `hrid-settings-storage 1.0`
* Provides `item-storage-batch-sync 0.1`
* Provides `holdings-storage-batch-sync 0.1`
* Provides `instance-storage-batch-sync 0.1`

## 17.0.0 2019-09-09

* Adds tags to `items records` (MODINVSTOR-322)
* Adds tags to `holdings records` (MODINVSTOR-324)
* Adds tags to `instances` (MODINVSTOR-323)
* Adds nature of content terms to `instances` (MODINVSTOR-327)
* Introduces `item damaged statuses` (MODINVSTOR-286)
* Introduces preview `instance storage batch` API (MODINVSTOR-291)
* Limits number of database connections when processing instance batches (MODINVSTOR-330)
* Disallows deletion of in use `instance types` (MODINVSTOR-301)
* Disallows creation of `holdings type` with existing name (MODINVSTOR-318)
* Fixes failures when combining CQL array index and other indexes (MODINVSTOR-319)
* Use sub-queries rather than views for cross-record searching (MODINVSTOR-339, MODINVSTOR-347)
* Provides `item-storage` interface version 7.5 (MODINVSTOR-322)
* Provides `holdings-storage` interface version 3.1 (MODINVSTOR-324)
* Provides `instance-storage` interface version 7.2 (MODINVSTOR-323, MODINVSTOR-327)
* Provides `instance-storage-batch` interface version 0.2 (MODINVSTOR-291)
* Provides `item-damaged-statuses` interface version 1.0 (MODINVSTOR-286)
* Upgrades RAML Module Builder to version 27.0.0 (MODINVSTOR-342)

## 16.0.0 2019-07-23

* Provides `instance-note-types` interface version 1.0 (MODINVSTOR-300)
* Provides `instance-storage` interface version 7.0 (MODINVSTOR-312,MODINVSTOR-297)
* Provides `nature-of-content-terms` interface version 1.0 (MODINVSTOR-309)
* Provides `item-storage` interface version 7.4 (MODINVSTOR-310)
* Provides `identifier-types` interface version 1.2 (MODINVSTOR-305)
* Provides `classification-types` interface version 1.2 (MODINVSTOR-306)
* Provides `modes-of-issuance` interface version 1.1 (MODINVSTOR-307)
* Changes structure of instance.notes (MODINVSTOR-312)
* Adds date and source fields to circulation notes (MODINVSTOR-310)
* Validates UUID properties with pattern (MODINVSTOR-297)
* Adds property `source` to identifier type, classification type, mode of issuance (MODINVSTOR-305, MODINVSTOR-306, MODINVSTOR-307)
* Sets array modifiers for contributors, identifiers (MODINVSTOR-311)
* Populate identifiers reference table with more identifiers (MODINVSTOR-313)
* Aligns barcode index (on `item`) with the SQL generated
* Upgrades RAML Module Builder to version 26.2.2 (MODINVSTOR-285)
* Improves test coverage, error logging, sample data.

## 15.5.1 2019-06-09

* Performance improvement (MODINVSTOR-255)
* Bug fix (MODINVSTOR-289)

## 15.4.0 2019-05-08

* Upgrades RAML Module Builder to version 24.0.0 and aligns MODINVSTOR-278
* Adds reference data for locations, location units, service points MODINVSTOR 279

## 15.3.1 2019-03-23

* Align sample data cross-module: Item statuses to match loan samples in circulation

## 15.3.0 2019-03-15

* Remove branch tag for mod-users submodule

## 15.2.0 2019-03-15

* Provides service point users sample data (MODINVSTOR-270)
* Loads sample data with `loadSample` (MODINVSTOR-264)
* Performance optimization, search by barcode (MODINVSTOR-269)
* Makes sample service points available for pickup (MODINVSTOR-268)

## 15.1.0 2019-02-19

* Provides `item-storage` interface version 7.3 (MODINVSTOR-252)
* Provides `service-points` interface version 3.2 (MODINVSTOR-251)
* Adds `holdShelfExpiryPeriod` to `servicepoint` schema (MODINVSTOR-251)
* Adds `status.date` to `item` schema (MODINVSTOR-252)
* Improves performance:
*   Fixes slow identifier search (MODINVSTOR-266)
*   Use sort or search index depending on result size (MODINVSTOR-215)
* Adds update of reference data on module upgrade (MODINVSTOR-263)
* Bugfix: No longer supports <> relation for ID properties (MODINVSTOR-267)

## 15.0.0 2019-02-01

* Provides `service-points` interface version 3.1 (MODINVSTOR-235)
* Provides `item-storage` interface version 7.2 (MODINVSTOR-249)
* Adds `staffSlips` to `servicepoint` schema (MODINVSTOR-235)
* Adds property `ServicePoint.printByDefault` (MODINVSTOR-235)
* Adds `circulationNotes` to `item` schema (MODINVSTOR-249)
* Adds sequences for generating human readable identifiers (MODINVSTOR-170)
* Improves performance for items by barcode (MODINVSTOR-247)
* Improves performance for items queried by ID (MODINVSTOR-248)
* Improves performance for PUT of `Instance` (MODINVSTOR-254)
* Adds property `Item.purchaseOrderLineIdentifier` (MODINVSTOR-245)
* Miscellaneous bug-fixes (MODINVSTOR-253, MODINVSTOR-243)


## 14.0.0 2018-11-30

* Provides `item-storage` interface version 7.0 (MODINVSTOR-205)
* Provides `holdings-storage` interface version 3.0 (MODINVSTOR-209)
* Provides `instance-storage` interface version 6.0 (MODINVNSTOR-232, MODINVSTOR-200)
* Renames `item` property `pieceIdentifiers` to `copyNumbers` (MODINVSTOR-205)
* Removes property `electronicLocation` from holdingsRecord schema (MODINVSTOR-227)
* Removes obsolete reference end-point `platforms` (MODINVSTOR-226)
* Changes structure of property `alternativeTitles` (MODINVSTOR-200)
* Changes/renames property statisticalCodes in Instance (MODINVSTOR-241)
* Changes structure of `holdingsStatements` (MODINVSTOR-228)
* Disallows additional properties in `holdingsRecord` (MODINVSTOR-229)
* Changes structure of `notes` in `item` (MODINVSTOR-154)
* Adds 19 new properties to `item` (MODINVSTOR-154)

## 13.2.0 2018-11-24

* Adds an in transit destination to a item (MODINVSTOR-230)
* Provides `item-storage` interface version 6.1 (MODINVSTOR-230)

## 13.1.0 2018-11-19

* Provides `holdings-storage` interface 2.1 (MODINVSTOR-153 etc)
* Provides `statistical-codes` 1.0 (MODINVSTOR-221)
* Provides `holdings-note-types` interface 1.0 (MODINVSTOR-220)
* Provides `item-note-types` interface 1.0 (MODINVSTOR-220)
* Provides `alternative-title-types` interface 1.0 (MODINVSTOR-200)
* Provides `call-number-types` interface 1.0 (MODINVSTOR-210)
* Provides `holdings-types` interface 1.0 (MODINVSTOR-210)
* Provides `ill-policies` interface 1.0 (MODINVSTOR-210)
* Extends holdingsRecord with 20+ new optional properties (MODINVSTOR-153)
* Uses full-text indexes for `instanceType` and `language` (MODINVSTOR-184, MODINVSTOR-188)

## 13.0.1 2018-10-12

* Extends locations reference data with new required properties (MODINVSTOR-177)

## 13.0.0 2018-10-10

* Provides `electronic-access-relationships` interface 1.0 (MODINVSTOR-190)
* Documents `instance` properties (MODINVSTOR-179)
* Converts to RAML 1.0 (MODINVSTOR-193)
* Add foreign keys item->holdings_record, holdings_record->instance (MODINVSTOR-135)
* Removes index on `item.title` (MODINVSTOR-135)
* Provides `instance-storage` interface 5.0 (MODINVSTOR-187)
* Provides `holdings-storage` interface 2.0 (MODINVSTOR-187)
* Provides `item-storage` interface 6.0 (MODINVSTOR-187)
* Change `instance.edition` to repeatable `editions` (MODINVSTOR-171)
* Change `instance.formatId` to repeatable `formatIds` (MODINVSTOR-195)
* Removes property `instance.urls` (MODINVSTOR-180)
* Removes property `instance.catalogingLevelId` (MODINVSTOR-186)
* Removes end-point `cataloging-levels`, reference table `cataloging_level` (MODINVSTOR-186)
* Renames `instance.electronicAccess.relationship` to `relationshipId` (MODINVSTOR-191)
* Add primary service point for location property (MODINVSTOR-177)
* Reversed the relationship of ServicePoint to Location (MODINVSTOR-177)

## 12.8.2 2018-09-16

* Enable UUID syntax check for POST instance and POST holding (MODINVSTOR-172)
* Uses RMB 19.4.4, fixing fulltext bug with trailing spaces and * (MODINVSTOR-175)

## 12.8.1 2018-09-13

* Uses RMB 19.4.3, which uses the 'simple' dictionary for fulltext,
  getting around the stopword problem. (MODINVSTOR-168)

## 12.8.0 2018-09-13

* Adds more properties to instance (MODINVSTOR-152)
* Provides `instance-storage` interface 4.6 (MODINVSTOR-152)
* Provides `statistical-code-types` interface 1.0 (MODINVSTOR-152)
* Provides `cataloging-levels` interface 1.0 (MODINVSTOR-152)
* Provides `instance-statuses` interface 1.0 (MODINVSTOR-152)
* Provides `modes-of-issuance` interface 1.0 (MODINVSTOR-152)

## 12.7.0 2018-09-12

* Uses full text indexing for instance `title` searching (MODINVSTOR-159)
* Upgrades to RAML Module Builder 19.4.2 (MODINVSTOR-159)
* Fixes inability to specify page `limit` higher than 100 (MODINVSTOR-164)

## 12.6.0 2018-09-10

* Adds instance relationship storage (MODINVSTOR-147)
* Provides `instance-storage` interface 4.5 (MODINVSTOR-147)
* Provides `instance-relationship-types` interface 1.0 (MODINVSTOR-147)

## 12.5.1 2018-08-15

* Fixes updating of existing MARC JSON source record (MODINVSTOR-144)

## 12.5.0 2018-08-02

* Add `service-points-users` endpoint (MODINVSTOR-132)
* Provides `service-points-users` interface 1.0 (MODINVSTOR-132)

## 12.4.0 2018-07-26

* Item `status` now defaults to 'Available' (MODINVSTOR-137)

## 12.3.0 2018-07-24

* Add MARC JSON source record endpoint (MODINVSTOR-26)
* Provides `instance-storage` interface 4.4 (MODINVSTOR-26)

## 12.2.0 2018-07-16

* Added locationIds array to service point (MODINVSTOR-127)
* Provides `service-points` interface 2.1 (MODINVSTOR-127)

## 12.1.0 2018-07-10

* Upgrade RAML Module Builder to 19.1.5 (MODINVSTOR-128)

## 12.0.0 2018-07-06

* Upgrade RAML Module Builder to 19.1.3 (MODINVSTOR-126)
* Provides v2.0 of instance-types, instance-formats, contributor-types (MODINVSTOR-115,-116,-123)
* Add `source`, `code`, `metadata` to instance type (MODINVSTOR-115)
* Add `source`, `code`, `metadata` to instance format (MODINVSTOR-116)
* Add `source`, `code`, `metadata` to contributor type (MODINVSTOR-123)

## 11.1.0 2018-06-25

* Adds `temporaryLocationId` property to holdings records (MODINVSTOR-97)
* Adds `permanentLocationId` property to item records (MODINVSTOR-97)
* Provides `item-storage` interface 5.3 (MODINVSTOR-97)
* Provides `holdings-storage` interface 1.3 (MODINVSTOR-97)

## 11.0.0 2018-06-21

* Removes `feeFineOwner` property from `service-points` record (MODINVSTOR-114)
* Fix proxy registration for GET individual `service-point` in module descriptor (MODINVSTOR-110)
* Provides `service-points` interface 2.0 (MODINVSTOR-114)
* Change item `id` index to be case insensitive and to remove accents to improve CQL search performance (MODINVSTOR-121)

## 10.2.0 2018-04-25

* Add 'description' and 'discoveryDisplayName' to locations (MODINVSTOR-113)
* Provides `locations` interface to 2.1 (MODINVSTOR-113)

## 10.1.0 2018-04-25

* Add /service-points API (MODINVSTOR-95)
* Rename `parking` property to `details` in locations (MODINVSTOR-96)
* Foreign keys in items and holdings for locations (MODINVSTOR-107, MODINVSTOR-92)
* Rename 'parking' to 'details' in locations (MODINVSTOR-96)
* Use proper foreign keys in location units (MODINVSTOR-92)
* Add metadata to locations and location units (MODINVSTOR-101, MODINVSTOR-102, MODINVSTOR-103, MODINVSTOR-104)
* Provides `locations` interface to 2.0 (MODINVSTOR-96, MODINVSTOR-104)
* Provides `location-units` interface to 1.1 (MODINVSTOR-101, MODINVSTOR-102, MODINVSTOR-103)
* Provides `service-points` interface 1.0 (MODINVSTOR-95)

## 9.0.1 2018-04-04

* GET requests to `/shelf-locations` proxy records from new location model (MODINVSTOR-85)
* POST/PUT/DELETE requests to `/shelf-locations` are rejected (MODINVSTOR-85)
* Adds a gin index on `holdingsRecordId` for items (MODINVSTOR-63)
* Adds a gin index on `id` for material types (MODINVSTOR-63)

## 8.5.0 2018-03-27

* Add optional field `contributorTypeText` to `instance.contributors` (MODINVSTOR-93)
* Adds metadata generation (dates and update user) to item, holding, material type and loan type records (MODINVSTOR-71)
* Removing SQ warnings and improving test coverage in the new locations and location-units (MODINVSTOR-89)
* Stops hiding database related errors when creating instances or holdings (MODINVSTOR-72)
* Introduces multi-level (institution, campus, library and location) location model (MODINVSTOR-70, MODINVSTOR-91)
* Extend the `offset` and `limit` paging query parameters to allow maximum integer values (MODINVSTOR-62)
* Provides `instance-storage` 4.3 interface (MODINVSTOR-93, MODINVSTOR-62)
* Provides `item-storage` 5.2 interface (MODINVSTOR-71, MODINVSTOR-62)
* Provides `holdings-storage` 1.2 interface (MODINVSTOR-71, MODINVSTOR-62)
* Provides `loan-types` 2.2 interface (MODINVSTOR-71, MODINVSTOR-62)
* Provides `material-types` 2.2 interface (MODINVSTOR-71, MODINVSTOR-62)
* Provides `locations` 1.1 interface (MODINVSTOR-70, MODINVSTOR-62)
* Provides `location-units` 1.1 interface (MODINVSTOR-70, MODINVSTOR-62)
* Provides `contributor-name-types` 1.2 interface (MODINVSTOR-66, MODINVSTOR-62)
* Provides `contributor-types` 1.1 interface (MODINVSTOR-62)
* Provides `shelf-locations` 1.1 interface (MODINVSTOR-62)
* Provides `instance-types` 1.1 interface (MODINVSTOR-62)
* Provides `identifier-types` 1.1 interface (MODINVSTOR-62)
* Provides `instance-formats` 1.1 interface (MODINVSTOR-62)
* Provides `classification-types` 1.1 interface (MODINVSTOR-62)
* Provides `platforms` 1.1 interface (MODINVSTOR-62)

## 8.0.0 2018-03-07

* Upgrades RAML Module Builder to version 19.0.0 (RMB-130, MODINVSTOR-65)
* Uses generated sources for generated code (RMB-130, MODINVSTOR-65)
* Uses `PgExceptionUtil` from RAML Module Builder to handle database exceptions (May change some server error response messages, MODINVSTOR-52)
* Uses embedded PostgreSQL 10.1 during tests (MODINVSTOR-65)

## 7.2.2 2018-03-02

* Adds the following GIN indexes for `instances` (MODINVSTOR-48):
  - `contributors`
  - `identifiers`
  - `instanceTypeId`
  - `languages`
  - `classifications`
  - `subjects`
* Adds the following b-tree indexes for `instances` (MODINVSTOR-48):
  - `contributors`
  - `publication`
* Uses CQL to get instance, item or holding by ID (in order to use available index, MODINVSTOR-48)
* Introduces searching for instances with an item with a given barcode (e.g. `item.barcode==683029605940`, MODINVSTOR-49)
* Searching (which includes a barcode) includes instances that do not have a holding or a item (MODINVSTOR-55)
* Searching containing barcode (or other item properties) is currently only supported on small sets of records

## 7.1.0 2018-01-08

* Adds metadata generation (dates and update user) to instance records (MODINVSTOR-37)
* Provides `instance-storage` 4.1 interface (MODINVSTOR-37)

## 7.0.0 2018-01-03

* Require `holdingsRecordId` from `items` (MODINVSTOR-44)
* Removes `creators` from `instances` (MODINVSTOR-33)
* Removes `title` from `items` (MODINVSTOR-29)
* Removes `instanceId` from `items` (MODINVSTOR-29)
* Removes `permanentLocationId` from `items` (MODINVSTOR-29)
* Adds `contributorNameTypeId` to `contributors` in `instances` (MODINVSTOR-33)
* No longer provides `creator-types` 1.0 interface (MODINVSTOR-33)
* Provides `contributor-name-types` 1.0 interface (MODINVSTOR-33)
* Provides `instance-storage` 4.0 interface (MODINVSTOR-33)
* Provides `item-storage` 5.0 interface (MODINVSTOR-29)

## 6.0.0 2017-12-20

* Adds optional property `electronicLocation` to `holdingsRecord`. Makes permanentLocationId optional (MODINVSTOR-35, UIIN-15)
* Adds optional properties `enumeration`, `chronology`, `pieceIdentifiers`, `numberOfPieces`, `notes` to `item` (MODINVSTOR-34)
* `title` is now optional for an `item` (MODINVSTOR-31)
* Provides `holdings-storage` 1.0 interface (MODINVSTOR-25)
* Adds `holdingsRecordId` to item (MODINVSTOR-25)
* Provides `instance-storage` 3.0 interface (MODINVSTOR-17)
* Instances: Add controlled vocabularies, providing following interfaces: (MODINVSTOR-17)
*   `identifier-types` 1.0
*   `contributor-types` 1.0
*   `creator-types` 1.0
*   `instance-formats` 1.0
*   `instance-types` 1.0
*   `classification-types` 1.0
* Instances: `identifiers` property refactored, multiple changes (MODINVSTOR-17)
* Instances: Fields added: source (mandatory), alternativeTitles, creators (mandatory),
*  contributors, subjects, classifications, publication, urls,
*  instanceTypeId (mandatory) instanceFormatId, physicalDescriptions,
*  languages, notes. (MODINVSTOR-17)
* Removes `location` property from Item record, and store a UUID for a (permanent and temporary) location record instead
* Implement `/shelf-locations` endpoint for CRUD of location records
* Provides `item-storage` 4.1 interface (MODINVSTOR-31)
* Provides `shelf-locations` 1.0 interface
* Upgrades to RAML Module Builder v16.0.3 (MODINVSTOR-20, MODINVSTOR-18, MODINVSTOR-38, MODINVSTOR-43)
* Fixes sorting by title for instances (MODINVSTOR-43)
* Generates Descriptors at build time from templates in ./descriptors (FOLIO-701)
* Adds mod- prefix to names of the built artifacts (FOLIO-813)

## 5.1.0 2017-08-03

* MODINVSTOR-12 Searching and sorting on material type properties (e.g. materialType.name)
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

* Provide loan type controlled vocabulary (see METADATA-59)

## 4.0.0 2017-04-25

* Use UUID to reference material types in inventory storage module

## 3.0.0 2017-04-04

* Required permissions for requests

## 2.0.0 2017-04-03

* Material type controlled vocabulary
