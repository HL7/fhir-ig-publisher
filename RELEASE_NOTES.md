* Versions: An IG authored in R5 can now emit downgraded R4 (4.0.1) and R4B (4.3.0) variant packages in a single run via generate-version, with a cross-version-analysis page (new CrossVersionAnalyser) reporting per-target conversion problems and intentional membership omissions
* Versions: Fixed generate-version R4B output being serialized as R4 bytes mislabeled 4.3.0 - R4B is now genuinely converted via the R4B factory/parser (behaviour change for existing R4B generate-version output)
* Versions: Added a per-version dependency extension on ImplementationGuide.dependsOn to override, add, or remove a dependency for a specific target FHIR version (replaces the previous package-id suffix rename, which is retained as a fallback)
* Versions: Added r4-inclusion / r4b-inclusion / r5-inclusion parameters to control which generated package(s) each resource lands in (tag-membership)
* Loader: Fixed the log-loaded-resources parameter falling through into generate-version
* Documentation: Added multi-version-IGs.md describing R5 to R4/R4B publishing, per-version dependencies, and resource membership
