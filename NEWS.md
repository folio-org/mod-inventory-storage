## 5.1.2 Unreleased

* MODINVSTOR-13 materialType query fails

## 5.1.1 Unreleased

* UIIT-33 Sorting on material-type should be by MT name, not ID (include upgrade of RMB)
* Include implementation version in `id` in Module Descriptor

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
