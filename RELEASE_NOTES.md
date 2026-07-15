* General change http: to https:
* Loader: Make sure binary loading works on contained resources too, adds SVG support and fixes JPEG support.
* Loader: improved logging for settings
* Validator: fix up caching for new sealed parameter
* Validator: fix version bug excluding codes and fix server side handling for excluded valuesets
* Validator: improve validation error message for retired codes
* Validator: fix tx server addresses to https:
* Validator: Fix cache-id double header issue
* Validator: rework cache shutdown
* Validator: fix htmlChecks() implementation on string in validator
* Validator: NPE fix for missing version when doing value set validation
* Validator: OID requirements don't apply to affiliates
* Renderer: fix broken links in comparisons
* Web Publiahing: fix for publishing error that allows failed publishing to proceed
