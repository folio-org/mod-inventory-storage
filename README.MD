
Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Goal

FOLIO compatible module suite to provide Knowledge Base and Metadata capabilities.

# Included Modules

## Inventory

Provides basic physical item inventory management, currently limited to basic representations of local instances and items, which can only be created via a MODS import. Written in Groovy and uses Gradle as its build system.

In the inventory directory.

## Inventory Storage

Provides Postgres based storage to complement the inventory module. Written in Java, uses the raml-module-builder and uses Maven as its build system.

In the inventory-storage directory.

## Knowledge Base

**Soon to be deprecated**. Provides a basic representation of an instance. Written in Groovy and uses Gradle as its build system.

In the knowledge-base-core directory.

## Catalogue

**Soon to be deprecated** Provides a basic representation of a physical item (with limited Dublin Core annotation). Written in Groovy and uses Gradle as its build system.

In the catalogue-core directory.

# Further Documentation

This readme is intended to get these modules up and running. For further information on what they do, see the [guide](doc/guide.md).

# Prerequisites

- Java 8 JDK
- Apache Groovy 2.4.8
- Gradle 3.3
- Maven 3.3.9
- [RAML Module Builder](https://github.com/folio-org/raml-module-builder) 2.3.3
- Postgres 9.6.1 (running and listening on localhost:5432, logged in user must have admin rights)
- MongoDB 3.2.9 (running and listening on localhost:27017)
- Docker 1.12
- Node 6.4.0 (for the demo UI)
- NPM 3.10.3 (for the demo UI and documentation generation)
- [SDKMan](http://sdkman.io/) (recommended for installing Gradle and Groovy)

# Preparation

If sdkman is installed, run `source ./setup-environment.sh` to setup the shell with the appropriate versions of Gradle and Groovy.

Run the `setup-test-db.sh` script in the inventory-storage directory to setup Postgres with a database to be used in tests. 
This is only required to run tests against an external Postgres instance, the default is to use an embedded Postgres instance.

# Building the Modules

## Inventory, Catalogue and Knowledge Base

run `gradle build` from the root directory.

## Inventory Storage

run `mvn install` from the inventory-storage directory.

Must have the RAML Module Builder installed (see https://github.com/folio-org/raml-module-builder for instructions) beforehand.

To run the tests against both embedded and external databases, run `./build.sh` from the inventory-storage directory.

# Running the Modules

## Preparation

## Running Okapi

Make sure that [Okapi](https://github.com/folio-org/okapi) is running on its default port of 9130 (see the [guide](https://github.com/folio-org/okapi/blob/master/doc/guide.md) for instructions).

A script for building and running Okapi is provided. Run `../mod-metadata/start-okapi.sh` from the root of the Okapi source.

As this runs Okapi using Postgres storage, some database preparation is required. This can be achieved by running `./create-okapi-database.sh` from the root of this repository.

### When Using the Modules as Docker Containers

For the modules to communicate via Okapi Proxy, when running in Docker containers, the address for Okapi Proxy needs to be routable from inside the container.

This can be achieved by passing a parameter to the script used to start Okapi, as follows `../mod-metadata/start-okapi.sh http://192.168.X.X:9130`

Where 192.168.X.X is a routable IP address for the host from container instances and both repository clones are at the same directory level on your machine.

#### Finding a Routable Address

Finding the appropriate IP address can be OS and Docker implementation dependent, so this is a very early guide rather than thorough treatment of the topic.

If these methods don't work for you, please do get in touch, so this section can be improved.

On Linux, `ifconfig docker0 | grep 'inet addr:'` should give output similar to `inet addr:192.168.X.X  Bcast:0.0.0.0  Mask:255.255.0.0`, , the first IP address is usually routable from within containers.

On Mac OS X (using Docker Native), `ifconfig en0 | grep 'inet '` should give output similar to `inet 192.168.X.X netmask 0xffffff00 broadcast 192.168.X.X`, the first IP address is usually routable from within containers.

## Demo

The best way to run all of the modules in this suite together.

Okapi needs to be running (see above) prior to running these scripts. The demo UI requires Node and NPM to be installed, in addition to the prerequisites for running the modules themselves.

To start the modules run the `./start-demo.sh` script from the root directory, which performs the following steps:

* Creates a demo tenant (with ID demo_tenant, see Appendix 1 for more info)
* Registers the modules with Okapi (see Appendix 1 for more info)
* Starts each module (Inventory and Inventory Storage run natively, Catalogue and Knowledge Base run in Docker containers)
* Creates some sample data
* Starts the demo UI

To stop the modules run `./stop-demo.sh` from the root directory, which performs the following steps:

### Demo User Interface (for the catalogue and knowledge base modules)

There is a basic demo user interface provided, which allows for searching instances by partial title.

It can be accessed via `http://localhost:9501` in a browser.

## Natively

Run `./start.sh` from the root directory.

To stop them, run `./stop.sh` from the root directory.

## With Docker (only for Knowledge Base and Catalogue modules)

Run `./start-docker.sh` from the root directory.

To stop them, run `./stop-docker.sh` from the root directory.

# Using the Modules

These modules provide HTTP based APIs rather than any UI themselves.

As FOLIO is a multi-tenant system, many of the requests made to these modules are tenant aware (via the X-Okapi-Tenant header), which means most requests need to be made via a system which understands these headers (e.g. another module or UI built using [Stripes](https://github.com/folio-org/stripes-core)).

Therefore, it is suggested that requests to the API are made via tools such as curl or [postman](https://www.getpostman.com/), or via a browser plugin for adding headers, such as [Requestly](https://chrome.google.com/webstore/detail/requestly/mdnleldcmiljblolnjhpnblkcekpdkpa).

## Okapi Root Addresses

It is recommended that the modules are located via Okapi. Access via Okapi requires passing the X-Okapi-Tenant header (see the Okapi guide above for details).

|Module|Root Address|
|------|------------|
|Inventory| http://localhost:9130/inventory |
|Inventory Storage| http://localhost:9130/item-storage/ and http://localhost:9130/instance-storage/ |
|Knowledge Base| http://localhost:9130/knowledge-base |
|Catalogue| http://localhost:9130/catalogue |

## Direct Root Addresses

For diagnostic purposes, it can sometimes be useful to connect directly to module APIs. Below is a list of root addresses for these modules.

Whilst some requests may currently not require tenant information (it is likely that if not all, the vast majority will do in the future), it is recommended that all requests provide an X-Okapi-Tenant header.

|Module|Root Address|
|------|------------|
|Inventory| http://localhost:9403/inventory |
|Inventory Storage| http://localhost:9407/item-storage/ and http://localhost:9407/instance-storage/ |
|Knowledge Base| http://localhost:9401/knowledge-base |
|Catalogue| http://localhost:9402/catalogue |

# Running the Tests

The best way to run the tests is to run `./full-test.sh`

Okapi needs to be already running, without changing the address it uses (unlike when running Okapi for running the modules in Docker containers).

The modules should *not* be started before running the tests, as this can cause confusing results. The tests start an instance themselves during setup.

The script performs the following steps:

1. Creates a test tenant (with ID test_tenant)
2. Registers the modules with Okapi (they run on a different port than usual during the tests)
3. Builds and runs the tests (API tests run both directly and via Okapi Proxy - except for Inventory Storage)
4. Deregister the modules from Okapi
5. Deletes the test tenant

Due to the clean up output (steps 4 and 5) being quite verbose (will be improved in future versions), it is necessary to scroll up in the output to see the test results.

If Okapi is running with a different okapiurl property set (like can be done with the `start-okapi.sh` script), then the same URL needs to be provided to this script for the tests to pass.

### Which Gradle Targets are Executed

Step 3 above executes the `test` (which includes running the API tests direct to the modules) and `testApiViaOkapi` targets to execute the tests.

Due to the modules running for the tests being bound to different ports, it is necessary to use slightly different registration steps than for when running the modules themselves (via parameterised scripts). For this reason, it is not recommended to run that target outside of the `full-test.sh` script unless willing to perform the registration steps in another manner.

# Operating System Support

Most of the development for these modules, thus far, has been performed on OS X, with some on Ubuntu. Feedback for these, and particularly other operating systems is very welcome.

# Additional Information

The guide and other [documentation](doc) for this module.

Other [modules](http://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)

# Appendix 1 - Okapi Registration

As an alternative to the demo scripts, it is possible to perform Okapi tenant and module setup via a set of smaller scripts, see below for more information on these steps.

### Preparing a Tenant

Run `./create-tenant.sh` from the root directory to set up a test tenant (named our).

The output should be something similar to:

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/tenants/demo_tenant
Content-Length: 81

{
  "id" : "demo_tenant",
  "name" : "A Library",
  "description" : "A library"
}
```

The tenant can be deleted by running `./delete-tenant.sh`

### Registering the Modules With Okapi

Run `./register.sh` from the root directory.

The last output should be something similar to:

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/discovery/modules/knowledge-base-core/localhost-9401
Content-Length: 104

{
  "instId" : "localhost-9401",
  "srvcId" : "knowledge-base-core",
  "url" : "http://localhost:9401"
}
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/modules/knowledge-base-core
Content-Length: 375

{
  "id" : "knowledge-base-core",
  "name" : "Core Knowledge Base Module",
  "provides" : [ {
    "id" : "knowledge-base-core",
    "version" : "0.1"
  } ],
  "routingEntries" : [ {
    "methods" : [ "GET", "POST" ],
    "path" : "/knowledge-base",
    "level" : "30",
    "type" : "request-response",
    "permissionsRequired" : null,
    "permissionsDesired" : null
  } ]
}
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 34

{
  "id" : "knowledge-base-core"
}
```
