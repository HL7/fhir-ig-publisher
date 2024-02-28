* General: Fix warning about unknown attribute at end of run
* Loader: Performance improvement for resource parsing - thanks Brian Postlethwaite
* Loader: Fix bug loading R5 extensions with imported value sets
* Loader: Fix bug in FML parser for target with no element 
* Cross-version Engine: major refactor of cross version analysis
* Terminology Module: Full support for case-sensitivity on code systems
* Validator: Fix significant bug where validator gets internal child lists incorrect and doesn't do complete validation when switching between validating profiles and resources directly
* Validator: Fix Crash on slicing a sliced element #862
* Validator: Fix bug copying constraints into Bundle.entry.resource profiles
* Validator: Replace dom-3 with custom java code, and check xhtml references to contained content (for performance reasons)
* Validator: Improve concept map code validation
* Validator: Update observation validator for committee decision not to enforce Blood Pressure profile for Mean Blood Pressure
* Validator: Validate value set internal codeSystem references
* Validator: Split value set validation into 10k batches for very large extensional value sets
* Validator: Hack fix for opd-3
* Validator: Fix bug where using ontoserver when not appropriate
* Validator: Fix issues with inferSystem
* Validator: Don't require HL7 committee for contained resources in HL7 namespace
* Validator: Fix where validator was ignoring minimum cardinality for XML attributes (especially in CDA)
* Validator: Improved ConceptMap validation
* Validator: Check for broken links inside resource narrative
* Validator: Change validator so that it marks value properties in primitive data types as illegal
* Renderer: Fix code system rendering for uri properties
* Renderer: Fix broken links Bundle and Profile rendering
* Renderer: Fix bug where not rendering ConceptMap relationships
* Renderer: Fix wrong URLs rendering Profiles and Questionnaires
* Renderer: fix bundle rendering broken links
* Renderer: correct representation of overly large value sets
* Renderer: Fix NullPointer exception on parsing FhirPath expression #863
* Renderer: Stop duplicating Global Packages in the current IG
* Renderer: Render contained resources when rendering Patient resources
* Generator: Fix for Formal instance end up in the Examples folder of package #861
* QA: Show release label in publication summary
* QA: Add suppressed hints and warning counts to qa.json
* Web publishing: Fix bug generating wrong link to R5 Base spec
