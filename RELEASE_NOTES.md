* Version Conversion: fix obscure issue converting discriminators from R2B to R4/5
* Validator: Handle SIDs consistently
* Validator: Fix validation of extensions on patterns 
* Validator: Validation of cardinality on address-line elements containing pattern elements fixed
* Validator: Fixed issue where when validating with no terminology server and a value set with only an expansion (no compose), the 'inferred' code system wasn't being populated and validation was then failing on a coding with no specified code system
* Validator: when validating value sets, use CodeSystem/$validate-code not ValueSet/$validate-code
* Templates: Improve error message
* Renderer: fix rendering bug on references
* Renderer: Don't treat all bundles as history by default
