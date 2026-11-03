## Specific Changes

* Renderer: in R4/R4B/R5 IGs, write the generated `Requirements-fromNarrative.json` as R5 (actor as canonical with the key in an extension), so it can be copied into the input folder and loaded again (#1449)
* Renderer: regenerate the actor list of `Requirements-fromNarrative.json` when a loaded actor has no key, e.g. a file written by an older publisher with the `requirements-actorkey` extension, which is dropped on load (#1449)
