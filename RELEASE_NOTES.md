
* Loader: Add support for private NPM registries requiring authentication
* Loader: Workaround ClinicalImpression definition problem in core spec
* Version Conversion: Fix missing extensions when converting value set properties
* Version Conversion: ExampleScenario conversion (R4/R5) significantly bulked up 
* Terminology System: Add provisional support for alternate codes
* Validator: Don't check FHIRPaths on differentials - not enough type info to test reliably
* Validator: Fix bugs in FHIRPath handling of logical models
* Validator: Fix minor bugs in type handling for Logical Models and R3 Profile validation
* Validator: Add value set qa checking 
* Validator: Fix up bi-di warning message
* Validator: Fix to get context variables right when running invariants + fix for parent not always being populated + check type in derived profiles
* Validator: Fix checking FHIRPath statements on inner elements of type slices 
* Validator: Fix scan of naming systems (error validating namespaces)
* Validator: Fix issue checking invariant expressions in R5
* Validator: FHIRPath: Strip returned IIdType of qualifier, version, and resourceType
* Renderer: Fix obligation rendering message
