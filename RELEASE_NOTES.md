* Terminology System: Properly populate property definitions in expansions
* Terminology System: Support for multiple servers
* Validator: Major rework of tx and validation test cases associated with reworking terminology / validator interface and ongoing testing + reconciliation with Ontoserver
major upgrade to validation - use terminology server to perform more logic, and standardise interface based on agreements with Vocab & Ontoserver
* Validator: Improve slicing error message
* Validator: Add warning to user when referencing an extension that doesn't exist in FHIRPath
* Validator: Fix bugs in FHIRPath implementation of split() and join()
* Validator: Fix bug handling null objects in JSON (R5)
* Validator: Fix for missing search parameter definition in R4
* Validator: fix bug handling XML name extension
* Validator: Fix FHIRPath validation errors around use of %resource and %rootResource
* Renderer: fix presentation issues for formatted xml and json
* Renderer: Fix bug around extra image files produced when rendering resources
* Fix bugs handling extended Unicode characters
* QA: show source server for message in QA
* QA: add editor comments to errors + be explicit about tooling IG dependency
* Publication Process: Improved errors publishing IGs
