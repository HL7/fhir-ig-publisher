* General: Support for openEHR WebTemplates (loading, rendering, very draft)
* Loader: Add support for recognising logical model JSON instances without resourceType
* Loader: Fix problem loading profiles in artifacts.html
* Loader: Add support for EHRS product family
* Loader: Support non-FHIR logical models in element model parsing and composing
* Loader: Hack around broken link in R4 ServiceRequest definition when rendering
* Validator: Pass context locale through to terminology server
* Validator: Fix error validating valid internal codes in supplements
* Validator: Delay text check on coded entries for performance and integrity reasons
* Validator: Properly Process all-slice element definitions when generating snapshots
* Validator: Add warnings about IG status for HL7 IGs
* Validator: Fix NPE validating code systems
* Renderer: Render displayLanguage when rendering value set expansion
* Renderer: Update operation renderer to support allowedType (R5+)
* Renderer: Fix wrongly escaped URLs for external terminology references
* Renderer: fix NPE in database creation
* Renderer: Make dependency tables active
* Publication process: make zip file location configurable
* Publication process: Add support for cloud hosting in -go-publish
* Publication process: Add draft announcement output
