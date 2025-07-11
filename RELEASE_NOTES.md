* General: Use maven central for release
* Loader: Add CodeSystem.content to resources.json
* Loader: Fix for signature generation
* Loader: Fix for duplicate link error
* Loader: Define path-liquid-template to fix liquid template loading order issue
* Terminology Subsystem: Fixing errors associated with implicit value sets
* Database: DB population fix
* Validator: FHIRPath improvements - see https://github.com/hapifhir/org.hl7.fhir.core/pull/2080
* Validator: Don't require vitals signs profile for BMI code: 59574-4
* Validator: Fix error in tx error processing where some errors were being lost when validating codes
* Validator: More work on signature validation
* Validator: Fix NPE validating concept maps
* Validator: Fixed issue with Parameter/Profile alignment validation when validating OperationDefinitions
* Validator: Don't check URL in Coding.system when checking defined URLs - it's already fixed
* Validator: Add urn:ietf:bcp:13 to known definitions
* Validator: Update Narrative validation for FHIR-I changes to language control extension
* Validator: Fix bugs with implicit value set handling
* Validator: Fix bugs validating extension context: nested elements and profiles
* Validator: Use batch validation when validating ValueSets and ConceptMaps but only if the server supports it
* Validator: Allow new target types when specializing in logical models
* Validator: Add warnings for unexpected all codes bindings on LOINC, SNOMED CT, and CPT
* Validator: Improve value set validation for ValueSet.compose.include.filter.value
* Validator: Fix wrong return value for attachment validation (wrongly causing profile validation to fail)
* Renderer: Add [multi-map feature](https://build.fhir.org/ig/FHIR/ig-guidance/multi-maps.html)
* Renderer: Fix up invalid XHTML generation in renderers 
* Renderer: Fix rendering for some multi-line strings
* Renderer: Fix supplement rendering for value sets
* Publication Support: allow publishing to different canonicals


