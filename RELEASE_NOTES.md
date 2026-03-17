* Loader: This release of the IGPublisher supports scoped packages (see [security advisory note](todo))
* Loader: add new trusted template for WHO
* Loader: jekyll command as a CLI parameter
* Loader: Support version '|*' when pinning
* Loader: Various Performance Improvements
* Terminology: Fix bugs in expansion and validation when valueset includes two different versions of the same code system
* Terminology: Expansion bugs: imported valueSet excludes ignored + expansion.total inconsistent
* Snapshot Generator: Fix bug where binding.valueSet extensions are lost in snapshots
* Snapshot Generator: Fix error setting content reference wrong in deeply nested snapshots
* Validator: Fix bugs chasing package versioned references when no version supplied
* Validator: Fix snapshot generation for nested slices with contentReference (h/t glichtner)
* Validator: Fix bug parsing with multiple profiles for a type
* Renderer: Updated rendering of conformance statement table
* Renderer: Improved rendering of 'div' conformance statements.
* Renderer: Reorganize generation so summary tables are available when generating pages + gen combined single package
* Renderer: Add generating package-combined.tgz 
* Renderer: Fix NPE rendering Questionnaires 
* Renderer: Fix rendering bug where naming system resolution was a little random
* Renderer: fix duplicate ids in questionnaire pages in igs
* Version Comparison: Fix problem where comparing profiles gets into an infinite loop
* QA: Add validation context


