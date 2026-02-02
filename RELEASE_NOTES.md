* Loader: Add parameter -produce-translator-ids
* Loader: Clear temp when doing template development.
* Loader: Fix bug in version checking with 'current' and 'dev' when loading packages
* Snapshot Generator: Fix bug where slice .min is not set to zero for multiple slices when slicing is closed
* FHIRPath: fix precedence error in FHIRPath implementation
* Validator: Fix missing links to custom canonical resources + related validation problems
* Validator: Check for circular definitions when validating Structure and Operation definitions
* Validator: Update compliesWith checker to handle value set bindings
* Validator: fix logic checking for canonical resources when they are custom resources
* Validator: Remove spurious warning in the console log about loading simplifier IGs
* Renderer: update language sources and fix some french phrases
* Renderer: Add better support for extended operations in OpenAPI (#2278)
* Renderer: Fix up json logic for suppressing resourceType when rendering fragments
* Renderer: Add identifier(s) to summary table when rendering resources
* Renderer: upgrade pubpack version
* QA: Fix html link checking for generated version comparisons
