
Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Goal

FOLIO compatible inventory module.

Provides basic physical item inventory management, currently limited to basic representations of local instances and items, which can only be created via a MODS import. 

Written in Groovy and uses Gradle as its build system.

# Further Documentation

This readme is intended to get these modules up and running. For further information on what they do, see the [guide](doc/guide.md).

# Prerequisites

- Java 8 JDK
- Apache Groovy 2.4.8
- Gradle 3.3
- Node 6.4.0 (for API linting and documentation generation)
- NPM 3.10.3 (for API linting and documentation generation)
- Python 3.6.0 (for nice to have parts of deployment scripts, e.g. is used to remove environment variables from Okapi)
- [SDKMan](http://sdkman.io/) (recommended for installing Gradle and Groovy)

# Preparation

## Git Submodules

There are some common RAML definitions that are shared between FOLIO projects via Git submodules.

To initialise these please run `git submodule init && git submodule update` in the root directory.

If these are not initialised, document generation and API linting operations may fail.

More information is available on the [developer site](http://dev.folio.org/doc/setup#update-git-submodules).

## Groovy and Grails

If sdkman is installed, run `source ./setup-environment.sh` to setup the shell with the appropriate versions of Gradle and Groovy.

# Building

run `gradle build` from the root directory. 

In order to build an executable Jar (e.g. for Okapi to deploy), run `gradle fatJar`.

# Running

## Preparation

In order to activate the inventory module for a tenant, the dependencies described in the [Module Descriptor](ModuleDescriptor.json]) need to be fulfilled.

The simplest way to fulfil these is to use the [inventory storage module](http://https://github.com/folio-org/mod-inventory-storage).

## Sample Data

Importing/ingesting of sample data is done via Okapi, so modules must be deployed and activated prior to importing.

To load some sample data run `./import-sample-data.sh` from the root directory.

## Running Okapi

Make sure that [Okapi](https://github.com/folio-org/okapi) is running on its default port of 9130 (see the [guide](https://github.com/folio-org/okapi/blob/master/doc/guide.md) for instructions).

A script for building and running Okapi is provided. Run `../mod-metadata/start-okapi.sh` from the root of the Okapi source.

As this runs Okapi using Postgres storage, some database preparation is required. This can be achieved by running `./create-okapi-database.sh` from the root of this repository.

## Managed by Okapi

To register the module with deployment instructions and activate it for a demo tenant, run `./start-managed-demo.sh` from the root directory.

To deactivate and unregister the module, run `./stop-managed-demo.sh` from the root directory.

## Natively

Run `./start.sh` from the root directory.

To stop them, run `./stop.sh` from the root directory.

# Making Requests

These modules provide HTTP based APIs rather than any UI themselves.

As FOLIO is a multi-tenant system, many of the requests made to these modules are tenant aware (via the X-Okapi-Tenant header), which means most requests need to be made via a system which understands these headers (e.g. another module or UI built using [Stripes](https://github.com/folio-org/stripes-core)).

Therefore, it is suggested that requests to the API are made via tools such as curl or [postman](https://www.getpostman.com/), or via a browser plugin for adding headers, such as [Requestly](https://chrome.google.com/webstore/detail/requestly/mdnleldcmiljblolnjhpnblkcekpdkpa).

## Okapi Root Address

It is recommended that the modules are located via Okapi. Access via Okapi requires passing the X-Okapi-Tenant header (see the Okapi guide above for details).

http://localhost:9130/inventory

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

# Appendix 2 - Docker Information

## When Using the Modules as Docker Containers

For the modules to communicate via Okapi Proxy, when running in Docker containers, the address for Okapi Proxy needs to be routable from inside the container.

This can be achieved by passing a parameter to the script used to start Okapi, as follows `../mod-metadata/start-okapi.sh http://192.168.X.X:9130`

Where 192.168.X.X is a routable IP address for the host from container instances and both repository clones are at the same directory level on your machine.

### Finding a Routable Address

Finding the appropriate IP address can be OS and Docker implementation dependent, so this is a very early guide rather than thorough treatment of the topic.

If these methods don't work for you, please do get in touch, so this section can be improved.

On Linux, `ifconfig docker0 | grep 'inet addr:'` should give output similar to `inet addr:192.168.X.X  Bcast:0.0.0.0  Mask:255.255.0.0`, , the first IP address is usually routable from within containers.

On Mac OS X (using Docker Native), `ifconfig en0 | grep 'inet '` should give output similar to `inet 192.168.X.X netmask 0xffffff00 broadcast 192.168.X.X`, the first IP address is usually routable from within containers.
