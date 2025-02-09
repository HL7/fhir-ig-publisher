* Loader: Optimize the JSON parsing in NpmPackageIndexBuilder.seeFile (#1898) (faster loading)
* Loader: Fix stack crash when structure definitions are circular
* ResourceFactory: updates for loading generated resources in IG publisher
* SnapshotGenerator: Add "http://hl7.org/fhir/tools/StructureDefinition/snapshot-base-version" to snapshot generation
* Validator: Add HL7 CodeSystem display and definition checks
* Validator: Fix error reporting duplicate contained IDs when contained resources are sliced by a profile
* Validator: Allow cardinality changes in obligation profiles (but not recommended)
* Validator: Add underscore to regex to be able to use underscore in Bundle URLs
* Renderer: fix NPE in patient renderer
* QA: Support for Profile Test Cases
* Publication Process: support for current-only IGs
* Publication Process: extra qa around HL7 web site process


