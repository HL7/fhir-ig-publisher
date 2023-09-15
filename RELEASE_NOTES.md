## Validator Changes

* General: Performance improvements for IG Publisher - parser improvements and don't regenerate snapshots
* Template Manager: Tighten up security testing on template scripts on cibuild
* NPM Loader: Fixed issue where package ids that are hl7.cda and hl7.v2 were spitting out error messages
* Validator: Add R4B binding-definition URL to validator exception list
* Validator: Correct validation when CodeSystem.content = example and server doesn't know code system
* Validator: Fix bug processing CDA snapshots
* Validator: Fix issue evaluating FHIRPath correctness on CDA.Observation
* Validator: Improve error message from validator when invariants fail
* Validator: Fix NPE validating concept maps
* Validator: Suppress wrong invariants on compiler magic types
* Validator: Fix NPE checking codes
* Renderer: Improve CodeSystem rendering - make parent property a link
* Renderer: Fix bug in version comparison
* Renderer: Improve rendering of message about logical target
* Database: Add ValueSet expansions
* QA File: Allow to suppress invariant message by invariant id
