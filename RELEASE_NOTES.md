* Loader: Rewrite version handling

Note: this is a significant change that has the potential to introduce bugs, and break existing
workflows. We found a significant bug in the version comparison routines, and then used AI 
to write more test cases, and then we had a lot more bugs to fix. Fixing those resulted in tighter
validation of the inputs to the version logic. We have tested this extensively, and fixed everything
we can find but no doubt there's more. Please be proactive in reporting issues. Other than outright
failure due to increased validation of version strings, the most likely issue of consequence is 
that wildcards of the form 1.0.x no longer allow pre-release versions, but wildcards were really
not supported except in corner cases, so this might not an issue for anyone. 

For those who use wildcards, see the documentation here: https://github.com/hapifhir/org.hl7.fhir.core/blob/427b8d6669893323a9e3121b017bc9f36cf5f25f/org.hl7.fhir.utilities/src/main/java/org/hl7/fhir/utilities/VersionUtilities.java#L873.
We will be extending wildcard support in the future.

* Loader: Improve support for dev-dependency IGs
* Loader: fix missing details from error message for markdown link
* Loader: pin versions in expansion parameters when using pin-manifest
* Version Conversion: Fix MedicationStatement R4/R5 conversion problem with status codes
* Terminology Subsystem: Fix for undefined code system for use = display
* Validator: Check for fixed version in manifest when validating canonical references
* Validator: Correct wrong code in error response validating codes
* Validator: Make sure messages from terminology validation have a message id
* Validator: Fix CDA Round-tripping problem in narrative
* Validator: Validate that resources from internal use packages are not being used innappropriately
* Validator: Fix some untranslated messages in the validator
* Validator: VCL Parser Update, primarily for use of {} as bracketing
* Renderer: Fix mapping rendering not seperating comments out
* Renderer: Fix NPE rendering additional bindings
* Renderer: Improved CapabilityStatement rendering - don't show headings when there's no content, expose shall/should/may/should not in the summary tables, show system level operations and search parameters.
* XIG: add indexes to XIG
* Build: OSSF compliance work
* Build: Reorganise the Publisher core to reduce single class size


