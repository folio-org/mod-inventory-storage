
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
- Groovy 2.4.7
- MongoDB 3.2.9, running and listening on localhost:27017
- Node 6.4.0 (for the demo UI)
- NPM 3.10.3 (for the demo UI and documentation generation)
- SDKMan (http://sdkman.io/, recommended for installing Gradle and Groovy)

# Preparation

If sdkman is installed, run `source ./setup-environment.sh` to setup the shell with the appropriate versions of Gradle and Groovy.

# Building the Modules

run `gradle build` from the root directory

# Running the Modules

Run `./start.sh` from the root directory. To stop them, run `./stop.sh` from the root directory.

# Using the Modules

## Standalone

Run the modules (see above).

### Root Address

[http://localhost:9401/knowledge-base](http://localhost:9401/knowledge-base) will take you to the root resource for the knowledge base module.

[http://localhost:9402/catalogue](http://localhost:9402/catalogue) will take you to the root resource for the catalogue module.

## With Okapi

Run the modules (see above).

Make sure that [Okapi](https://github.com/folio-org/okapi) is running on it's default port of 9130 (see the [guide](https://github.com/folio-org/okapi/blob/master/doc/guide.md) for details).

### Preparing a Tenant

Run `./create-tenant.sh` from the root directory to set up a test tenant (named our).

The output should be something similar to:

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/tenants/our
Content-Length: 81

{
  "id" : "our",
  "name" : "our library",
  "description" : "Our Own Library"
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
4. Deregister the modules from Okapi 5. Deletes the test tenant

Due to the clean up output (steps 4 and 5) being quite verbose (will be improved in future versions), it is necessary to scroll up in the output to see the test results.

If Okapi is running with a different okapiurl property set (like can be done with the `start-okapi.sh` script), then the same URL needs to be provided to this script for the tests to pass.   

# Demo User Interface

There is a basic demo user interface provided, which allows for searching instances by partial title.

This demo requires Node and NPM to be installed.

Use ```./start-demo.sh``` and ```./stop-demo.sh``` to start and stop the modules, set up some sample data, prepare Okapi and run the UI (this installs a few NPM-related modules globally).

