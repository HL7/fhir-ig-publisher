Dependency resource classification examples

These are reduced StructureDefinition JSON inputs to the Publisher dependency
scan, not complete resources for FHIR validation or a complete IG. They omit
snapshots and base definitions because the classification code does not use them.
No EBM/AWMF packages or SUSHI installation are needed.

PublisherIGLoaderAdditionalResourcesTest parses these exact files, caches the
definitions in an otherwise empty worker context, and calls loadCustomResources().
The consuming IG declares no resource files of its own.

Fixture                         Before fix              With fix
AdditionalRecord.json           Not registered (bug)    AdditionalRecord registered
VersionedAdditionalRecord.json  Not registered (bug)    AdditionalRecord registered
Patient.json                    Not registered          Not registered
VersionedObservation.json       Not registered          Not registered
PatientFalseFlag.json           Not registered          Not registered
CustomRecord.json               CustomRecord registered CustomRecord registered
PatientProfile.json             Not registered          Not registered
LogicalRecord.json              Not registered          Not registered

AdditionalRecord.json uses the canonical
http://hl7.org/fhir/StructureDefinition/AdditionalRecord and explicitly sets
http://hl7.org/fhir/tools/StructureDefinition/additional-resource to true.
VersionedAdditionalRecord.json places the same marked type in the 5.0 namespace.
CustomRecord.json is a working compatibility control in an external namespace.
The marker must not register constraints, logical models, or a false-marked core
resource as additional resource types.

From the repository root, with JDK 17:
  mvn -pl org.hl7.fhir.publisher.core -am -Dtest=PublisherIGLoaderAdditionalResourcesTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: 6 tests pass with the fix; 2 tests fail without it. The other 4 tests
are compatibility controls (some load more than one JSON file).
