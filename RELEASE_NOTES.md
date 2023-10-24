* Package Cache Manager: Add package use tracking to FHIR cache for validator.fhir.org
* Loader: Support for instance-name and instance-description in IG publisher
* Loader: Hack for wrong URLs in subscriptions backport
* Validator: Validation by templateId for CDA
* Validator: Fix NPE validating concept maps
* Validator: Update ViewDefinition validator for change (alias -> name)
* Validator: Fix for NPE validating sql-on-fhir ViewDefinition
* Validator: Fix for index out of bounds error when extension uses itself
* Validator: Fix issue where .resolve() in FHIRPath didn't work with URL values (and fix typo in i18n system)
* Validator: Implement FHIRPath slice() function in validator
* Renderer: Generate html, json, csv and db views for code system and value set lists
* NPM Package builder: Force version to be a proper semantic version on the ci-build site
* QA: Add link checking for AreaMaps
* QA: Update validation presenter for binary signposts
* Release System: Track Speed & Memory use for test IG runs for cross-release performance tracking
