
#Goal

Folio compatible Knowledge Base and Cataloguing module suite.

# Further Documentation

This readme is intended to get these modules up and running, for further information on what they do, see the [guide](doc/guide.md)

# Prerequisites

- Java 8 JDK
- Gradle 3.0
- Groovy 2.4.7
- MongoDB 3.2.9
- SDKMan (http://sdkman.io/, recommended for installing Gradle and Groovy)
- NPM 3.10.3 (for tools to generate documentation)

# Preparation

If sdkman is installed, run `source ./setup-environment.sh` to setup the shell with the appropriate versions of Gradle and Groovy.

# Building the Modules

run `gradle build` from the root directory

# Running the Modules

Run `./start.sh` from the root directory. To stop them, run `./stop.sh` from the root directory.

#Using the Knowledge Base Core Module

## Standalone

Run the modules (see above)

### Root Address

[http://localhost:9401/knowledge-base] will take you to the root resource.

## With Okapi

Run the modules (see above)

Make sure that [Okapi](https://github.com/sling-incubator/okapi) is running on it's default port of 9130 (see the [guide](https://github.com/sling-incubator/okapi/blob/master/doc/guide.md) for details) for details).

### Preparing a Tenant

Run `./setup-okapi.sh` from the root directory to set up a test tenant (named our).

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

[http://localhost:9130/knowledge-base] will go to the root resource.

# Running the Tests

The tests can either be run direct or via Okapi (not all tests are affected by this).

The modules should not be started before running the tests, the tests start an instance during setup.

## Direct

These run as part of a standard build. To run them separately use `gradle testApiDirect`

## Via Okapi

Make sure that Okapi is running, the test tenant has been created and the modules registered (see the section about running the module with Okapi).

Use `gradle testApiViaOkapi` to run the API tests only via Okapi Proxy.

## Both Together

The script sets up the test tenant and registers the modules with Okapi, so only need to ensure that Okapi is running prior to running it.

Use `source full-test.sh` to set up the test tenant, register the modules and run the suite of tests both directly and via Okapi.

