* Loader: Fix up parsing of logical models
* Loader: Fix handling of CDA example fragments
* Loader: Fix bug parsing extension with no value in JSON for the validator
* Validator: Fix for issue parsing SHC and not recording line/col correctly
* Validator: Fix issue validating CDA FHIR Path constraints
* Validator: Better error handling validating codes in concept maps
* Validator: Validate special resource rules on contained resources and Bundle entries
* Validator: Improved error messages of observation bp
* Validator: Fix up WG internal model for changes to workgroups
* Validator: fix misleading error message inferring system when filters in play
* Validator: Fix type handling for logical models (CDA fixes)
* Version convertor: Fix version conversion issue between r4 and r5 charge definition issue
* Renderer: Fix rendering extension and missed profile on Reference()
* Renderer: fix wrong generation of logical models (for CDA)
* Renderer: Don't create wrong phinvads references
* Link Checker: Don't fail in link checking for illegal file references
* Publication Process: exit code = 1 unless publication run is succesful
