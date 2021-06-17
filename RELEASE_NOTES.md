* Loader: fix for NPE loading non IG publisher packages
* Loader: fix error loading path when downloading from github
* Validator: Don't raise an error if an element is identified as an example of an inherited profile rather than one defined in the current resource.
* Validator: Add support for $index on aggregators in FHIRPath
* Validator: don't fail with an exception if an unknown resource type appears in contained resource
* Validator: improved validation for some value sets that are based on unknown code systems
* Validator: add the -verbose parameter, and add additional verbose messages
* Validator: CDA: Fix erroneous type validation on CDA templates
* CDA: Suppress erroneous "Expansion" text appearing in view
* CDA: Don't delete binding information in snapshot for CDA bindable data types
* Renderer: Fix rendering of slices so type on slicer is not hidden
* Renderer: Fix rendering for most resources - remove empty tables (e.g. text element, that shouldn't render)
* Renderer: Fix NPE rendering code systems with some kinds of properties
* Renderer: Improve rendering of questionnaires (icons, option sets)
* Renderer: add support for CodeableReference
* Renderer Support binding mode and XML element information 
