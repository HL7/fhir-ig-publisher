* Loader: Lazy load binaries for reduced memory usage
* Loader: Support for Archetype processing in IG publisher
* Loader: Process Archetypes properly as both IG input and output + remove ig-r4.json
* Validator: Do not create issue about draft dependency for example bindings
* Validator: Beef up validation of CodeSystem properties that are codes
* Validator: Make sure all validation messages have a message id
* Validator: enforce version-set-specific value for Extension and Extension context
* Validator: Specific Error when ValueSet.compose.include.system refers to a ValueSet
* Renderer: improved version specific extension rendering
* Renderer: Add element view to generated fragments
* Renderer: improve Extension context of use presentation - generate a fragment for that 
* Renderer: resolve issues with references between IGs to example resources
* Renderer: add use-context fragment for version contest restrictions
* Renderer: Lookup compliesWithProfile target from link-only dependencies
* Renderer: Accessibility - role=presentation on appropriate tables
* Renderer: Add archetype processing, and also path-other support
* Renderer: resolve issues with references between IGs to example resources
* Renderer: Lookup compliesWithProfile target from link-only dependencies
* Renderer: Update SNOMED editions related routines (add more editions)
* Renderer: Accessibility - role=presentation on appropriate tables
* Packages: Add support for ADL in packages
* Terminology Tests: Report count of tests in output from TxTester
