* Loader: Remove support for old-style IGs
* Loader: Fix bad version links
* Loader: Workaround issue where R5 build wrongly adds ele-1 to base
* Snapshot Generator: Make sure snapshots are generated when fetching types
* Version Conversion: Fix conversion issue associated with ConceptMap.element.target.equivalence in versions previous to R5 (use proper extension URL, and move extension so it can be a modifier. And fix for modifierExtension handling)
* Validator: Improve language on constraint error message + add expression checking for SQL on FHIR project
* StructureMap support: Fix uuid() executing StructureMaps and dont throw errors processing StructureMaps
* Renderer: Fix rendering of XML Attributes in profiles
* Package Builder: Add support for test folder in NPM packages
* QA: work around changes to constraint message
* XIG: Major XIG upgrade - generate SQLite file instead


