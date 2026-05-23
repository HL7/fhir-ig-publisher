# Validator changes

* Java: update dependencies 
* Loader: fix error pinning parameter (wrong type)
* Loader: add support for -po (file)
* Loader: trust v2 template
* Loader: Additional resources fix
* Loader: refactor PO pipeline to allow loading a .po file at runtime (-po file)
* Loader: Fix version loading issue with cross version extensions
* Terminology: fix issue with multiple languages in internal terminology server
* Version Comparison: more logging in comparators
* Validator: fix FHIRPath error evaluating $this in repeat()
* Validator: fix json parser NPE for some incomplete arrays
* Validator: Validate using new contentReferenceProfile extension
* Validator: disallow extensions on resource.id
* Validator: Applied https://jira.hl7.org/browse/FHIR-56312, which allows htmlChecks to be invoked on 'string' as well as xhtml.  Also fixed issue where htmlChecks was returning 'false' rather than 'empty' when invoked on invalid types.  (Spec says it returns empty.)
* Validator: Fix issue with old HTML not being processed properly
* Renderer: Add start of support for structural constraints
* Renderer: Add support for rendering FML fragments in IGs
* Renderer: When rendering Binary resources that contain logical model instances (e.g., ViewDefinition), check if a specialised renderer exists before falling back to JSON/XML rendering.
* Renderer: better rendering of references in markdown
* Renderer: fix rendering recusrion problem
* QA: catch url decoding error
* Web site maintainer: fix path errors
