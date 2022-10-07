* Initialization: add warning if default encoding isn't UTF-8
* Loader: Change how Tools IG is loaded (no record for other tools to stumble over)
* Loader: populate StructureMap xhtml when loading from mapping language
* Validator: Don't validate contained resources against Shareable* profiles, and also check ShareableMeasure
* Validator: Fix R5 error around cnl-1
* Validator: Add markdown validation
* Validator: add support for http://hl7.org/fhir/StructureDefinition/structuredefinition-dependencies
* Validator: fix bugs in FHIRPath handling of polymorphism
* Validator: fix validation of Coding when system is unknown (align with CodeableConcept handling)
* Validator: Fix bug where extranous text in XML was reported in the wrong location
* Renderer: Fix links in bundle rendering
* Renderer: add support for tabbed snapshots 
* Renderer: Update markdown handling per R5 agreement (see FHIR-38714)
* Renderer: Improved representation of Additional Bindings
* Renderer: Fix string offset bug generating summary for some IGs
* XIG: more presentation improvements
* -go-publish: allow folder to be a relative reference
* -go-publish: handle whe ```[category]``` is not being used.
