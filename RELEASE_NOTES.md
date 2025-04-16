## Validator Changes

* Loader: Don't pin ImplementationGuide.dependsOn references
* Loader: don't raise errors for URL duplications where one resource is retired
* Loader: upgrade tools IG to 0.5.0
* Loader: Fix bug where examples in other packages not being found on windows
* Template System: Add additional-paths to resources.json
* Terminology Sub-system: Add support for filter by regex on property value in ValueSet definitions
* Snapshot Generator: Fix discovered issues related to snapshot generation with slicing while investing how slicing works per extensive committee discussion
* Validator: Cross-check canonical version when there's an applicable version element when validating `ImplemenationGuide.dependsOn` or `ValueSet.compose.include`
* Validator: OperationDefinition: Cross check Operation Parameters with inputProfile and outputProfile
* Validator: Add check for consistency across slices for types and must-support when validating profiles
* Validator: more validation url resolution exemptions
* Validator: Remove superfluous spaces validating ValueSet parameters in Questionnaire 
* Validator: Exempt `tel:` urls from being resolved in validator
* Validator: More URL validation improvements including checking the OID registry
* Validator: Fix source location tracking for FHIRPath expressions
* Validator: Add support for enforcing R5 policy on relative references in Bundle entries
* Validator: Add profile.compliesWith validation
* Validator: Fix validation issues around ImplementationGuide.dependsOn canonical and package
* Validator: Add support for sort in FHIRPath
* Renderer: Obligation Rendering improvements
* Renderer: Don't produce links in narrative when they are definitely broken
* Renderer: Add obligation-summary fragment
* Renderer: add oid based expansion fragment
* Package Generator: Handle version conversion failure gracefully
* QA: Improved rendering of sub-issues for compliesWith validation
* General: Fix various NullPointerErrors
* General: Bump apache poi and xml-beans versions
