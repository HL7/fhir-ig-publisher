package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.hl7.fhir.model.core.ElementDefinition;
import org.hl7.fhir.model.core.Extension;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.ExtensionContextType;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.model.extensions.ExtensionDefinitions;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;

/**
 * Checks the id of an extension defined in the extensions pack (hl7.fhir.uv.extensions) against
 * the standard naming pattern defined in FHIR-43753:
 *
 * <ul>
 *   <li>If the context is a single resource or type, the id starts with the resource/type name in all lowercase</li>
 *   <li>If the context is only resources from a single pattern (Request, Definition, Event, CanonicalResource, etc.),
 *       the id starts with the pattern name in all lowercase</li>
 *   <li>Otherwise, there may be one of the standard prefixes (artifact, cqf, iso21090, openEHR, workflow)</li>
 *   <li>If there is a prefix, it is followed by a dash</li>
 *   <li>After that, there is a qualifier that uniquely names the extension within the context, expressed as lowerCamelCase</li>
 * </ul>
 *
 * Extensions that existed when the rule was introduced (2026-10-03) are exempt.
 */
public class ExtensionNamingChecker {

  public static final String EXTENSIONS_PACKAGE_ID = "hl7.fhir.uv.extensions";

  private static final String[] STANDARD_PREFIXES = { "artifact", "cqf", "iso21090", "openEHR", "workflow" };

  private static final String QUALIFIER = "[a-z][a-zA-Z0-9]*";

  /**
   * The extensions that were defined in the extensions pack on 2026-10-03, when this rule was introduced.
   * These are not checked
   */
  private static final Set<String> EXISTING_EXTENSIONS = new HashSet<>(Arrays.asList(
      "11179-objectClass", "11179-objectClassProperty", "11179-permitted-value-conceptmap",
      "11179-permitted-value-valueset", "DeviceDefinition-partVariableCount", "additional-language",
      "additional-operation-context", "additional-operation-parameter-allowedType",
      "additional-operation-parameter-targetProfile", "additional-resource-compartment",
      "additional-resource-reference-target", "additionalIdentifier", "additionalImplicitRules",
      "address-classification", "address-official", "allergyintolerance-abatement", "allergyintolerance-assertedDate",
      "allergyintolerance-certainty", "allergyintolerance-duration", "allergyintolerance-reasonRefuted",
      "allergyintolerance-resolutionAge", "allergyintolerance-substanceExposureRisk", "alternate-canonical",
      "alternate-codes", "alternate-hash", "alternate-reference", "annotationType",
      "artifact-approvalDate", "artifact-author", "artifact-authoritativeSource", "artifact-canonicalReference",
      "artifact-citeAs", "artifact-contact", "artifact-contactDetailReference", "artifact-copyright",
      "artifact-copyrightLabel", "artifact-date", "artifact-description", "artifact-editor", "artifact-effectivePeriod",
      "artifact-endorser", "artifact-experimental", "artifact-extended-contact-detail", "artifact-identifier",
      "artifact-isOwned", "artifact-jurisdiction", "artifact-lastReviewDate", "artifact-name",
      "artifact-periodDuration", "artifact-publicationDate", "artifact-publisher", "artifact-purpose",
      "artifact-reference", "artifact-relatedArtifact", "artifact-releaseDescription", "artifact-releaseLabel",
      "artifact-reviewer", "artifact-status", "artifact-title", "artifact-topic", "artifact-uriReference",
      "artifact-url", "artifact-usage", "artifact-useContext", "artifact-version", "artifact-versionAlgorithm",
      "artifact-versionPolicy", "artifactassessment-content", "artifactassessment-disposition",
      "artifactassessment-workflowStatus", "auditevent-Accession", "auditevent-AlternativeUserID",
      "auditevent-Anonymized", "auditevent-Encrypted", "auditevent-Instance", "auditevent-Lifecycle", "auditevent-MPPS",
      "auditevent-NumberOfInstances", "auditevent-OnBehalfOf", "auditevent-ParticipantObjectContainsStudy",
      "auditevent-SOPClass", "authorization-hint", "binding-concept-domain",
      "biologicallyderivedproduct-collection-procedure", "biologicallyderivedproduct-intendedRecipient",
      "biologicallyderivedproduct-manipulation", "biologicallyderivedproduct-processing", "blinded-role", "bodySite",
      "businessEvent", "canonicalresource-short-description", "capabilities", "capabilitystatement-declared-profile",
      "capabilitystatement-expectation", "capabilitystatement-prohibited", "capabilitystatement-search-mode",
      "capabilitystatement-search-parameter-combination", "capabilitystatement-search-parameter-use",
      "capabilitystatement-supported-system", "capabilitystatement-websocket", "careplan-activity-title",
      "careteam-alias", "characteristicExpression", "citation-societyAffiliation", "codeOptions",
      "codesystem-alternate", "codesystem-author", "codesystem-authoritativeSource", "codesystem-concept-comments",
      "codesystem-conceptOrder", "codesystem-effectiveDate", "codesystem-expirationDate", "codesystem-globalLangPack",
      "codesystem-history", "codesystem-keyWord", "codesystem-label", "codesystem-map", "codesystem-otherName",
      "codesystem-properties-mode", "codesystem-property-valueset", "codesystem-replacedby",
      "codesystem-sourceReference", "codesystem-trusted-expansion", "codesystem-usage", "codesystem-use-markdown",
      "codesystem-warning", "codesystem-workflowStatus", "coding-conformance", "coding-purpose", "coding-sctdescid",
      "communication-media", "communicationrequest-initiatingLocation", "complies-with-canonical",
      "composition-clinicaldocument-otherConfidentiality", "composition-clinicaldocument-versionNumber",
      "composition-section-subject", "concept-bidirectional", "condition-assertedDate", "condition-diseaseCourse",
      "condition-dueTo", "condition-occurredFollowing", "condition-outcome", "condition-related", "condition-reviewed",
      "condition-ruledOut", "confidential", "consent-NotificationEndpoint", "consent-ResearchStudyContext",
      "consent-Transcriber", "consent-Witness", "consent-location", "consent-provision-expression",
      "consent-provision-limit", "contactpoint-area", "contactpoint-comment", "contactpoint-country",
      "contactpoint-extension", "contactpoint-local", "contactpoint-multiple-use", "contactpoint-purpose",
      "contentReferenceProfile", "cqf-alternativeExpression", "cqf-artifactComment", "cqf-calculatedValue",
      "cqf-cdsHooksEndpoint", "cqf-certainty", "cqf-citation", "cqf-contactAddress", "cqf-contactReference",
      "cqf-contributionTime", "cqf-cqlAccessModifier", "cqf-cqlOptions", "cqf-cqlType", "cqf-criteriaReference",
      "cqf-defaultValue", "cqf-definitionTerm", "cqf-directReferenceCode", "cqf-encounterClass", "cqf-encounterType",
      "cqf-expansionParameters", "cqf-expression", "cqf-fhirQueryPattern", "cqf-improvementNotationGuidance",
      "cqf-initialValue", "cqf-initiatingOrganization", "cqf-initiatingPerson", "cqf-inputParameters",
      "cqf-isEmptyList", "cqf-isEmptyTuple", "cqf-isPrefetchToken", "cqf-isPrimaryCitation", "cqf-isSelective",
      "cqf-knowledgeCapability", "cqf-knowledgeRepresentationLevel", "cqf-library", "cqf-libraryAlias",
      "cqf-logicDefinition", "cqf-measureInfo", "cqf-messages", "cqf-modelInfo-isIncluded",
      "cqf-modelInfo-isRetrievable", "cqf-modelInfo-label", "cqf-modelInfo-primaryCodePath", "cqf-modelInfoSettings",
      "cqf-notDoneValueSet", "cqf-parameterDefinition", "cqf-partOf", "cqf-publicationDate", "cqf-publicationStatus",
      "cqf-qualityOfEvidence", "cqf-receivingOrganization", "cqf-receivingPerson", "cqf-recipientLanguage",
      "cqf-recipientType", "cqf-relatedRequirement", "cqf-relativeDateTime", "cqf-resourceType", "cqf-scope",
      "cqf-shouldTraceDependency", "cqf-strengthOfRecommendation", "cqf-supportedCqlVersion", "cqf-supportingEvidence",
      "cqf-supportingEvidenceDefinition", "cqf-systemUserLanguage", "cqf-systemUserTaskContext", "cqf-systemUserType",
      "cqf-targetInvariant", "cqf-testArtifact", "cqf-valueFilter", "cqm-ValidityPeriod", "data-absent-reason",
      "datatype", "datatype-short-string", "derivation-reference", "designNote", "detectedissue-doseType",
      "device-alertDetection", "device-commercialBrand", "device-conformsTo-source", "device-endpoint",
      "device-gateway", "device-implantStatus", "device-lastmaintenancetime", "device-maintenanceresponsibility",
      "device-operation-cycle", "device-operation-duration", "device-operation-mode",
      "devicerequest-patientInstruction", "diagnosticReport-addendumOf", "diagnosticReport-extends",
      "diagnosticReport-focus", "diagnosticReport-locationPerformed", "diagnosticReport-replaces",
      "diagnosticReport-risk", "diagnosticReport-summaryOf", "diagnosticReport-workflowStatus", "display",
      "documentreference-sourcepatient", "documentreference-thumbnail", "dosage-conditions",
      "dosage-minimumGapBetweenDose", "elementSource", "elementdefinition-allowedUnits",
      "elementdefinition-bestpractice", "elementdefinition-bestpractice-explanation", "elementdefinition-bindingName",
      "elementdefinition-conceptmap", "elementdefinition-defaulttype", "elementdefinition-equivalence",
      "elementdefinition-graphConstraint", "elementdefinition-identifier",
      "elementdefinition-inheritedExtensibleValueSet", "elementdefinition-isCommonBinding",
      "elementdefinition-maxValueSet", "elementdefinition-minValueSet", "elementdefinition-namespace",
      "elementdefinition-pattern", "elementdefinition-profile-element", "elementdefinition-question",
      "elementdefinition-selector", "elementdefinition-suppress", "elementdefinition-translatable",
      "elementdefinition-type-must-support", "encounter-associatedEncounter", "encounter-modeOfArrival",
      "encounter-reasonCancelled", "encounter-recordLinkage", "endpoint-fhir-version", "entered-in-error-status",
      "entryFormat", "event-basedOn", "event-eventHistory", "event-location", "event-partOf", "event-performerFunction",
      "event-recorded", "event-statusReason", "evidence-variable-handling-detail", "expression-coding",
      "extended-contact-availability", "extension-quantity-translation", "external-communication",
      "family-member-history-genetics-observation", "family-member-history-genetics-parent",
      "family-member-history-genetics-sibling", "familymemberhistory-abatement", "familymemberhistory-patient-record",
      "familymemberhistory-severity", "familymemberhistory-type", "feature-assertion", "firstCreated", "flag-detail",
      "flag-priority", "geolocation", "goal-acceptance", "goal-reasonRejected", "goal-relationship",
      "healthcareservice-schedulable", "hla-genotyping-results-allele-database", "hla-genotyping-results-glstring",
      "hla-genotyping-results-haploid", "hla-genotyping-results-method", "http-response-header",
      "humanname-assembly-order", "humanname-fathers-family", "humanname-mothers-family", "humanname-own-name",
      "humanname-own-prefix", "humanname-partner-name", "humanname-partner-prefix", "identifier-checkDigit",
      "identifier-jurisdiction", "identifier-validDate", "immunization-procedure", "implementationguide-sourceFile",
      "individual-genderIdentity", "individual-pronouns", "individual-recordedSexOrGender", "inherit-obligations",
      "intended-context", "iso21090-AD-use", "iso21090-ADXP-additionalLocator",
      "iso21090-ADXP-buildingNumberSuffix", "iso21090-ADXP-careOf", "iso21090-ADXP-censusTract",
      "iso21090-ADXP-delimiter", "iso21090-ADXP-deliveryAddressLine", "iso21090-ADXP-deliveryInstallationArea",
      "iso21090-ADXP-deliveryInstallationQualifier", "iso21090-ADXP-deliveryInstallationType",
      "iso21090-ADXP-deliveryMode", "iso21090-ADXP-deliveryModeIdentifier", "iso21090-ADXP-direction",
      "iso21090-ADXP-houseNumber", "iso21090-ADXP-houseNumberNumeric", "iso21090-ADXP-postBox",
      "iso21090-ADXP-precinct", "iso21090-ADXP-streetAddressLine", "iso21090-ADXP-streetName",
      "iso21090-ADXP-streetNameBase", "iso21090-ADXP-streetNameType", "iso21090-ADXP-unitID", "iso21090-ADXP-unitType",
      "iso21090-EN-qualifier", "iso21090-EN-representation", "iso21090-EN-use", "iso21090-PQ-translation",
      "iso21090-SC-coding", "iso21090-TEL-address", "iso21090-codedString", "iso21090-nullFlavor", "iso21090-preferred",
      "iso21090-uncertainty", "iso21090-uncertaintyType", "itemWeight", "language", "largeValue", "lastSourceSync",
      "list-category", "list-changeBase", "list-for", "location-boundary-geojson", "location-communication",
      "location-distance", "match-grade", "maxDecimalPlaces", "maxSize", "maxValue", "measurereport-category",
      "measurereport-countQuantity", "measurereport-populationDescription",
      "medication-characteristic", "medication-classification", "medication-manufacturer",
      "medication-manufacturingBatch", "medication-type", "medicationdispense-quantityRemaining",
      "medicationdispense-refillsRemaining", "messageheader-response-request", "metadataresource-publish-date",
      "mimeType", "minLength", "minValue", "namingsystem-checkDigit", "narrative-language-control",
      "narrative-source-control", "narrativeLink", "no-fixed-address", "note", "nutritionorder-adaptiveFeedingDevice",
      "nutritionorder-serviceCategory", "oauth-uris", "obligation", "obligation-profile-flag", "obligations-profile",
      "observation-analysis-date-time", "observation-bodyPosition", "observation-componentCategory",
      "observation-delta", "observation-deviceCode", "observation-focusCode", "observation-gatewayDevice",
      "observation-geneticsAllele", "observation-geneticsAminoAcidChange", "observation-geneticsAncestry",
      "observation-geneticsCopyNumberEvent", "observation-geneticsDNARegionName", "observation-geneticsGene",
      "observation-geneticsGenomicSourceClass", "observation-geneticsInterpretation", "observation-geneticsPhaseSet",
      "observation-geneticsVariant", "observation-human-transcribed", "observation-nature-of-abnormal-test",
      "observation-precondition", "observation-reagent", "observation-replaces", "observation-secondaryFinding",
      "observation-sequelTo", "observation-specimenCode", "observation-structure-type",
      "observation-structureLaterality", "observation-supportingDevice", "observation-timeOffset",
      "observation-v2-subid", "observationdefinition-supportingDevice", "openEHR-administration", "openEHR-careplan",
      "openEHR-exposureDate", "openEHR-exposureDescription", "openEHR-exposureDuration", "openEHR-location",
      "openEHR-management", "operationdefinition-allowed-type", "operationdefinition-profile",
      "operationoutcome-authority", "operationoutcome-detectedIssue", "operationoutcome-file",
      "operationoutcome-instance-id", "operationoutcome-issue-col", "operationoutcome-issue-context",
      "operationoutcome-issue-line", "operationoutcome-issue-server", "operationoutcome-issue-slicetext",
      "operationoutcome-issue-source", "operationoutcome-message-id", "operationoutcome-resource", "ordinalValue",
      "organization-brand", "organization-period", "organization-portal", "organization-preferredContact",
      "organizationaffiliation-primaryInd", "originalText", "package-source", "parameters-definition",
      "parameters-fullUrl", "patient-adoptionInfo", "patient-animal", "patient-birthPlace", "patient-birthTime",
      "patient-bornStatus", "patient-cadavericDonor", "patient-citizenship", "patient-congregation",
      "patient-contactPriority", "patient-disability", "patient-fetalStatus", "patient-genderIdentity",
      "patient-importance", "patient-interpreterRequired", "patient-knownNonDuplicate", "patient-mothersMaidenName",
      "patient-multipleBirthTotal", "patient-nationality", "patient-preferenceType", "patient-preferredPharmacy",
      "patient-proficiency", "patient-relatedPerson", "patient-religion", "patient-sexParameterForClinicalUse",
      "patient-unknownIdentity", "perform-condition", "practitioner-animalSpecies", "practitioner-job-title",
      "practitionerrole-doingBusinessAs", "practitionerrole-employmentStatus", "practitionerrole-primaryInd",
      "preferredTerminologyServer", "preferredValueAlternatives", "procedure-approachBodyStructure",
      "procedure-causedBy", "procedure-directedBy", "procedure-incisionDateTime", "procedure-method",
      "procedure-progressStatus", "procedure-schedule", "procedure-targetBodyStructure", "profile-mapping",
      "quantity-accuracy", "quantity-confidenceInterval", "quantity-precision", "questionnaire-baseType",
      "questionnaire-choiceOrientation", "questionnaire-constraint", "questionnaire-definitionBased",
      "questionnaire-derivationType", "questionnaire-displayCategory", "questionnaire-fhirType", "questionnaire-hidden",
      "questionnaire-index-answer", "questionnaire-itemControl", "questionnaire-maxOccurs", "questionnaire-minOccurs",
      "questionnaire-optionExclusive", "questionnaire-optionPrefix", "questionnaire-optionRestriction",
      "questionnaire-referenceFilter", "questionnaire-referenceProfile", "questionnaire-referenceResource",
      "questionnaire-signatureRequired", "questionnaire-sliderStepValue", "questionnaire-supportHyperlink",
      "questionnaire-supportLink", "questionnaire-unit", "questionnaire-unitOption", "questionnaire-unitValueSet",
      "questionnaire-usageMode", "questionnaireresponse-attester", "questionnaireresponse-author",
      "questionnaireresponse-completionMode", "questionnaireresponse-reason", "questionnaireresponse-reviewer",
      "questionnaireresponse-signature", "rank", "rdf-concept-iri", "referencesContained", "regex",
      "relatesto-classifier", "relative-date", "relative-time", "rendered-value", "rendering-markdown",
      "rendering-style", "rendering-styleSensitive", "rendering-xhtml", "replaces", "request-doNotPerform",
      "request-insurance", "request-performerOrder", "request-relevantHistory", "request-replaces",
      "request-statusReason", "requirements-parent", "researchStudy-interventionalistRecruitment",
      "researchStudy-investigatorRecruitment", "researchStudy-siteRecruitment", "researchStudy-studyRegistration",
      "resolve-as-version-specific", "resource-approvalDate", "resource-effectivePeriod",
      "resource-instance-description", "resource-instance-name", "resource-lastReviewDate", "resource-pertainsToGoal",
      "satisfies-requirement", "servicerequest-geneticsItem", "servicerequest-order-callback-phone-number",
      "servicerequest-precondition", "servicerequest-questionnaireRequest", "servicerequest-specimenSuggestion",
      "specimen-additive", "specimen-collectionPriority", "specimen-isDryWeight", "specimen-processingTime",
      "specimen-reject-reason", "specimen-sequenceNumber", "specimen-specialHandling", "statistic-model-include-if",
      "structuredefinition-ancestor", "structuredefinition-applicable-version", "structuredefinition-category",
      "structuredefinition-codegen-super", "structuredefinition-compliesWithProfile",
      "structuredefinition-conformance-derivedFrom", "structuredefinition-dependencies",
      "structuredefinition-display-hint", "structuredefinition-explicit-type-name",
      "structuredefinition-extension-meaning", "structuredefinition-fhir-type", "structuredefinition-fmm",
      "structuredefinition-fmm-no-warnings", "structuredefinition-fmm-support", "structuredefinition-hierarchy",
      "structuredefinition-implements", "structuredefinition-imposeProfile", "structuredefinition-inheritance-control",
      "structuredefinition-interface", "structuredefinition-normative-version", "structuredefinition-security-category",
      "structuredefinition-standards-status", "structuredefinition-standards-status-reason",
      "structuredefinition-summary", "structuredefinition-table-name", "structuredefinition-template-status",
      "structuredefinition-type-characteristics", "structuredefinition-wg", "structuredefinition-xml-no-order",
      "subject-locationClassification", "subscription-best-effort", "supplydelivery-previousDelivery",
      "synchronicity-control", "target-feature-assertion", "targetConstraint", "targetElement", "targetPath",
      "task-candidateList", "task-replaces", "terminology-resource-identifier-metadata", "textLink",
      "time-precision", "timezone", "timing-dayOfMonth", "timing-daysOfCycle", "timing-exact", "timing-uncertainDate",
      "translation", "tz-code", "tz-offset", "uncertainPeriod", "valueset-activityStatusDate", "valueset-author",
      "valueset-authoritativeSource", "valueset-caseSensitive", "valueset-compose-createdBy",
      "valueset-compose-creationDate", "valueset-compose-include-valueSetTitle", "valueset-concept-comments",
      "valueset-concept-definition", "valueset-conceptOrder", "valueset-deprecated", "valueset-effectiveDate",
      "valueset-expand-group", "valueset-expand-rules", "valueset-expansion-parameter", "valueset-expansionSource",
      "valueset-expirationDate", "valueset-expression", "valueset-extensible", "valueset-keyWord", "valueset-label",
      "valueset-map", "valueset-otherName", "valueset-otherTitle", "valueset-parameterSource", "valueset-reference",
      "valueset-rules-text", "valueset-scope", "valueset-select-by-map", "valueset-sourceReference",
      "valueset-special-status", "valueset-steward", "valueset-supplement", "valueset-systemName", "valueset-toocostly",
      "valueset-trusted-expansion", "valueset-unclosed", "valueset-usage", "valueset-warning",
      "valueset-workflowStatus", "valueset-workflowStatusDescription", "variable", "version-specific-use",
      "version-specific-value", "web-source", "workflow-adheresTo", "workflow-barrier", "workflow-compliesWith",
      "workflow-episodeOfCare", "workflow-followOnOf", "workflow-generatedFrom", "workflow-instantiatesCanonical",
      "workflow-instantiatesUri", "workflow-protectiveFactor", "workflow-reason", "workflow-reasonCode",
      "workflow-reasonReference", "workflow-relatedArtifact", "workflow-releaseDate", "workflow-researchStudy",
      "workflow-shallComplyWith", "workflow-statusReason", "workflow-supportingInfo", "workflow-triggeredBy"
  ));

  private final IWorkerContext context;

  public ExtensionNamingChecker(IWorkerContext context) {
    this.context = context;
  }

  public static boolean isExisting(String id) {
    return EXISTING_EXTENSIONS.contains(id);
  }

  /**
   * @return null if the id is ok (or exempt), or else a message explaining what the id should look like
   */
  public String check(StructureDefinition sd) {
    if (sd == null || !sd.hasId() || EXISTING_EXTENSIONS.contains(sd.getId())) {
      return null;
    }
    String id = sd.getId();
    List<String> roots = elementContextRoots(sd);

    if (roots != null && roots.size() == 1) {
      String prefix = roots.get(0).toLowerCase();
      if (matches(id, prefix)) {
        return null;
      }
      return fail(id, "the context is the single resource or type "+roots.get(0)+", so the id should be '"+prefix+"-' followed by a lowerCamelCase qualifier");
    }

    if (roots != null && allResourceRoots(sd)) {
      Set<String> patterns = commonPatterns(roots);
      if (!patterns.isEmpty()) {
        CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(" or ");
        for (String p : patterns) {
          if (matches(id, p.toLowerCase())) {
            return null;
          }
          b.append("'"+p.toLowerCase()+"-'");
        }
        return fail(id, "the context is resources that all implement the pattern "+String.join("/", patterns)+", so the id should be "+b.toString()+" followed by a lowerCamelCase qualifier");
      }
    }

    if (Pattern.matches(QUALIFIER, id)) {
      return null;
    }
    for (String p : STANDARD_PREFIXES) {
      if (matches(id, p)) {
        return null;
      }
    }
    return fail(id, "the id should be a lowerCamelCase qualifier, optionally preceded by one of the standard prefixes ("+String.join(", ", STANDARD_PREFIXES)+") and a dash");
  }

  private boolean matches(String id, String prefix) {
    return Pattern.matches(Pattern.quote(prefix)+"-"+QUALIFIER, id);
  }

  private String fail(String id, String why) {
    return "The extension id '"+id+"' does not follow the standard pattern for extension names defined in FHIR-43753: "+why;
  }

  /**
   * @return the distinct resource/type names at the root of the contexts, or null if any context is not an element context
   */
  private List<String> elementContextRoots(StructureDefinition sd) {
    if (sd.getContextList().isEmpty()) {
      return null;
    }
    List<String> roots = new ArrayList<>();
    for (StructureDefinitionContextComponent ctxt : sd.getContextList()) {
      if (ctxt.getType() != ExtensionContextType.ELEMENT || !ctxt.hasExpression()) {
        return null;
      }
      String e = ctxt.getExpression();
      String root = e.contains(".") ? e.substring(0, e.indexOf(".")) : e;
      if (!roots.contains(root)) {
        roots.add(root);
      }
    }
    return roots;
  }

  /**
   * true if every context is a resource (not an element within a resource, and not a data type)
   */
  private boolean allResourceRoots(StructureDefinition sd) {
    if (context == null) {
      return false;
    }
    for (StructureDefinitionContextComponent ctxt : sd.getContextList()) {
      String e = ctxt.getExpression();
      if (e.contains(".")) {
        return false;
      }
      StructureDefinition t = context.fetchTypeDefinition(e);
      if (t == null || t.getKind() != StructureDefinition.StructureDefinitionKind.RESOURCE) {
        return false;
      }
    }
    return true;
  }

  private Set<String> commonPatterns(List<String> resources) {
    Set<String> common = null;
    for (String r : resources) {
      Set<String> p = patterns(r);
      if (common == null) {
        common = p;
      } else {
        common.retainAll(p);
      }
    }
    return common == null ? new HashSet<>() : common;
  }

  /**
   * The patterns a resource implements: the interfaces it declares it implements (e.g. MetadataResource, which
   * is itself a CanonicalResource), and the workflow pattern it maps to (Request, Event, Definition)
   */
  private Set<String> patterns(String resource) {
    Set<String> res = new HashSet<>();
    StructureDefinition sd = context.fetchTypeDefinition(resource);
    if (sd == null) {
      return res;
    }
    for (Extension ext : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_RESOURCE_IMPLEMENTS)) {
      if (ext.hasValue() && ext.getValue().primitiveValue() != null) {
        String v = ext.getValue().primitiveValue();
        String name = v.substring(v.lastIndexOf("/")+1);
        res.add(name);
        if ("MetadataResource".equals(name)) {
          res.add("CanonicalResource");
        }
      }
    }
    if (sd.hasSnapshot() && !sd.getSnapshot().getElementList().isEmpty()) {
      for (ElementDefinition.ElementDefinitionMappingComponent m : sd.getSnapshot().getElementList().get(0).getMappingList()) {
        if ("workflow".equals(m.getIdentity()) && m.hasMap()) {
          for (String s : m.getMap().split(",")) {
            s = s.trim();
            if (Pattern.matches("[A-Z][A-Za-z]+", s)) {
              res.add(s);
            }
          }
        }
      }
    }
    return res;
  }
}
