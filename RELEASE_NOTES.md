
* Terminology Subsystem: Major upgrade to terminology functionality
  * Rework handling of version references between value sets and codesystem based on extensive tests in tx-ecosystem test suite 
  * Handle versions in ValueSet.compose.include.version and expansion parameters properly
  * Remove Version Parameter in expansions (long deprecated)
  * Handle wildcard version dependencies
  * Deprecated codes are not inactive
  * Improved warning messages when value set concepts are not validated
  * Improve Error Message about missing value set binding
  * Improve display of value set version determination
* Template Manager: R4 Support + don't override layouts in templates that don't use the new approach
* Loader: Fix bug where version wildcards cause errors processing some already published packages
* Loader: Overhaul to support Additional Resources
* Loader: Fix up linking of Additional Resources
* Loader: Give all SCT codes value set a web path
* Loader: fix errors processing packages with wildcard dependencies
* Version Conversion: don't map EXT_CS_PROFILE when converting r4 back to dstu3
* Validator: Validation changes to support Additional Resources (resourceDefinition property)
* Validator: Fix issue where extensions can have either value or sub-extensions but this was not allowed by the validator
* Validator: No unknown url errors for CapabilityStatement.implementation.url
* Validator: Prevent duplicate messages from validator (still happened in a few circumstances)
* Renderer: Introduce system for tracking / rendering Conformance Statements
* Renderer: Rendering improvements to support Additional Resources
* Renderer: Improved rendering of bindings and constraints
* Renderer: Clean up inherited element representation for Additional Resources
* Renderer: fix rendering issue in CS rendering where versions shown wrongly
* Renderer: Fix problems in generated Excel spreadsheets for profiles
* Renderer: Updated NLM launcher to use URL and parameters that actually work when rendering questionnaires
* Renderer: Fix handling of multi-language narrative so that hand-coded narrative doesn't get overridden
* Renderer: fix use of wrong element name in CDA IGs for extensions when rendering CDA content
* Renderer: fix showing wrong references when referencing into bundles
* Renderer: Mark generated content for cleaner AI processing
* Comparison Generation: Suppress irrelevant binaries from the comparison folders (bye bye donald duck)
* QA: Fixed problem where not all errors were rendering
* Website Management: Add utility to republish IGs in AI ready form
* XIG: fix path problem in XIG generation
