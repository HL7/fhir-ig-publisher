* Loader: Adjust default extensions pack to be last milestone, not last working document
* Loader: Fix bug loading resources that don't have an explicit id on windows
* Loader: Only report missing localization messages the first time (#2121)
* Loader: Support use of version specific packs for extensions
* Loader: Updated CQL translator version to 3.27.0 (#1127)
* Loader: fix bug mishandling sqlite index not being generated
* Loader: use CodeSystem comments instead of deprecated extension
* Languages: Minor Improvements to French Translations
* Snapshot Generation: Fix snapshot processing - missing inner elements in some circumstances
* Snapshot Generation: better handling for missing description in derived IGs when generating snapshots
* Cross-Version Support: Fix bug setting status on extensions for previous versions
* Cross-Version Support: Fix error where ExtensionContext of 'CanonicalResource' is not preserved when adapting extensions for past versions
* Cross-Version Support: fix problem genrating cross-version snapshots
* Terminology Sub-system: Change R3 client to use JSON work around weird IO error
* Terminology Sub-system: fix issue with "configured to not allow Iterating ValueSet resources"
* Validation: Add .debug(name) to FHIRPath engine for user convenience
* Validation: Better handling of xsi:type errors in CDA
* Validation: Fix HTML validation: Allow for col in colgroup
* Validation: Fix NPE in FHIRPath
* Validation: Fix bug checking for extension contexts on the wrong structure
* Validation: Fix bug overriding explicit language in multi-langual mode
* Validation: Fix problem with using wrong language code validating terminology (en-* didn't equate to null)
* Validation: Fix to handle CodeSystems with content=not-present when validating ValueSets
* Validation: Ignore binding missing for types that aren't bindable when checking profile CompliesWith extension
* Validation: Improve error message when code system is unknown
* Validation: Improve extension error messages
* Validation: Improve hash mismatch error
* Validation: Remove maxLength from prohibited properties for elements in specializations
* Validation: improve error message for invariants when running inside Publisher
* Renderer: Add class-table fragment
* Renderer: Fix errors in CDA narrative handling
* Renderer: Make XIG link translatable
* Renderer: Make closing elements hyperlinks too in marked up xml view
* Renderer: Render logical model example with link enabled rendering (like other resources)
* Renderer: Support for type operations rendering
* Renderer: allow suppress xig link
* Renderer: fix broken links in extension pack
* Renderer: fix encoding bug for ElementDefinition.examples
* Renderer: fix experimental extension message
* Renderer: fix experimental extension warning
* Renderer: fix namespace problem with CDA examples
* Publication Process: allow -tx parameter with -go-publish
* Publication Process: revise note about out-of-date publisher when publisher is a dev version
