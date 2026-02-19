
Note: this version is synchronised with a major update to tx.fhir.org. 
Implementers SHOULD upgrade to this version immediately - this is the 
oldest IGPublisher version *supported* for use with the new server. On
the other hand, there's no change to the terminology server API - older
version of the Implementers SHOULD continue to work. But how some cases 
are handled - particularly around supplements - has changed.

* General: update dependencies 
* Loader: fix processing of value set versions when pinning canonicals
* Loader: Fix stated version when rendering explicitly pinned canonicals
* Terminology Support: Update internal terminology server for new server on tx.fhir.org
* Version Convertor: Fix NPE converting bad property converting from R4 to R5
* Version Convertor: fix NPE reading extensions with no value
* Validator: fix bug reporting wrong version when validating value sets
* Renderer: fix case of forloop variable in liquid implementation
* QA: find additional canonical URLs when validating links
