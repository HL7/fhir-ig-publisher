* Loader: Use correct sub-version of tools package etc
* Loader: fix version mismatch notes
* Loader: add further information about resource loading to the log
* Loader: fix problem handling R6 evidence resources
* Terminology: Change R5 tx server to use http://tx.fhir.org/r5 (instead of /r4)
* Validator: Support authentication for terminology servers (see https://confluence.hl7.org/display/FHIR/Using+fhir-settings.json)
* Validator: Fix issue where validator not retaining extension context when checking constraint expressions in profiles
* Validator: Validate min-length when found in extension
* Validator: Correct bug parsing json-property-key values with meant validation failed
* Validator: Fix problem validating json-property-key value pairs
* Validator: Fix special case r5 loading of terminology to fix validation error on ExampleScenario
* Validator: Improve handling of JSON format errors
* Validator: Fix bug where extension slices defined in other profiles are not found when processing slices based on extension
* Validator: Validate fhirpath expression in slice discriminators
* Validator: Fix slicing by type and profile to allow multiple options per slice
* Validator: List measure choices when a match by version can't be found
* Renderer: Render min-length extension on profiles
* Renderer: Fix rendering of Logical Models for polymorphic elements, and rendering target profiles with versions
* Renderer: Render contained resources in List resource
* SQL Module: fix bug in DB building
* Go-publish: clean up IG registry maintenance code
* Go-publish: Stop technical-corrections for now
