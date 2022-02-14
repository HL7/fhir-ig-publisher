* Validator: accept regex on ElementDefinition and ElementDefinition.type, and an error for regex on ElementDefintion.type (though core packages have it on the wrong place)
* Validator: fix handling of cross-version extensions on choice elements
* Validator: fix OID validation (accept 1.3.88 GTIN OID)
* Validator: only consider bindable types when checking for multi-type bindings
* Renderer: Rendering fixes & improvements for Questionnaire and Patient and partial dates improvements to relative link handling in markdown when generating snapshots
* Publisher: Add new parameter "propagate-status" - see https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters
* Publisher: introduce fhir-tools-settings.conf (see https://confluence.hl7.org/display/FHIR/Using+fhir-tool-settings.conf)
* Publisher: updates to package tools for changes to package.json#type (see https://confluence.hl7.org/pages/viewpage.action?pageId=35718629#NPMPackageSpecification-Packagemanifest)
