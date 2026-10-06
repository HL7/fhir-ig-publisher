## R6 Version

* As of this version, internal processing is moving to be based on R6 not R5
* Support the new R6 release (6.0.0-snapshot1)

## Other Changes

* Loader: use tools IG release 1.3.0
* Loader: Loading Speed improvements
* Loader: additional resources - check their definitions carry the IG's version, keep the additional-resource flag on the published definition, and fix "null#Type.element" links from missing web paths
* Loader: fix package cache identification in multi-language IGs
* Loader: fix running sushi with a fixed version
* Loader: don't try to load .svg files from source folders as resources
* Loader: Depth limits in the JSON, XML, Turtle, XHTML and SHC parsers
* Loader: Work around a problem with an extension definition in old builds of the extensions pack
* Loader: NPM package generator fixes for core dependencies and versionless dependsOn, plus an immutable package dependency planner
* Conversion: R5 -> R4/R4B carries ValueSet.compose.property as an extension; fix type "Any", FHIR version codes, and the ValueSet scope extension (FHIR-53122)
* Terminology Subsystem: save the terminology cache after validation and again at the end of the build, even if the build fails, nonce moved to a partner file, fixed cache key conflicts, and only load from disk when asked
* Terminology: send required code system supplements to the server (including versioned supplements and server-side includes) and count server-applied supplements as used
* Terminology: fixes for contained resources (including circularities), mixed inactive codes, missing code or system, and resource status checking
* Terminology: the router no longer queries every server for a code system that doesn't exist; dummy value sets get a consistent URL so they cache
* Snapshot generation: rework how datatype profile root constraints migrate into the referencing element, stop copying slicer constraints into slices, always close type slicing, and fix type-specific constraints (binding, maxLength) found in US Core
* Snapshot generation: fix additional-base merges, mapping identity collisions, slice groups that end the snapshot, obligation bindings and extensions, label and additional binding merges, pattern handling, and wrong URLs in R6 snapshot processing
* Validator: FHIRPath: fix =/!= on mismatched types and hasValue()/getValue() on complex types; join() on an empty collection returns empty; split() is typed as an ordered collection in static analysis
* Validator: Allow ElementDefinition.constraint.source to name an imposed profile
* Validator: Match the reference host, not a substring, in policyForReference
* Validator: Fix time validation problem
* Validator: Fix base64Binary whitespace handling
* Validator: Add missing SPDX codes
* Validator: Add support for Questionnaire variables (SDC), including launch context (#2404), and Questionnaire answer constraints (#2549)
* Validator: Add support for AdditionalBinding.usage when validating
* Validator: Improved error messages for failed invariants and constraints
* Validator: OperationOutcomes produced by the validator now carry a `validator-version` extension (#2459)
* Validator: StructureDefinition validation: validate root ElementDefinitions, move the slicing cardinality consistency check from the snapshot generator to the validator, and fix profiles being validated against the wrong version context
* Validator: Missing ELM in a CQL Library is now a warning, not an error
* Mapping Language: many evaluation and validation fixes - constants, cp/qty/id/c/cc transforms, sub-element sources/targets, choice types, type resolution and analysis, and parse/render of version metadata and trailing comments
* SQL on FHIR: %rowIndex and repeat support, bounded repeat recursion, and runner/validator fixes aligned across R4, R5 and R6
* Renderer: new narrative renderers for Organization, OrganizationAffiliation, HealthcareService, Endpoint, Location, Group, Practitioner, PractitionerRole and RelatedPerson; render RelativeTime, Duration, Distance and Count
* Renderer: Provenance shows patient, encounter, basedOn, reason/authorization/why, agent roles and an Entities table (HL7/fhir-ig-publisher#1225)
* Renderer: improved rendering of Additional Resources, change tracking in StructureDefinitions, and standards status on ValueSet.compose.include.concept
* Renderer: WCAG accessibility fixes, and new XHTML utilities to support WCAG
* Rendeer: add a Translatable flag
* Renderer: CodeableConcept text, display-only references, identifiers, ConceptMap relationship anchors, R6 Requirements, TestReport score, unclosed elements in the copy-XML buttons, illegal html in resources, and no narrative links when there's no web path
* Renderer: accessibility fixes across the generated pages and tables
* Renderer: fix table of contents ids beyond 222 entries per section, breadcrumbs when the IG resource is the root page, and the ToC when the root is tied to a resource
* Renderer: show must-support inherited from the slicer in slices, following the snapshot generation changes
* Renderer: extensions and incubator pack fixes - unique table ids when several tables are on a page, note when only metadata has changed, and mark the generated summary as generated
* Renderer: fix malformed XHTML from & or < in page titles, and leave additional resources out of definitions.ttl.zip (no RDF support)
* Signing: use the canonical mime type form in Signature.targetFormat
* QA: check generated HTML for WCAG accessibility (heading structure, page language, Section 508 static checks), with an `accessibility-checks` parameter and an in-page axe-core check panel for local builds
* QA: check extension naming conventions in the extensions pack (FHIR-43753)
* QA: report a malformed template outcomes file as a template error instead of failing the build
* QA: don't report "no explicitly linked examples" for additional resources, and fix the OID guidance link
* Web Publishing: check the publication-request version is correct, require absolute paths for all parameters, and fix a file deletion problem during publication
* Web Publishing: fix HTML in the generated reports and redirects




