* General: Bump lombok, archie, commons-lang3 and nimbus-jose-jwt
* Languages: remove pt_BR translations - just have Portuguese
* Languages: Fix NPE in language translation processing
* Version Conversion: fix a few minor bugs
* Loader: process logical models when generating cross version packages
* Loader: Fix extension type and url for snapshot-source extension
* Validator: produce hint about deprecation when description uses the word 'deprecated'
* Validator: Change an unknown property in a CodeSystem to a warning not an error
* Validator: Produce a better error message for referencing a value set by it's OID
* Validator: Rework extension context validation for cross-version issues
* Validator: Fix problem parsing named elements for extensions in json (CDS-Hooks support)
* Validator: Add support for terminology operations in FHIRPath
* Validator: Fix error validating conditional references in Bundles
* Validator: Improvements handling Quantity Datatype in FHIRPath expressions
* Validator: Add support for Implicit CodeSystems from StructureDefinitions
* Renderer: update code system and value set scanning to not force loading every cs/vs in scope looking for (illegal) forward references
* Renderer: Big improvement in Extension Registry View
* Renderer: New fragments for reporting changes between publications
* Renderer: Add xig references for usage tracking
* Renderer: Improved markdown support in _data.json
* Renderer: Improve rendering of UsageContext in Additional Bindings
* QA: Pick up a few errors that were getting silently dropped
* SQL: use plain text for 1 row / 1 col sql statements (no table)
* SQL: new fields in package.db
* XIG: Add StandardsStatus, FMM and WG to XIG database
