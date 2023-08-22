* GUI: Round of work to improve GUI, and make it build automatically with the correct parameters (Finder integration)
* Loader: Fix R4 FML parser problem
* Loader: Fix issue with missing version causing NPE
* Logging: Remove spurious logging in FHIRPath engine
* Terminology Service: Many minor changes to terminology functionality (reconciliation with differences with OntoServer) including service protection 
  * Rename implySystem parameter to inferSystem per TI decision
  * rework how definitions are handled after discussion with Michael
  * add flat mode to tests for Ontoserver, and add experimental functionality
* Terminology Service: Don't suppress exceptions in terminology clients
* Terminology Service: Stop putting invalid codes in expansions if they are not in the code system* Validator: CodeSystem validation around count and content
* Validator: Add checking around internal status consistency and across dependencies (draft/experimental/retired/deprecated)
* Validator: Improved error messages on tx server failure
* Validator: Fix bug in warning about No valid Display Names found
* Validator: Use Supplements when validating display names
* Validator: Fix issue in FHIRPath .combine focus handling 
* Validator: Check Extension fixed values for URLs - enforce consistency
* Validator: Track and report inactive status when reported from terminology server
* Validator: Add defense against large terminology operations causing obscure java errors
* Validator: Fix bug with client sending too much data to tx.fhir.org (big performance hit in some cases)
* Validator: Fix obscure bug with designations in a more specific language the the code system they are in
* Renderer + Version Comparison: Significant upgrade of version comparison for profiles/extensions, value sets, and code systems, and integration into rendering framework
* Renderer: fix rendering issue in subscription topic 
* Renderer: Add a renderer for ExampleScenario
* Renderer: Automatically render markdown in code system concept definitions
* Renderer: Release new pubpack for new icons
* Renderer: fix cross-version extensions web references where possible
* Renderer: ToC rendering fixes
