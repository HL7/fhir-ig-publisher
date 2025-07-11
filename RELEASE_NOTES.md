
* General: Use maven central for release
* Translations: update pt translations (#2055)
* Loader: Add support for signing Bundles to the publisher
* Loader: Improved canonical support for JSON and XML + refactor xhtml handling to fix canonical issues
* Snapshot Generator: fix problem where paths not processed properly generating snapshots
* Terminology Subsystem: fix problem with implicit value sets not getting resolved
* Validator: Add more structural validation of XHTML
* Validator: Add validating ECL filters
* Validator: Improve error message for missing extensions
* Validator: Fixed error with OperationDefinition Parameter cross-validation:
  * Can't have target profiles if the type is Element due to existing OperationDefinition invariant
  * Old code was sticking profiles into targetProfiles, which generated spurious errors
  * Make messages clearer
* Validator: Provisional support for validating Bundle signatures
  * `-cert` parameter for certificate sources, and add certificates folder to fhir-settings.json
* Validator: Allow subclassing in structureMaps
* Validator: Adjust errors for broken internal links in Resource.meta.source for HAPI users
* Validator: Improved validation for attachments
* Renderer: limit Obligations to active Actors
* Renderer: Filter obligations - filter out actors not used
* Renderer: Fix stated paths for query type OperationDefinitions
* QA: clean up 'see above' references in dependency view
* QA: Fix error where bad html terminates QA rendering




