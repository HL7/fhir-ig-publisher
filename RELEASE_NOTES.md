* Loader: Fix problem not finding current version of extensions pack for non-R5 versions
* Loader: New release of pubpack
* Loader: extend CQLSubsystem to cover Measure, PlanDefinition, ActivityDefinition
* Loader: fix missing union and intersection for CodeSystem and ValueSet comparisons
* Loader: Work around flaw in R5 specmaps claiming to have paths for terminology URLs it does not
* Loader:fix custom resources with profiles
* Template Manager: Add the SDC template to the list of trusted templates
* Terminology System: Fix missing username/password for tx server in IG publisher
* Terminology System: Add support for valueset-version 
* Snapshot Generator: Change rules around stripping extensions when generating snapshots
* Snapshot Generator: Do not use metadata from data type profiles on elements when generating snapshots
* Snapshot Generator: Fix version issues in snapshot generation tests
* Validator: Add supplements for used systems as well as for value set systems when validating on server
* Validator: Add versions to message about multiple matching profiles
* Validator: fix issue missing idrefs validating IPS documents
* Validator: fix missing port from server when doing tx-registry redirections
* Validator: fix NPE in validator around Extension context
* Validator: Fix obscure error on contentReference in profiles in FHIRPath engine
* Validator: Fix questionnaire response status checking
* Validator: Fix validation of displays when language is unknown
* Validator: Fix version conversion issue for validating derived questionnaires
* Validator: Handle secondary terminology server errors properly
* Validator: Update FHIRPath validation to handle rootResource type properly
* Version Comparison: Fix filter comparison logic when comparing valuesets
* Version Comparison: Fix presentation issues and union and intersection links in previous version comparison
* Test Subsystem: Add support for test-data-factory parameter
* Renderer: Update logic for determining key elements to include elements where min, max, minValue, maxValue, bindings, etc. have changed from the base resource
* Renderer: Add support for using Liquid on plain JSON directly and add support for markdownify filter
* Renderer: fix bug using wrong reference on uri in liquid renderer
* Renderer: Questionnaire rendering improvements
* Renderer: Refactor Liquid engine and add support for forLoop and capture
* Renderer: Add support for liquid based rendering of json files
* Renderer: Defined new dependency table that shows all dependencies, while being less technical (and still making technical information available).
* Renderer: Ensure details about the primary bindings appear after the primary binding, not after additional bindings
* Renderer: Fix description of new content in difference analysis
* Renderer: Improve OID error message 
* Renderer: In key elements, don't just show MS types, show all of them
* Log: hide API-Key from appearing on the tx log
* Log: Ensure that if there's an issue with SQL or fragment expansion, at least something shows up in the log, even if debug is not on.
* QA: fix presentation of tx server errors
* QA: Improve qa output for sequence in publication checker
* QA: Show code system fragments in QA summary for improved visiblity
