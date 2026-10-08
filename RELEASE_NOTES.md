## Major Release - R6

* This is a major release. Since 2.3.5, the IG publisher has been based on R6 internally rather than R5, and 2.3.5 should have been released as a major version

## Other Changes

* Loader: don't delete temporary package folders that another process sharing the package cache may still be installing into
* Terminology Subsystem: Fix code system supplement content (designations, properties) being missed once the supplements are merged into the code system
* Terminology Subsystem: Fix the terminology client looking for R5 resource classes when fetching resources from a terminology server
* Validator: Check that a StructureDefinition only defines new elements inside an element with an abstract type (e.g. BackboneElement)
* Validator: Fix the FML parser not setting the resource definition on the StructureMaps it parses
* Renderer: don't fail rendering a canonical resource that has no status (e.g. an R5 Group under the R6 model)
* QA: broken links are now only links and images that can't be resolved - other HTML checker issues (well-formedness, duplicate ids, WCAG checks) were being counted as broken links. The build log, qa.html, qa.txt and qa.json (new 'broken-links' count) all use the same count, and the broken-links error only fires when there are broken links
* QA: the HTML checker closes each file after reading it, and reads it as UTF-8
