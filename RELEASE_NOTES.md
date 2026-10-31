## General notes

* The terminology ecosystem coordination server (tx-reg) is now separate from the terminology server: by default it is https://ecosystem.hl7.org/tx-reg, and the new `-ecosystem` parameter nominates a different one (`n/a` for none). Nominating a terminology server with `-tx` turns the ecosystem off unless `-ecosystem` is given too
* This release continues the accessibility (WCAG) work on generated IGs - see the Renderer notes below

## Specific Changes

* Loader: new `-ecosystem` parameter (see above), and `--help` is accepted as well as `-help`
* Loader: recognise additional resource types defined in loaded dependencies (StructureDefinitions marked as additional resources), not only those in the IG itself
* Loader: decide whether a resource is a canonical resource from the type hierarchy (including the interfaces a type implements), not from a fixed list - fixes additional resources such as ViewDefinition
* Loader: logical model instances published as Binary resources are written into the package (and the per-version and per-language packages) as Binary-[id].json, and the raw format links for them are fixed
* Loader: adjunct files - preserve the content type (including media type parameters), and say when a file can't be found because the IG defines no `path-binary` parameters
* Loader: FML (StructureMap) source is parsed with a worker context
* Loader: the CQL subsystem uses version 4.9.0 of the CQL translator, and data requirements processing works again
* Loader: openEHR archetype import works out which attributes are single valued from the model instead of a hard coded list
* Loader: add `ans.cda.fr.template` to the known templates
* Loader: don't crash when no `-ig` parameter is given
* Loader: when a file name can't be decoded (non-ASCII names under a POSIX locale), stop with a message that says why and how to fix it, rather than reporting a missing file; pdf files are now correctly treated as unparsed files
* Loader: `-rapido` - fix the dependency check for canonicals with no value or with a version (dependencies are now matched on url alone)
* Loader: close the files the template script steps open (they blocked deleting template/ on Windows in `-watch` mode), read the output when the script writes only JSON, and close package.db when generation fails
* Loader: translations come in the same order on every machine
* Terminology Subsystem: a ValueSet include with no system (one that only imports value sets) is always used when choosing which includes apply to a code system version
* Terminology Subsystem: supplements that name a code system without a version apply to every version of it; global-lang-pack supplements are recognised as language packs
* Terminology Subsystem: the notSelectable property is recognised by its standard uri whatever code the code system gives it, and the notSelectable/abstract check is much faster
* Terminology Subsystem: content in both hl7.terminology and a FHIR core package - THO's copy is still what a reference with no version gets, but a reference that names a core version (e.g. url|5.0.0) now gets that version unless THO provides it
* Validator: FHIRPath conformsTo() can name an implementation guide (by url or package id, optionally versioned) as well as a profile: the resource must conform to each profile the IG makes apply to it
* Validator: FHIRPath document functions section(), entry(), entryIn(), resources(), resourcesIn(), sameSubject() and hasCoding() (local extensions), for invariants about what a document (e.g. an IPS) contains
* Validator: FHIRPath fixes - lowBoundary()/highBoundary() on decimals with exponents; = and ~ for quantities with no UCUM code; calendar durations year and month are the UCUM units a and mo for equivalence; substring(), skip(), take(), quantity ordering, dateTime comparison, unescape('json') and unicode escape edge cases now follow the spec; deeply nested expressions are rejected, and malformed expressions give informative errors
* Validator: Snapshot generation - a derived profile can reslice an inherited slice and then constrain a later inherited sibling slice ('Named items are out of order in the slice'); child constraints of a datatype under a content reference are kept, and a new extension under a nested content reference (e.g. Composition.section.section) no longer recurses into its parent
* Validator: contentReferenceProfile - applied after snapshot expansion, values must have a canonical before the '#', and an unresolvable value is an error (on the instance, and once on the StructureDefinition) instead of stopping validation
* Validator: when several versions of an extension definition are in scope, validate against the most recent one
* Validator: extensible binding messages now say a code SHALL come from the value set unless it has no suitable code
* Validator: a constraint on a type slicer that only makes sense for some of its types is a warning in the differential and a hint when only in the snapshot
* Validator: whitespace - leading/trailing whitespace is only reported for the characters XML treats as whitespace, and a whitespace-only value counts as a value, with new messages
* Validator: QuestionnaireResponse.item.text may match a translation of the Questionnaire.item.text
* Validator: new warning for CodeSystems with hierarchyMeaning = classified-with that don't set versionNeeded = true
* Validator: resourceDefinition on an additional resource is checked against the resource the profile constrains; stop at a loop in the baseDefinition chain
* Validator: a meta.profile with no value is a warning (was a crash), and a StructureDefinition must have a baseDefinition and derivation (logical models without them no longer crash)
* Validator: don't check the values of the xml-namespace extensions for URL resolution (#1416)
* Validator: fix validation of logical model instances published as Binary resources
* Validator: the -check-ips-codes check (SNOMED CT codes not in the IPS free set) has been removed
* Renderer: accessibility - markdown pages have their headings seated under the page title the template gives them (as narratives already are), CSS classes are used instead of inline styles (unchanged/removed content, target links), target links have an aria-label, and new constant fragments (ul/ol/table-grid/table-list open and close) for templates
* Renderer: new CodeSystem fragment `content-noprop` - the code system content without the properties table; CodeSystem property links are only skipped for properties with the no-link display hint (the check was inverted)
* Renderer: a Reference with the alternate-reference extension links to the target in the extension (#1423); Consent (R4/R4B) uses the R5 decision modifierExtension for the base rule (#1422)
* Renderer: Provenance renders anchors for contained resources; the history table's actor columns are in a stable (code system) order
* Renderer: the slicer rules display no longer depends on what was rendered before; markdown is rendered with URL sanitisation
* Renderer: markdown conformance clauses - plain-text clauses (§id:statement§) extract their ids correctly, and a comment holding "!§§" (or one in mixed content) no longer breaks the page
* Renderer: Czech translations for rendering phrases
* QA: performance - links to other specs are checked without rebuilding their file urls for every link
