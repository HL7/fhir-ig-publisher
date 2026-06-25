
* Loader: Fixed a whitespace issue parsing XML
* Loader: Fixed resolving canonical resources by version
* Loader: Unload xhtml in loaded resources to reduce memory load
* Terminology Subsystem: Reworked terminology caching and fixed the cache unloading too early
* Terminology Subsystem: Turn on client caching when the terminology server supports it
* Terminology Subsystem: Fixed handling of secondary displays in the wrong language
* Validator: Hardened processing against malicious input (bounded decompressed sizes, stack overflow prevention, escaped script-tag scanning, empty SHC content)
* Renderer: Cross-version RDF/Turtle support
* Renderer: Fixed Turtle-as-HTML formatting
* Renderer: Escape HTML output properly
* Renderer: Fixed NPEs in the diagnostic report renderer
* QA: Fixed bug checking whether duplicate OIDs are in examples
* SQL: Allow SQL processing for code systems in dependencies
