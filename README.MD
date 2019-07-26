# mod-inventory-storage

Copyright (C) 2016-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Goal

FOLIO compatible inventory storage module.

Provides PostgreSQL based storage to complement the [inventory module](http://github.com/folio-org/mod-inventory). Written in Java, using the raml-module-builder and uses Maven as its build system.

# Prerequisites

- Java 8 JDK
- Maven 3.3.9
- Postgres 9.6.1 (running and listening on localhost:5432, logged in user must have admin rights)
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

# Building

run `mvn install` from the root directory.

To run the tests against both embedded and external databases, run `./build.sh` from the root directory.

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

The module supports v1.2 of the Okapi `_tenant` interface. This version of the interface allows Okapi to pass tenant initialization parameters using the `tenantParameters` key. Currently, the only parameter supported is the `loadReference` key, which will cause the module to load reference data for the tenant if set to `true`. Here is an example of passing the parameter to the module via Okapi's `/_/proxy/tenants/<tenantId>/install` endpoint:

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

Importing/ingesting of sample data is done via Okapi, so modules must be registered prior to importing.

To load some sample data run `./import-sample-data.sh` from the root directory.

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

The guide and other [documentation](doc) for this module.

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

# Batch interface

Batch interface was introduced for processing a collection of entities in bulk. It is not transactional, each entity is processed separately, response contains combined results of processing.

###Design for batch save Instances endpoint
_Method_: POST

_Resource_: /instance-storage/batch/instances (interface "instance-storage-batch")

_Body_: collection of Instances and total number of Instances

_Returns_: collection of successfully created Instances, error messages for failure Instances, total number of created Instances:

- If at least one Instance is successfully saved - returns 201 response with saved instances ("instances" section), "errorMessages" array contains errors for failed Instances (empty if all Instances are successfully saved).

- If save for all Instances failed - returns 500 response with "errorMessages" array, explaining a reason of why Instances were processed with failures (one error message per one Instance). Instances array is empty.
