# HTA Code System Copyright Rules

This document describes the per–code-system rules that the IG Publisher applies when
performing **HTA (HL7 Terminology Authority) License Conformance Analysis**. For each
non-HL7 code system referenced by a ValueSet or ConceptMap in an HL7 Implementation
Guide, the Publisher checks two things:

1. **Approval** — whether the code system is approved by the HTA for use in an HL7 IG.
2. **Copyright** — whether resources that use the code system carry an acceptable
   copyright statement.

These rules are maintained here so they can be reviewed and changed through pull
requests. The rules in this document are the source of truth; the implementation lives
in
[`HTAAnalysisRenderer.java`](org.hl7.fhir.publisher.core/src/main/java/org/hl7/fhir/igtools/renderers/HTAAnalysisRenderer.java)
(methods `getIsApproved` and `getApprovedCopyright`). **A change to these rules must be
reflected in both places.**

> **Note:** This analysis is developed jointly with the HTA committee and is subject to
> change. Editors may use it to find errors in their definitions, but should not treat
> the analysis as final.

## How the rules work

### Approval status

A code system has one of three approval states:

| State | Meaning |
|-------|---------|
| **Approved** | Explicitly approved by the HTA for use in an HL7 IG. |
| **Not approved** | Explicitly *not* approved. Any use is flagged as an error. |
| **To be resolved** | No decision recorded yet. Use is permitted but reported as unresolved. |

Only code systems listed below as **Approved** are approved. Every other non-HL7 code
system defaults to **To be resolved**.

### Copyright expectations

Each code system has an expected-copyright rule. When a ValueSet (or ConceptMap) includes
content from the system, the Publisher compares the resource's copyright statement
against this rule:

| Rule | Meaning | Check applied |
|------|---------|---------------|
| **(unspecified)** | No expectation recorded. | Always passes. |
| **Must be empty** | The resource should **not** carry a copyright statement. | Passes only if the resource has no copyright. |
| **Required (any wording)** | The resource **must** carry a copyright, but no specific wording is required. | Passes if any copyright is present. |
| **Required (specific text)** | The resource's copyright must **contain** one of the approved statements below. | Passes if the resource copyright contains any one of the listed statements (substring match). |

For "Required (specific text)", several alternative statements may be acceptable; a
resource only needs to contain **one** of them.

---

## Approved code systems

The following code systems are explicitly **approved by the HTA**.

### SNOMED CT

- **System URL:** `http://snomed.info/sct`
- **Approval:** Approved
- **Copyright:** Required — the resource copyright must contain one of the following:

  > This value set includes content from SNOMED CT, which is copyright © 2002+ International Health Terminology Standards Development Organisation (IHTSDO), and distributed by agreement between IHTSDO and HL7. Implementer use of SNOMED CT is not covered by this agreement

  > The SNOMED International IPS Terminology is distributed by International Health Terminology Standards Development Organisation, trading as SNOMED International, and is subject the terms of the [Creative Commons Attribution 4.0 International Public License](https://creativecommons.org/licenses/by/4.0/). For more information, see [SNOMED IPS Terminology](https://www.snomed.org/snomed-ct/Other-SNOMED-products/international-patient-summary-terminology)

  > The HL7 International IPS implementation guides incorporate SNOMED CT®, used by permission of the International Health Terminology Standards Development Organisation, trading as SNOMED International. SNOMED CT was originally created by the College of American Pathologists. SNOMED CT is a registered trademark of the International Health Terminology Standards Development Organisation, all rights reserved. Implementers of SNOMED CT should review [usage terms](https://www.snomed.org/get-snomed) or directly contact SNOMED International: info@snomed.org

### LOINC

- **System URL:** `http://loinc.org`
- **Approval:** Approved
- **Copyright:** Required — the resource copyright must contain:

  > This material contains content from LOINC (http://loinc.org). LOINC is copyright © 1995-2020, Regenstrief Institute, Inc. and the Logical Observation Identifiers Names and Codes (LOINC) Committee and is available at no cost under the license at http://loinc.org/license. LOINC® is a registered United States trademark of Regenstrief Institute, Inc

---

## Code systems with copyright rules but no approval decision

These code systems have an expected copyright statement, but the HTA has **not** recorded
an approval decision, so their use is reported as **To be resolved**.

### CPT (Current Procedural Terminology)

- **System URL:** `http://www.ama-assn.org/go/cpt`
- **Approval:** To be resolved
- **Copyright:** Required — the resource copyright must contain:

  > Current Procedural Terminology (CPT) is copyright 2020 American Medical Association. All rights reserved

### ATC (Anatomical Therapeutic Chemical classification)

- **System URL:** `http://www.whocc.no/atc`
- **Approval:** To be resolved
- **Copyright:** Required — the resource copyright must contain:

  > This artifact includes content from Anatomical Therapeutic Chemical (ATC) classification system. ATC codes are copyright World Health Organization (WHO) Collaborating Centre for Drug Statistics Methodology. Terms & Conditions in https://www.whocc.no/use_of_atc_ddd/

### ISCO (International Standard Classification of Occupations)

- **System URL:** `urn:oid:2.16.840.1.113883.2.9.6.2.7`
- **Approval:** To be resolved
- **Copyright:** Required — the resource copyright must contain:

  > This artifact includes content from International Standard Classification of Occupations (ISCO). ISCO is copyright International Labour Organization (ILO). Terms & Conditions in http://www.ilo.org/global/copyright/lang--en/index.htm

### EDQM Standard Terms

- **System URL:** `http://standardterms.edqm.eu`
- **Approval:** To be resolved
- **Copyright:** Required — the resource copyright must contain:

  > This artifact includes content from EDQM Standard Terms. EDQM Standard Terms are copyright European Directorate for the Quality of Medicines. Terms & Conditions in https://www.edqm.eu/en/standard-terms-database

---

## Code systems handled elsewhere (not analysed)

The following are **not** subject to this analysis:

- **HL7 systems** — any system whose URL starts with `http://terminology.hl7.org` or
  `http://hl7.org/fhir`.
- **Internal code systems** — any code system defined within the IG being built.
- **`hl7.terminology` (UTG)** — exempt from terminology dependency analysis.
- **Non-HL7 IGs** — IGs whose package id does not start with `hl7.` are exempt.

---

## Proposing a change

To add a new code system or change an existing rule, open a pull request that edits
**this document** and the corresponding entries in `getIsApproved` (approval) and
`getApprovedCopyright` (copyright) in `HTAAnalysisRenderer.java`. Please include in the
PR description the rationale and any reference to the relevant HTA decision.
