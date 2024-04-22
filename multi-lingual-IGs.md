Mulit-lingual IGs

#1 Introduction 

All IGs are authored in a single primary language that is the master language, and then
additional languages are provided through the use of translation infrastructure.
The primary language does not have to be in english (even though most existing IGs use english)
Note that the external resources to the IG are also have a single primary 
language, but may provide translations. 

Text in the final IG comes from 6 different sources:

1. Text in the IG resources 
2. Text in the resources from core and other packages the IG depends on
3. Text from the java code that presents the resources 
4. Text from the underlying terminologies (external to FHIR e.g. SCT, LOINC, RxNorm etc)
5. Text from the narrative pages authored as part of the specification 
6. Text from the template that builds all the parts into a coherent specification 

A fully multi-lingual IG must somehow provide translations for all this content. 

This list summarizes the expected source materials for each of these sources.

1. Text in the IG resources 

Translations are provided as part of the IG using the IG translation mechanism described 
below.

2. Text in the resources from core and other packages the IG depends on

The core specification and the HL7 terminology are developed and delivered in english. 
"Language Packs" are developed by communities of interest that represent regions that 
use particular languages that provide translations of some of the content to other 
langages to support IGs and implementations that depend on the content.

The IG references the language pack(s) of relevance, and the translations they provide
are automatically in scope

3. Text from the java code that presents the resources 

The java code uses standard java internationalization techniques internally, but for 
translator convenience, the master copy of the translations are stored in .po files 
at [https://github.com/hapifhir/org.hl7.fhir.core/tree/master/org.hl7.fhir.utilities/src/main/resources/source].
Contributions are welcome, and the file https://github.com/hapifhir/org.hl7.fhir.core/blob/master/org.hl7.fhir.utilities/src/main/resources/source/editors.txt
lists who to contact for existing translations. 

4. Text from the underlying terminologies (external to FHIR e.g. SCT, LOINC, RxNorm etc)

External terminologies such as SCT and LOINC have some translations, and these are 
supported by the [approved terminology services](https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator#UsingtheFHIRValidator-AlternateTerminologyServers).
If the terminology or one of the servers doesn't support your language for the terminology, contact either 
the terminology authority, or the terminology service provider (e.g. [zulip]() for tx.fhir.org).

5. Text from the narrative pages authored as part of the specification 

At present, the plan is that each page that is authored - as md or xml - has a matching 
file [name]-[lang].[ext] in the translation source folder (see below) that translates the
page, but right now nothing will happen with that - depends on the template (next)

6. Text from the template that builds all the parts into a coherent specification 

ToDo

#2 Setting up the IGs

* Which languages are in scope 
* Where translations come from 

#3 Providing the translations
