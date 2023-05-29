
* Core: Fix bug where elementmodel.Element.copy() didn't clone children which lead to content corruption in Quantity Examples
* Validator: Add support for "Obligation Profiles" (see https://chat.fhir.org/#narrow/stream/179177-conformance/topic/Proposed.20new.20Profile.20features)
* Validator: Adjust slice min/max checking to ignore type slices (rules are different in this case)
* Validator: Properly handle validating mime/type when terminology server is not available
* QA: More checking around publication milestones and versions
* QA: Add QA reports around performance 
* Publication Process: Add new file package-registry.json


