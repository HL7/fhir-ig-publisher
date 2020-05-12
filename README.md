# HAPI FHIR - HL7 FHIR Core Artifacts

## Building this Project

This project uses [Apache Maven](http://maven.apache.org) to build. To build:

```sh
mvn install
```

Note that unit tests will run, but are currently not set to fail the build as they do not all pass. This is being worked on.

To skip unit tests:

```sh
mvn -Dmaven.test.skip install
```

### Docker Build

```sh
docker build -t fhir-ig-publisher:test .
```

## Maintenance

This project is maintained by Grahame Grieve and Lloyd Mckenzie on behalf of the FHIR community.
