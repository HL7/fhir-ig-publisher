* Security Fix: Move all instantiation of transformerFactory to XMLUtils and set ACCESS_EXTERNAL flags automatically
* Loader: Update tools dependency to latest tooling package
* Loader: Add support for Gherkin, json, xml, and text to Adjunct Files
* Loader: Handle 308 redirects when fetching packages
* Version Converter: fix version conversion problem for ConceptMap (4<->5): relationship comment getting lost for noMap entries
* Snapshot Generator: Rewrite processing of map statements when generating snapshots to fix known bugs
* Validator: Fix NPE in ValueSetValidator
* Validator: Add check for multiple WG extensions (HL7 context)
* Validator: rework decimal lowBoundary() and highBoundary() after discussion on Zulip, and add extensive testing
* Renderer: Improve complex extension rendering when rendering by profile
* Renderer: Updates to Capability Statement rendering (and minor Operation Definition rendering improvement)
* Renderer: Fix wrong reference to CDA classes for unscoped class names
* Renderer: fix rendering issue for R4 relationship codes in ConceptMap
* Renderer: Fix NPE in questionnaire renderer
* Registry: switch IGRegistry to use different json implementation (formatting reasons)
* XVer Module: Remove the use of Grahame's local folder while processing the XVer an…
* XVer Module: reduce severity of no binding rule
* XVer Module: performance improvements for Jekyll for cross-version IG
* XVer Module: Ensure the xver-qa temp folder exists before trying to write/read files from the folder