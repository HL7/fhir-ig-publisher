* Loader: Fix issue loading cross-version package
* Loader: Fix problem with loading packages without an IG resource
* Terminology sub-system: Use https: for terminology servers
* Snapshot Generation: Fix for wrong calculation of slice boundaries when pre-processing slices
* Snapshot Generation: Fix for missing type profile cardinality when generating snapshots
* Snapshot Generation: Fix: add null check for derived definition in ProfileUtilities
* Snapshot Generation: Track extension provenance when generating snapshots (for obligations)
* Validator: Significant upgrade to Questionnaire validation capability
* Validator: Fix maxLength validation in Questionnaires
* Validator: Fix for repurposing the SNOMED-CT CA edition concept
* Validator: Fix issue with validation profiles on parameters of type Resource in OperationDefinitions
* Validator: Fix OperationDefinition Validation check on outputProfile
* Validator: Better validation for references to IGs in packages
* Validator: Add oid 2.999 to example URLs
* Validator: Fix bug in compliesWithProfile checking - wrongly processing issue level for extensible bindings
* Validator: Preliminary Work on multi-inheritance for profiles (still WIP)
* Validator: Fix: recheck invariant with deviating severity
* Validator: find OIDs as NamingSystem OIDs
* Version Comparison: fix code system comparison to not alter code system being compared
* Renderer: generate https: links for images and package references (not http:)
* Renderer: fix NPE rendering codesystem links
* Renderer: Modified rendering of SubscriptionTopic.trigger.queryCriteria.current, changing from 'create {0}' to 'current {0}'
* Renderer: Improved presentation of extension summary
* UI: AI Driven upgrade of the GUI interface
* QA: Finish work on New QA pipeline
* QA: significantly improve performance generating QA files
