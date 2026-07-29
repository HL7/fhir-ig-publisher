# Multi-version IGs

## 1 Introduction

An Implementation Guide is authored against a single primary FHIR version, but the
IG Publisher can, in a single run, additionally emit **downgraded variant packages**
for other FHIR versions so that implementers on those versions can consume the IG.

This document describes emitting **R4 (4.0.1)** and **R4B (4.3.0)** consumable packages
from an IG **authored in R5 (5.0.0)**. Existing R4↔R4B behaviour (see
[the legacy path](#6-relationship-to-the-legacy-r4r4b-path)) is unchanged.

The variant packages are produced by the semantic `generate-version` conversion path,
which uses the shared version-conversion machinery (`ProfileVersionAdaptor` and the
`VersionConvertorFactory_*` factories). No new element-level converters are involved -
this is about enabling R5 as a source version, per-version wiring, configuration,
analysis and reporting.

## 2 Selecting the target versions

Add one `generate-version` guidance parameter to the IG for each FHIR version you want
a variant package for. For an R5 IG that should ship R4 and R4B:

```json
"parameter" : [
  { "code" : "generate-version", "value" : "r4" },
  { "code" : "generate-version", "value" : "r4b" }
]
```

Each target produces an extra package `<packageId>.<token>.tgz` (e.g. `<id>.r4.tgz`,
`<id>.r4b.tgz`) alongside the R5 `package.tgz`.

### Version tokens

Everywhere a FHIR version is named (the `generate-version` value, the per-version
dependency extension, and the inclusion parameters) the token is matched by **version
family**, so all of the following are accepted and collate to the same target:

| Family | Accepted tokens                 | Recommended |
|--------|---------------------------------|-------------|
| R4     | `r4`, `4.0`, `4.0.1`            | `r4`        |
| R4B    | `r4b`, `4.3`, `4.3.0`          | `r4b`       |
| R5     | `r5`, `5.0.0`                  | `r5`        |

Use `r4` / `r4b` as the `generate-version` tokens so the produced file names are
`<id>.r4.tgz` / `<id>.r4b.tgz`.

## 3 Per-version dependencies

The dependencies a downgraded R4/R4B package needs are usually **not** a mechanical
rename of the R5 dependencies - they frequently have different package ids and/or
versions, and some dependencies only make sense for a particular FHIR version.

Per-version dependencies are declared with a single repeating extension on each
`ImplementationGuide.dependsOn` entry:

`http://hl7.org/fhir/tools/StructureDefinition/ig-dependency-for-version`

with these sub-extensions:

| Part          | Card. | Type   | Meaning                                                    |
|---------------|-------|--------|------------------------------------------------------------|
| `fhirVersion` | 1..1  | code   | the target version this occurrence describes               |
| `packageId`   | 0..1  | id     | override the dependency's package id for that version      |
| `version`     | 0..1  | string | override the dependency's package version for that version |
| `use`         | 0..1  | code   | `override` (default) or `remove`                           |

Semantics for a given target version `V` (a `generate-version` target, or the base
version for the R5 package itself):

* **Override** - the entry has an occurrence for `V`: apply its `packageId`/`version`
  overrides (a bare occurrence keeps the authored values).
* **Remove** - the entry has an occurrence for `V` with `use = remove`: the entry is
  absent from `V`.
* **Add (version-specific)** - the entry has occurrences for some versions but **not**
  `V`: the entry is absent from `V`. This is how a dependency that applies only to R4
  is declared - author a normal `dependsOn` row and add one occurrence for `r4`; it is
  then present only in the R4 package and absent from the R5 and R4B packages.
* **Legacy** - the entry has **no** such extension: it applies to every version, and for
  each variant the historical package-id suffix rename applies (`.r5` &rarr; `.r4`,
  with R4B forced to `.r4` for wire-compatibility).

The effective per-version dependencies drive each variant package's `dependsOn` and are
surfaced in the rendered dependency table. A version-scoped entry is never loaded,
validated, rendered, or packaged for a version it does not apply to.

## 4 Per-version resource membership

By default every resource is written into every generated package. To scope a resource
to particular version(s), use the inclusion parameters:

* `r4-inclusion` - value is a resource `Type/id` or canonical URL
* `r4b-inclusion`
* `r5-inclusion`

These use **tag-membership** semantics:

* A resource listed in **any** inclusion set appears **only** in the listed version(s).
* A resource listed in **no** inclusion set appears in **all** versions (the default).

`r5-inclusion` also gates the base R5 package, so a resource can be scoped away from R5
entirely (e.g. an R4-only resource authored in R5). Membership governs *package*
membership only; the R5 site rendering is unchanged.

> The legacy `r4-exclusion` / `r4b-exclusion` parameters are unrelated to this feature -
> they continue to drive the legacy R4↔R4B path only and are not consulted for an R5 base.

## 5 Cross-version analysis

For an R5 base with `generate-version`, a `cross-version-analysis` page reports, per
target version:

* conversion problems (from the `ProfileVersionAdaptor` conversion log for
  StructureDefinition/SearchParameter, and from conversion success/failure for the other
  conformance types and examples), and
* resources intentionally omitted from that target via the inclusion parameters.

A resource that uses an R5-only type that cannot be represented in R4/R4B yields a
**warning** on this page (not a build failure); use the inclusion parameters to exclude
it from the target(s) where it does not belong.

## 6 Relationship to the legacy R4/R4B path

An IG whose base version is **R4 or R4B** continues to use the original cross-version
mechanism (`R4ToR4BAnalyser`), which re-parses the built package with the R4B parser and
clones it into `.r4.tgz` / `.r4b.tgz`. That path is unchanged and is only entered for an
R4/R4B base; an R5 base always uses the `generate-version` path described above.
