
Copyright (C) 2016 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

#Goal

FOLIO compatible Knowledge Base and Cataloguing module suite.

# Further Documentation

This readme is intended to get these modules up and running. For further information on what they do, see the [guide](doc/guide.md)

# Prerequisites

- Java 8 JDK
- Gradle 3.0
- Apache Groovy 2.4.7
- MongoDB 3.2.9 (running and listening on localhost:27017)
- Docker 1.12
- Node 6.4.0 (for the demo UI)
- NPM 3.10.3 (for the demo UI and documentation generation)
- SDKMan (http://sdkman.io/, recommended for installing Gradle and Groovy)

# Preparation

If sdkman is installed, run `source ./setup-environment.sh` to setup the shell with the appropriate versions of Gradle and Groovy.

# Building the Modules

run `gradle build` from the root directory

# Running the Modules

## Natively

Run `./start.sh` from the root directory. To stop them, run `./stop.sh` from the root directory.

## With Docker

Run `./start-docker.sh` from the root directory. To stop them, run `./stop-docker.sh` from the root directory.

# Using the Modules

## Standalone

Run the modules (either natively or with Docker, see above).

### Root Address

[http://localhost:9401/knowledge-base](http://localhost:9401/knowledge-base) will take you to the root resource for the knowledge base module.

[http://localhost:9402/catalogue](http://localhost:9402/catalogue) will take you to the root resource for the catalogue module.

## With Okapi

Run the modules (see above).

Make sure that [Okapi](https://github.com/folio-org/okapi) is running on it's default port of 9130 (see the [guide](https://github.com/folio-org/okapi/blob/master/doc/guide.md) for details).

### When Using the Modules as Docker Containers

For the modules to communicate via Okapi Proxy, when running in Docker containers, the address for Okapi Proxy needs to be routable from inside the container.

This can be achieved by running `../mod-metadata/start-okapi.sh http://192.168.X.X:9130`, from the root of the Okapi source.

Where 192.168.X.X is a routable IP address for the host from container instances and both repository clones are at the same directory level on your machine.

#### Finding a Routable Address

Finding the appropriate IP address can be OS and Docker implementation dependent, so this is a very early guide rather than thorough treatment of the topic.

If these methods don't work for you, please do get in touch, so this section can be improved.

On Linux, `ifconfig docker0 | grep 'inet addr:'` should give output similar to `inet addr:192.168.X.X  Bcast:0.0.0.0  Mask:255.255.0.0`, , the first IP address is usually routable from within containers.

On Mac OS X (using Docker Native), `ifconfig en0 | grep 'inet '` should give output similar to `net 192.168.X.X netmask 0xffffff00 broadcast 192.168.X.X`, the first IP address is usually routable from within containers.

### Preparing a Tenant

Run `./create-tenant.sh` from the root directory to set up a test tenant (named our).

The output should be something similar to:

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/tenants/test-tenant
Content-Length: 81

{
  "id" : "test-tenant",
  "name" : "A Test Library",
  "description" : "A test library"
}
```

The tenant can be deleted by running `./delete-tenant.sh`.

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

### Root Address

Access via Okapi requires passing the X-Okapi-Tenant header (see the Okapi guide above for details).

[http://localhost:9130/knowledge-base](http://localhost:9130/knowledge-base) will go to the root resource for the knowledge base module via Okapi.

[http://localhost:9130/catalogue](http://localhost:9130/catalogue) will go to the root resource for the catalogue module via Okapi.

# Running the Tests

The best way to run the tests is to run ./full-test.sh. Okapi needs to be already running, without changing the address it uses (unlike when running Okapi for running the modules in Docker containers).

The modules should *not* be started before running the tests, as this can cause confusing results. The tests start an instance themselves during setup.

The script performs the following steps:
1. Creates a test tenant
2. Registers the modules with Okapi (they run on a different port than usual during the tests)
3. Builds and runs the tests (API tests run both directly and via Okapi Proxy)
4. Deregister the modules from Okapi
5. Deletes the test tenant

Due to the clean up output (steps 4 and 5) being quite verbose (will be improved in future versions), it is necessary to scroll up in the output to see the test results.

If Okapi is running with a different okapiurl property set (like can be done with the `start-okapi.sh` script), then the same URL needs to be provided to this script for the tests to pass.

## Which Gradle Targets are Executed

Step 3 above executes the `test` (which includes running the API tests direct to the modules) and `testApiViaOkapi` targets to execute the tests.

Due to the modules running for the tests being bound to different ports, it is necessary to use slightly different registration steps than for when running the modules themselves (via parameterised scripts). For this reason, it is not recommended to run that target outside of the `full-test.sh` script unless willing to perform the registration steps in another manner.

# Demo User Interface

There is a basic demo user interface provided, which allows for searching instances by partial title.

This demo requires Node and NPM to be installed.

Use ```./start-demo.sh``` and ```./stop-demo.sh``` to start and stop the modules, set up some sample data, prepare Okapi and run the UI (this installs a few NPM packages globally, which may not currently work properly on Linux systems).

This demo uses the modules running in Docker containers.

# Operating System Support

Most of the development for these modules, thus far, has been performed on OS X, with some on Ubuntu. Feedback for these, and particularly other operating systems is very welcome.

# Additional information

The guide and other [documentation](doc) for this module.

Other [modules](http://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)
