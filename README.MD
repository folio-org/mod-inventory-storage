
#Goal

Folio compatible Knowledge Base and Cataloguing module suite.

# Prerequisites

- Java 8 JDK
- Gradle 3.0
- Groovy 2.4.7
- SDKMan (http://sdkman.io/, recommended for installing Gradle and Groovy)

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

Make sure that [Okapi](https://github.com/sling-incubator/okapi) is running on it's default port of 9130 (see the [guide](https://github.com/sling-incubator/okapi/blob/master/doc/guide.md for details) for details).

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

The last output, which is the response from a test request to the module, should be something similar to:

```
HTTP/1.1 200 OK
content-type: application/json
X-Okapi-Trace: GET knowledge-base-core:200 65744us
Transfer-Encoding: chunked

{
  "message" : "Welcome to the Folio Knowledge Base"
}
```

### Root Address

[http://localhost:9130/knowledge-base] will go to the root resource.

# Running the Tests

The tests can either be run direct or via Okapi (not all tests are affected by this).

The modules should not be started before running the tests, the tests start an instance during setup.

## Direct

These run as part of a standard build. To run them separately use `gradle test`

## Via Okapi

Make sure that Okapi is running, the test tenant has been created and the modules registered (see the section about running the module with Okapi).

Use `gradle testOkapi` to run them.

