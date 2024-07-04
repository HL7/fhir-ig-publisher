* Loader: do not add Binary resources which are not really Binary
* Renderer: Fix various rendering problems leading to non-unique html anchors
* Renderer: Fix for unrendered data types
* Translations: Add complete dutch translations (Thanks Alexander Henket)
* Validator: the validator was not checking for the end of the input when parsing a JSON resource finished. It will now start giving errors when JSON continues once the object is complete 
* Validator: Add support for the create object syntax in FML when validating FML
* Validator: Improved error message when supplement url used instead of code system URL
