* Template Manager: add .woff2 to template white list
* Validator: Rework bundle references validation for R4+ - this is a *significant* change - many existing bundles that were previously erroneously passing will now fail
* Validator: Don't fail on erroneously repeating elements
* Validator: Fix problem creating CDA type discriminators
* Validator: Fix problem with R3 expansion
* Validator: Add support for CCDA .hasTemplateIdOf(canonical)
* Renderer: Fix issue where markdown with multiple characters was being cut off sometimes when rendering profiles
