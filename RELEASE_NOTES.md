* Loader: Don't crash when data directory doesn't exist + add 'repoSource' to fhir.json
* Validator: Fix bug where some #refs are not resolved to the root of the resource in FHIRPath slicing evaluation
* Validator: Fix bug passing wrong type to tx server when using inferSystem
* Validator: Fix bug processing version of CodeSystem.supplements incorrectly
* Validator: Don't process wrong parent when processing snapshot of element with profiled type that is not reprofiled
* Validator: Fix typo in OID message
* Validator: Fix handling value set exclude filters
* Validator: Allow code system declared properties to be self-referential
* Renderer: Move many rendering phrases into i18n framework
* Renderer: Add processSQLData - see https://build.fhir.org/ig/FHIR/ig-guidance/sql.html
* Renderer: Fix issue with unknown element rendering fixed value for Attachment
* Renderer: Fix bug calculating value set expansion size for multiple imports
* Renderer: Fix bug using wrong message for value sets that are too costly to expand
* Renderer: Fix extension urls not being linked in tree view
* Renderer: rendering improvements and remove static use of describeSystem
* Renderer: Fix NPE rendering profile comparisons
* Renderer: Fix bug where slicing and grouping gets mixed up rendering profile tree
* QA: Improve error message for javascript errors
* QA: fragment tracking + igtools upgrade
