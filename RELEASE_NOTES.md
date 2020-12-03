* Package Loader: fix bug loading VSAC/PHINVads packages as dependencies
* Package Loader: Allow loading dependent IGs that reference different versions of FHIR
* Validator: Fix support for cross version extensions across the entire valdation rule set
* Validator: Improve security warnings about rogue HTML tags
* Validator: fix validation of profiles and target profiles in all versions (before R3 different rules)
* Validator: reference to validator.pack just a warning for non-HL7 IGs
* Renderer: improve error messages when rendering bundles that are documents that aren't properly formed
* Renderer: Process Markdown when rendering CapabilityStatement.rest.documentation
* Renderer: Fix rendering of CanonicalResource.urlA
* Renderer: Fix handling of markdown references for R2/R2B style references 
* QA: No error on VSAC external links
* QA: fix error message suppression on tooling client





