* General: redo how generate-versions flag is handled, and produce alternative version descriptions
* General: Rework how other version packages are generated, and recast extensions for older versions
* CQL Sub-system: Fix for CQL Framework Dependencies (#1042)
* Version Conversion: Add subscription Conversion R4 - R5
* Terminology: Fix bug processing languages when processing value set from server
* Validator: Fix detection of HL7 core vs affiliate specifications
* Validator: Fix bug failing to compare large valuesets correctly when generating snapshots
* Renderer: Handle possibility of language displays being null, and don't blow up if it happens.
* Renderer: find profile uses in capability statements
* Renderer: Fix bad link for unknown FHIR value set
* Renderer: Improve Binary rendering
* Renderer: Fix bug rendering test plans
* Renderer: fix illegal URL on compositional SCT codes
* Loader: Support for CDS-hooks extension type when writing json
* QA: Check for scripts in .svg files - not allowed anymore
* QA: Bump owasp plugin to 12.1.0 (#1045)
