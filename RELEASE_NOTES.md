# Rapido Mode

This version introduces the Cascais Rapido mode. Rapido = Portuguese for quick!

Rapido mode, as discussed (and named) at the FHIR Camp in Cascais, can result in builds that are up to 20x faster. 

When running in rapido mode, the publisher performs a difference/dependency evaluation of the resources, and if it finds that only a selection of resources have changed, then it only validates and generates the changed resources, and anything that depends on them. Notes:

* The dependency chain can be long, so lots of files get rebuilt. 
* this works with Sushi, though sushi itself doesn't do differential builds (yet?)
* At this time, Rapido mode is an opt-in setting while teething problems are identified. Eventually it will be the way things work

To run rapido mode, use the parameter `-rapido`. You can also add the parameter `-watch`.

# Other changes

* Terminology Subsystem: Fix npe in valueset expansion infrastructure
* Validator: Remove apikey from tx log
* Validator: Fix error validating profile reference in extension context
* Validator: Fix issue with slice discriminators checking compliesWith
* Validator: Fix issue with type matching when validating structure maps
* Validator: Validate display names in value sets
* Validator: Fix version comparison code to avoid semver failure
* Renderer: NPE fix rendering obligations
* Renderer:Adjust representation of excluded elements in profiles
* Renderer:present expansion parameters
* Renderer: Finish AI Post-processing
* QA: update qa generation for changes to file management
* QA: fix issue with not approving relative references from subdirectory to root directory
