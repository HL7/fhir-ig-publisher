* Loader: ignore canonical resources found in core examples packages
* Loader: Support for R4B and R5 implementation guides
* Validator: get .ofType() working in discriminators (round #1!)
* Validator: fix bug checking enableWhen - ignoring items in answers
* Validator: Improved Error messages validating bundle entries
* Validator: Fix bug in deep profiles (profiles that dont start at the root)
* Validator: validate examples against the profile they are examples for
* Rendering: Improve rendering of uris that point to known resources
* Rendering: Fix wrong reference rendering questionnaire
* Rendering: Fix rendering of QuestionnaireResponses - render items in answers properly
* Rendering: Strip front matter from markdown passed to Jekyll for processing
* QA: change the way suppressed messages work - see [Managing Warnings and Hints](https://confluence.hl7.org/pages/viewpage.action?pageId=66938614#ImplementationGuideParameters-ManagingWarningsandHints)
* Publication Manager: update publication pipeline not to rewrite package files
