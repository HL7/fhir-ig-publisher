---
title: FHIR IGPublisher Release Notes
---

# FHIR IGPublisher Release Notes

## Current (not released yet)

* Validator: fix fatal NPE validating bundles when resource is missing
* Validator: Fix to check invariants on Elements with type redirections (e.g. ValueSet.compose.exclude)
* Renderer: fix rendering of ValueSet exclusions

## v1.0.94 (2020-05-12)

* Release for version of main FHIR build

## v1.0.93 (2020-05-11)

* update to v4.4.0 internally

## v1.0.92 (2020-05-10)


* Package System: Change to use different secondary package server
* Renderer: Improve wording for dode systems and value sets that aren't used

## v1.0.91 (2020-05-09)


* Fix bug processing redirects for UTG release

## v1.0.90 (2020-05-08)

* fix bug checkling link messages 
* improve error message for syntax issues in warnings file
* Fix bug checking publish box for non-HL7 implementation guides 
* Fix bug not consistently populating version in data file for templates

## v1.0.89 (2020-05-06)


* Validator: Update cross-version extension support for new preview of R5 published
* Validator: Check proper use of urn:ietf:rfc:3986 identifiers
* Renderer: Fix rendering bug in value set definitions

## v1.0.88 (2020-05-02)

* Bump version for new preview release of R5

## v1.0.87 (2020-05-02)

* Fix issue with CQL dependencies
* Fix for missing actors in history after first row

## v1.0.86 (2020-05-01)


* Change history approach to use Provenance
* Fix JAXB dependencies


## v1.0.85 (2020-04-29)


* Validator: Fix problem evaluating "type" discriminators ending with .resolve()
* Actually fix NPE loading some bundles

## v1.0.84 (2020-04-29)


* Validator: add icd-9-cm to list of known URIs
* Renderer: Generate Narrative correctly for ContactDetails
* Renderer: change title of ValueSet display from "Definition" to "Logical Definition (CLD)"
* Fix NPE loading some bundles
* Add support for a history view on resources

## v1.0.82 (2020-04-23)


* Terminology Sub-system: fix problem expanding flat code systems part #2 
* Terminology Sub-system: fix problem with abstract concepts not appearing in code system expansions

## v1.0.81 (2020-04-21)

* Package Manager: Fix Accept header when using package server
* Version Conversion: fix bug converting primitive types with no value (extensions only) between versions
* Terminology Sub-system: Allow expansions based on code system fragments
* Terminology Sub-system: fix problem expanding flat code systems
* Terminology Sub-system: fix version note when multiple versions of the same code system
* Validator: Better URL validation
* Validator: Fix using a FHIRPath context in an extension in a Bundle
* Validator: Add support for R5 extensions validating cross-version extensions
* Renderer: Add content mode to rendering of CodeSystem
* CQL Subsystem: Added support for model, code system and value set dependencies
* Publisher: start introducing US Realm Business rules

## v1.0.80 (2020-04-12)

* Terminology Sub-system: pass too-costly note on when including value sets
* Renderer: Improve rendering of value set version dependencies
* Renderer: Add All codes value set to rendering of code system
* Renderer: fix bugs rendering Lists
* Improved rendering of filtered messages and group messages by type in qa.html

## v1.0.79 (2020-04-09)

* Package Manager: fix to handle UTG terminology correctly 
* Validator: Better error for wrong text in XML instance
* Publisher: Show line/col number for issues in qa.html

## v1.0.78 (2020-04-06)

* SnapShot generator: fix NPE when element mapping is "" (illegal, but still shouldn't cause an NPE)
* Validator: more work on validating Measure & MeasureReport 
* Validator: Change validator so root resource id is not in the same space as all other ids
* Validator: Add type to path statement when validating bundles for easier human understanding
* Validator: Fix bug determining system for a bound code where there is an exclude
* Renderer: Improve List Rendering
* Renderer: Fix link to maturity list (more work required on this link)
* Add support for automatically inserting binary files (easier to edit - see https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters parameter path-binary)
* Add support for compiling CQL and updating library resources with ELM and dependency information from the CQL

## v1.0.77 (2020-04-02)


* Validator: Add a warning if a coding has a code but no system
* Validator: Add check for duplicate ids 
* Validator: Validate MeasureReport against it's Measure
* Validator: Check that Canonical URLs are absolute 
* Publisher: change the format of the suppressed messages file - see https://confluence.hl7.org/display/FHIR/Implementation+Guide+Parameters for details
* Publisher: support // comments in json source for input resources (will be stripped out when publishing)
* Publisher: trust UTG template

## v1.0.76 (2020-03-31)


* Validator: Fix problem validation questionnaire items 
* Validator: fix problem validating bundles in references 
* Renderer: fix problem rendering expansion with multiople versions of the same code system
* Template Manager: fix problem with missing liquid template directory
* Publisher: better error handling when snapshot generation fails
* Publisher: fix problem processing Sushi output error count


(no changes yet)

## v1.0.75 (2020-03-28)


* Snapshot Generator: fix internal exception with missing type in R3
* Validator: Fix for R3 extension context of Any
* Validator: Better error message when encountering ```null``` in json format
* Renderer: add missing short definitions from differential format
* Renderer: Change the rules around generation of value set CLD to allow CLD to be supplied by the narrative
* Renderer: Fix problem where generated narratives get links with script syntax in them 
* Publisher: fix problem launching Sushi

## v1.0.74 (2020-03-27)


* Change the way Sushi integration is handled to allow the -ig parameter to nominate ig.ini, whether it exists or not

## v1.0.73 (2020-03-26)


* Validator: More validation of XML syntax + encoding + version + URLs in XHTML ```a``` and ```img```
* Support for pre-processing using Sushi (put sushi content in /fsh in ig root folder; see Sushi documentation for further details)


## v1.0.72 (2020-03-17)


* Snapshot Generator: fix bugs generating 1.4.0 extensions 
* Renderer: make code system properties that are URLs hotlinks in the html

## v1.0.71 (2020-03-13)

* Package Manager: check version before checking cache if no version specified when loading a package
* Version Conversion: Fix issue with processing R4 concept maps with relationship type = relatedto
* Snapshot Generator: fix problem with bad maps from core spec
* Validator: Check that a Json Primitive is actually a list when it should be
* Publisher: Auto-populate modifierReason on modifierExtensions
* Publisher: Fix to support R5 implementation guides.
* Publisher: Improvements to ValueSet definition rendering

## v1.0.70 (2020-03-05)

* Version Conversion: Add support for MedicinalProductDefinition
* Validator: Support for criteria on exists() in invariants
* Validator: Do not omit invariants that have a stated source
* Publisher: more fixes for codesystem property rendering
* Publisher: fix bug rendering value set with missing code on concept

## v1.0.69 (2020-03-03)

* Publisher: improve codesystem property rendering

## v1.0.68 (2020-02-28)


* Template Sub-system: Fix problem loading templates on unix/macOS
* Validator: Support for slicing by patternCoding

## v1.0.67 (2020-02-25)

* Template Sub-system: support referring to github branches
* Publisher: fix for various bugs reported in loading templates and dealing with missing value sets

## v1.0.66 (2020-02-22)

* NPM sub-system: fix package subsystem for challenge with hl7.fhir.au.base setup
* Publisher: improve rendering of concept map (relating to the directionality of the relationship codes)
* Publisher: Add hl7.be.fhir.template to the list of trusted templates

## v1.0.65 (2020-02-19)

* NPM sub-system: Change to use http://packages.fhir.org
* Java Core: Fix problem loading xml:lang from narrative in some cases
* Version Conversion: Fix problem converting PlanDefinition.action.definition between R4 and R5
* Validator: Allow search references in transactions
* Publisher: Fix rendering of partial bindings in differentials 
* Publisher: Don't report errors for tel: URLs
* Publisher: fix problem generating broken links in bundle rendering
* Publisher: remove extended checks for bad URLs from old versions of IGs? (review needed)
* Publisher: fix rendering of older version ConceptMaps (equivalence)
* Publisher Utils: Implement template release process

## v1.0.64 (2020-02-13)

* Renderer: Workaround NPE in summary renderer for profiles
* Narrative Generation: Fix generator to add both lang and xml:lang per https://www.w3.org/TR/i18n-html-tech-lang/#langvalues (actually get it in the right place this time)
* Publisher: handle bad profile reference in ImplementationGuide.manifest.resource.exampleFor better

## v1.0.63 (2020-02-13)

* NPM sub-system: Fix IHE template to work
* NPM sub-system: Enforce that package versions can only contain the characters a-zA-Z0-9-. or else start with file: followed by a valid local file system reference
* Snap-shot generator: Fix a bug where a differential caused an NPE in the snapshot-generator
* Snap-shot generator: Improve handling of circular dependencies in profiles (better error reporting, less errors)
* Version Conversion: Restructure the internal version conversion routines to convert extensions more faithfully (and be easier to manage)
* Narrative Generation: Fix generator to add both lang and xml:lang per https://www.w3.org/TR/i18n-html-tech-lang/#langvalues
* Narrative Generation: Fix the generator handle Concept Maps with missing tragets 
* Validator: Fix warnings around xhtml language to cover both lang and xml:lang (see https://www.w3.org/TR/i18n-html-tech-lang/#langvalues)
* Validator: Questionnaire.item.enableWhen validation - stop producing spurious warnings about errors, and check enableWhen in descendent questions (was being ignored)
* Publisher: Ignore files starting with . when scanning for resources
* Publisher: Add opt-out support for stats to IG as a parameter
* Publisher: fix so empty context/contacts/jurisdictions in the IG don't clear these in other resources

## v1.0.62 (2020-02-07)

* Publisher: Fix problem loading address of current packages
* Snapshot generation: handle profiles on Bundle.entry.resource properly

## v1.0.61 (2020-02-02)

* This 