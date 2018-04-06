
## 10.0.0 Unreleased

* Rename 'parking' to 'details' in locations (MODINVSTOR-96). BREAKING CHANGE!
* Bump the version of the 'locations' interface to 2.0
* Use proper foreign keys in LocationUnits (MODINVSTOR-92)

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
