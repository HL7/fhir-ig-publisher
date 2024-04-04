## General Changes

* Package Loader: fix bug where not loading latest version of link IGs from package server
* Version Conversion: Fix ConceptMap.group.target conversion from R3 to R5
* Loader: don't assign OIDs to examples
* Validator: Validate fixed/pattern values are not changed in derived profiles
* Validator: Fix NPE validating some profiles
* Validator: FHIRPath: consider sub-extensions when checking extension($) use
* Validator: Fix validation of concept maps containing SCT
* Validator: Preserve message id from terminology service so editors can use it to remove hints and warnings
* Validator: Allow examples to be assigned to an actor with an alternative endpoint
* Validator: fix bug referencing examples in other IGs
* Rendering: Fix NPE in list renderer
* Rendering: fix bug showing 'required' instead of 'current' rendering additional bindings
* Rendering: Fix bad references generating narratives in bundles
* Rendering: Fix bug showing extension binding twice
* Rendering: Various improvements to structure map validation to support cross-version mappings
* Rendering: Add rendering for UsageContext and ContactDetail
* Rendering: Fix bug rendering resources in Parameters resource
* Rendering: Not-pretty xhtml gets line breaks before block tags to keep line length down (work around a jekyll performance issue)
* QA: Add special case broken link handling for hl7.org.au/fhir

## Cross Version Module

* Rendering: Fix broken link in xver IG for R2
* Improved ConceptMap rendering for cross-version IG
* Handle xhtml:div type for old FHIR version
* FML: strip '-' from rules names when parsing
* Update FML parsers to accept R5 metadata in R4 FML format

## WHO Internationalization work:

* Lots's more work on cross-version module generally
* Much work making rendering i18n-able
* i18n for Patient renderer
* Refactor language handling in R5 renderers
* Generate narratives for each language in the configuration +  + 
* more work on language translations
