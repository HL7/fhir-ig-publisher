* Many performance and bug fix improvements to rapido mode (-rapido, -watch) - h/t Vadim
* Loader: new `-threads n` parameter - lets the publisher use up to n threads beyond its own for work it can do in parallel (at most one less than the number of cores; default 0)
* Terminology Subsystem: value set excludes against terminology servers get passed to a server to process
* Terminology Subsystem: choosing a server for a code system - a candidate server has it when its TerminologyCapabilities lists it 
* Terminology Subsystem: LOINC answer list value sets (http://loinc.org/vs/LL...) go to the terminology server by implicit url
* Terminology Subsystem: a lookup that failed instead of getting an answer (registry or server down, timeout, a proxy error page) is only remembered for the run, not cached as not found; an unknown code system is only asked about once per run
* Terminology Subsystem: when a terminology server has lost the client's cache (it restarted, or the cache expired or was dropped), the client starts a new cache and repeats the request, instead of failing every request after that
* Terminology Subsystem: $subsumes answers are cached
* Validator: quantities with different but comparable UCUM units are compared after conversion, as in the validator (e.g. `rng-2` on a Range from 1 mg to 2 g); the publisher context had no UCUM service
* Validator: New Validation checks:
  * Check for an empty JSON array of a primitive element, or of its `_element`
  * a warning when a coding has no version, but its code system says versionNeeded = true
  * Warnings when an Observation (or component) doesn't fit its LOINC code properties (units, time aspect, scale). Same for profiles on observation
  * Warnings when SNOMED CT code properties don't fit. e.g. code type, status, laterality. Same for profiles
  * Error if SNOMED CT codes in medications are not medications 
  * warnings if there are double negatives in Condition, Allergy etc (refuting the refutation)
* Validator: a note that a coding isn't in the value set is no longer given as the reason a code failed validation when another coding decided the outcome; a value set's language no longer carries over to the codes validated after it
* SQL: performance - package.db keeps its journal in memory and doesn't wait for disk syncs (it is rebuilt on every run)
* Renderer: ValueSet expansions show the issues the expansion reports and the reason the value set is unclosed
* Renderer: ValueSet expansion pages use the terminology cache (they were expanded again on every build)
* Renderer: CapabilityStatement server-wide search parameters (rest.searchParam) are shown; ExampleScenario diagrams no longer run an operation with no request payload into the next line; Questionnaire display items have no cardinality; StructureDefinition mappings from a ConceptMap with no name use its title or id, and link [x] elements to the right anchors; the XIG usage link in French and Swedish
* Renderer: performance - the per-profile spreadsheets are built on the `-threads` workers, with column widths and shared strings cached across workbooks (the output is the same); full-ig.zip is compressed on the `-threads` workers
* QA: performance - the HTML check reads and parses each page once
