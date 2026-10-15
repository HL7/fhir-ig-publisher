## General notes

* This release includes various performance improvements, thanks Vadim Peretokin
* From this release, examples are now written into the generated NPM packages in `package/examples/` (as the package spec says) instead of `package/example/`. But 1000s of packages have been published with examples in /example, so tools looking for examples must look in both places

## Specific Changes

* Loader: support for nested (child) IGs has been removed - the `-nested` parameter is now an error, and the `nestedIgConfig` and `nestedIgOutput` parameters are no longer used
* Loader: examples are now written to `package/examples/` (as the package spec says) instead of `package/example/`, and examples are read from either folder (#1373)
* Loader: fix handling of additional resources - resource types known to the model context are no longer dropped, and additional resources are converted to R4/R5 through the version convertor registry
* Loader: in R6+ IGs, the Testing IG resources (TestPlan etc) and ActorDefinition, Requirements and SubscriptionTopic are native, not R5 resources to be converted
* Loader: handle pre-loading packages for Sushi when a package name has an npm alias
* Validator: Snapshot generation: fix type slicing when a type specific name (e.g. valueQuantity) is used where the base restricts a choice element but doesn't type slice it (2.3.5 regression, seen in AU Core blood pressure), and report duplicate slice names and differential fixed/pattern values that conflict with the base profile
* Validator: Snapshot generation: a profile on an extension now takes the context of the extension it profiles when it doesn't state its own
* Validator: new ConceptMap mapping quality checks - consistency with the reverse ConceptMap when one is found in the context (equivalence, narrower/broader, noMap, source/target scope), and subsumption checks on sources and targets when the code system has an is-a hierarchy (hints for missing things, warnings for inconsistent things). Reverse maps are matched on versions as well as systems, a map from a code system to itself is only usable if versions or scopes distinguish source from target, and subsumption on a code system that is available locally is never sent to the terminology server
* Validator: FHIRPath type checking narrows types under `is` guards (e.g. `$this is dateTime implies $this.toString().length() >= 10`) through implies, and, or, xor, not and iif
* Validator: FHIRPath fixes - decimal comparison (only zeros after the decimal point are insignificant: `100 = 1.0` was true), toString() on a complex type returns empty, and element model decimals compare as decimals (1.0 = 1.00)
* Validator: allow `Base` as an extension context type
* Validator: fix loading R6 resources from references during validation
* Validator: improved error messages - FHIRPath functions that only work on primitives report the actual type, and invariant expression errors identify the element
* Renderer: fixed and pattern values (and minValue/maxValue) in profiles are now shown as JSON (with XML as well in the detailed view), primitives as the value with "(no extensions allowed)" or "(extensions allowed)", and Quantity collapsed to value, units and code
* Renderer: hierarchical tables use CSS classes instead of inline styles where possible (new pubpack 0.2.10) (for accessibility)
* Renderer: Bundle entry narratives are rendered with their top heading at h3, so a nested document Bundle no longer pushes the headings of the other entries one level too deep (#1404)
* Renderer: ConceptMaps between StructureDefinitions - fix links to [x] elements, link to the definitions page for core spec resources, datatypes and profiles, and show the element types instead of 'todo' (#1405)
* Renderer: narratives inline images (participant photos, Library content) as data: urls instead of writing files alongside, with a note for images too big to include, and ignore whitespace in base64 image data
* Renderer: an Identifier with no value shows the reason (data absent reason etc.), and more rendering phrases are translatable
* Renderer: profile usage now finds profiles used in modifierExtensions as well as extensions
* Renderer: messages no longer fall back to the JVM default locale when the requested language has no translations
* QA: performance fixes for qa-ipreview.html generation
* Web Publishing: fix publication crash when building redirects for the milestone build output
