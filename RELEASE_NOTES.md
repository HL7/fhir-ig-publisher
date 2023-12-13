* Loader: Fix problems setting owning committee consistently
* Loader: Fix bug loading packages with partially specified version that doesn't exist
* Loader: Fix problem with profiled resources being called examples in IG publisher
* Package Manager: Write locking on FilesystemPackageCacheManager
* Version Conversion: Fix StringType element properties not being copied in various Address, HumanName convertors
* Validator: Fix narrative link validation and add id/idref validation (for IPS)
* Validator: Fix to CDA xsi:type validation per SD decision 
* Validator: Apply regex pattern to literal format if defined
* Validator: Improvements to vital signs related messages
* Validator: Fix R4 con-3 FHIRPath expression
* Validator: Fix for occasional missing warnings around bundle link validation
* Validator: Fix NPE in validator processing CCDA examples
* Validator: Fix for SearchParameter validation using custom resource types
* Validator: Fix stated path for error when code not in value set
* Renderer: Fix rendering of trigger definition using tables inside paragraphs
* Renderer: Fix problem with profiled resources being called examples in IG publisher
* Renderer: Fix bug where version specific references to profiles not picked up
* Renderer: Add more data to qa.json
* Renderer: Fix rendering of trigger definition using tables inside paragraphs
* Renderer: Handle all initial value types when rendering Questionnaires
* US Realm: Add link to US Core derivation process