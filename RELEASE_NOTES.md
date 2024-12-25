
* Christmas 2025 Release: Add support for [test instance factory generation](https://build.fhir.org/ig/FHIR/ig-guidance/testfactory.html)
* Snapshot Generator: Only use profiled datatype information for generating snapshots for Resource and Extension
* Validator: Fix validation of invariants in profiles - check for more conflicts, and don't call conflict if the expression is the same
* Validator: Fix issues with tracking supplement usage and getting supplement version matching correct
* Validator: Fix npe loading old simplifier package
* Renderer: Fix logical model rendering to use type characteristics for can-be-target
* Version Comparison: Fix comparison template loading issue
* Website Publishing: fix bug publishing CDA IGs
