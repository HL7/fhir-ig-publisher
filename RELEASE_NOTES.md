# IG Publisher Major Release

This version is labelled as Release 2 of the IG Publisher, as it is the first version to support 
a long requested feature, which is the ability to generate fully multi-lingual IGs (see 
the [documentation](https://build.fhir.org/ig/FHIR/ig-guidance/languages.html)).

Anything to do with language loading/processing/rendering has been entirely rewritten in this 
version. We believe after extensive checking that mono-lingual IGs have not been affected at all.

Thanks for Lloyd Mckenzie and Jose Costa Teixeira for extensive work on this development. 

## Other changes

* Loader: Add support for NPM aliases
* Loader: Workaround issue where side-version packages contain resources without stated paths
* Terminology Subsystem: Fix terminology cache hashing (#2006) and Add fhir-types entries to txCache
* Terminology Subsystem: improve tx log performance and readability, and avoid logging excessively large requests
* Version Conversion: Convert DocumentReference.date/indexed/created conform fhir specs (#2013)
* Validator: change URL wording in warning about unknown URLS after discussion in committee and fix error validating URLs + don't check Attachment.url + Bundle.entry.request.url + QuestionnaireResponse.item.definition
* Validator: implement new agreement about how IPS narrative linking works
* Validator: Check extension contexts are valid paths
* Validator: Properly handle not-present code systems in yet another place
* Validator: fix bug when validation an operation definition that has parameters with parts and profiles
* Validator: fix slicing bug in pre-process
* Validator: fix bug in path handling in snapshot preprocessing
* Validator: Fix extension URL for value set parameter 
* Validator: fix outsize batch validation request problem
* Validator: fix: ensure non-null return for slices and handle missing slicing details for extensions
* Renderer: rework presentation of elements with no computable binding
* Renderer: update table generator to add filter and view controller
* Renderer: fix null locale in RenderingContext
* Renderer: Remove generated narrative header in IG pages
* Renderer: Fix NPE rendering some extensions
* Renderer: Add plain language summary on ci-build
* Package Generator: fix error generating metadata table in .db
* Package Generator: require implementation guide resources in packages
* QA: Flag to Suppress ci-build warnings


