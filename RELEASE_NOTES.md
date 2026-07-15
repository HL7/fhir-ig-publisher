* Loader: Fixed the log-loaded-resources parameter falling through into generate-version
* Documentation: Added multi-version-IGs.md describing R5 to R4/R4B publishing, per-version dependencies, and resource membership
* General change http: to https:
* Loader: Make sure binary loading works on contained resources too, adds SVG support and fixes JPEG support.
* Loader: improved logging for settings
* Validator: fix up caching for new sealed parameter
* Validator: fix version bug excluding codes and fix server side handling for excluded valuesets
* Validator: improve validation error message for retired codes
* Validator: fix tx server addresses to https:
* Validator: Fix cache-id double header issue
* Validator: rework cache shutdown
* Validator: fix htmlChecks() implementation on string in validator
* Validator: NPE fix for missing version when doing value set validation
* Validator: OID requirements don't apply to affiliates
* Versions: An IG authored in R5 can now emit downgraded R4 (4.0.1) and R4B (4.3.0) variant packages in a single run via generate-version, with a cross-version-analysis page (new CrossVersionAnalyser) reporting per-target conversion problems and intentional membership omissions
* Versions: Fixed generate-version R4B output being serialized as R4 bytes mislabeled 4.3.0 - R4B is now genuinely converted via the R4B factory/parser (behaviour change for existing R4B generate-version output)
* Versions: Added a per-version dependency extension on ImplementationGuide.dependsOn to override, add, or remove a dependency for a specific target FHIR version (replaces the previous package-id suffix rename, which is retained as a fallback)
* Versions: Added r4-inclusion / r4b-inclusion / r5-inclusion parameters to control which generated package(s) each resource lands in (tag-membership)
* Versions: The expected FHIR core dependency is now resolved by target version family, wrong-family core dependencies are removed, and no hl7.fhir.r?.core is added for unrecognized, ballot, or non-semver version codes
* Versions: generate-version and per-version dependency version tokens are now validated, with a clear error for an unrecognized FHIR version code instead of silently mis-targeting a package
* Versions: NPM package aliases (alias@npm:real.package) declared on a dependency are now preserved on the base and every per-version package
* Versions: The dependency table, publication checks, and generated package manifests now share one consistent per-target effective dependency view
* Versions: Each per-version package (and the base) now embeds an ImplementationGuide whose fhirVersion, packageId, dependsOn, and definition.resource membership match that target instead of the R5 source; the base package's embedded IG is now consistent with the variant packages
* Renderer: fix broken links in comparisons
* Web Publiahing: fix for publishing error that allows failed publishing to proceed
