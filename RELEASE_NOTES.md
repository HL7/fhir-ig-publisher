## Validator Changes

* Loader: Internal changes for supporting new cross-version packages - not yet released (WIP)
* Loader: Support for new cross-version packages - pin versions explicitly in old core packages (R3-R4B)
* Loader: Fix bug checking versions with wildcards
* Loader: Ensure that wildcard dependencies are always to the latest version when loading wildcard dependencies
* Loader: Fix misleading error for case mismatch on files
* Loader: restore partial version R2 support for cross-version packages
* Loader: fix R3 name issues
* Loader: upgrade tools ig
* Validator: Fix issues handling deprecated concepts and designations in code systems and value sets - make consistent across all contexts and code systems
* Validator: No double errors on unknown cross version extensions
* Validator: Don't use batch validation when no tx server
* Validator: Add missing SIDs to validator definitions
* Validator: Fix error trying to validate against a broken profile (no snapshot)
* Validator: Fix validation of max decimal places in Questionnaire
* Validator: Fix erroneous use of alternate tx servers to validate value set codes in batch mode
* Renderer: render modifier extensions better
* Renderer: Fix web source for LOINC attachments value set
* Renderer: Render modifier explicitly on extensions
* Renderer: Fix double $$ rendering operations
* Renderer: Add rendering support for additional SCT editions
* Renderer: Allow suppressing db & expansions
* Cross-Version support: Fix error generating cross-version definitions for canonical resources
* XIG: Phase one of document tracking
