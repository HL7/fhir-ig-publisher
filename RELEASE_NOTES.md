
* Loader: Significant Performance improvements parsing JSON resources + other performance improvements
* Loader: New Parameters apply-wg and default-wg
* NpmPackages: Start generating .index.db as well as .index.json in packages for faster package reading
* Validator: Refactor Type handling for faster performance
* Validator: Validate the stated publisher/WG/contacts for HL7 published resources
* Validator: Better error message when diff contains bad paths
* Validator: Pass dependent resources to server and make sure cache-id is filled out properly in all contexts
* Validator: Fix error in FML parser parsing parameters
* Validator: Fix issue with dom-6 and contained elements (Internal ChildMap synchro issues)
* Validator: Better handling of errors from tx.fhir.org
* Validator: Fix bug checking for implicit value sets
* Validator: Fix bug checking of mixing snomed display types
* Validator: Reduce size of validatable concept map to 500 - for now + better handling of errors on server batches
* Validator: Improve UCUM validation BP rule
* Validator: set example status when valdiating
* Validator: don't check examples for deprecated / withdrawn content
* Renderer: Fix up handling of includes in liquid templates
* Renderer: Fix up rendering of profile names for abstract profile instantiations
* Renderer: Improved rendering of codes in include when rendering valuesets
* Renderer: Fix problem caching look up of implied value sets
* Renderer: Display workgroup in extension summary table
* QA: Report max memory used
* Publication Process: fix issues generating and releasing templates
* Publication Process: Fix typo in version default logic
* XIG: Start rewrite (phase 1)
