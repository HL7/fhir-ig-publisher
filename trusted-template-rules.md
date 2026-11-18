# Rules for authors of trusted IG templates

Nov 18, 2026 · @Grahame Grieve

A template on the trusted list can run code on the ci-build: its ant scripts and its pre-process transforms. Anyone can build a child template on top of a trusted one, and that child isn't trusted. It can't add code, but it can replace any of your files that has a harmless extension. These rules keep a trusted template safe to build on.

## Where code lives

1. **Put everything your scripts run or read as configuration in `scripts/`.** That means ant files, stylesheets, jars and data files such as `groupings.txt`. The publisher treats every file in `scripts/` as active content, so an untrusted child can't add or replace anything there.
2. **Register every ant file.** The main file goes in `script`, and every file it imports, directly or indirectly, goes in `otherScripts`.
3. **Stylesheets are `.xslt`, never `.xml`.** `xsl:include` and `xsl:import` name only other `.xslt` files in `scripts/`.
4. **Executables (jars etc.) live in `scripts/`** under fixed names, and run with fixed arguments.

## Files outside scripts/

5. **Anything outside `scripts/` can be replaced by a child template**: `content/`, `includes/`, `layouts/`, `data/`, `translations/` and so on. Treat these files as page content only: copy them, or pass them to a stylesheet as text. Never import, run or `eval` them, never load them as ant properties, and never let them choose a target, file or URL.

## IG content is hostile

6. **Anyone can write an IG**, so nothing derived from its content (the ImplementationGuide resource, its pages, files generated from them) may reach an `<exec>` or `<java>` argument, a `<get>` URL or destination, a file path outside the template and temp folders, or JavaScript `eval`.
7. **Only `<loadproperties>` a file built from IG content when your stylesheet fixes every property name,** and the values are used only as text.
8. **Inline `<script>` and `<scriptdef>` JavaScript only joins strings and sets properties.** It never evaluates content.
9. **Protect your scripts against variable injection.** Variable injection is when a value your script picks up from the IG or from a child template (a file name, an id, a link, a parameter) is pasted into something that is read again later: a properties file, an XML file, a command line, a URL or a file path. If the value contains characters that mean something there, it changes what the script does instead of just being data. For example:
   - A newline in a value written to a properties file adds new properties of the IG's choosing.
   - `../` in a value used as a file name reaches files outside the folder you meant.
   - A space or quote in a value pasted into `<arg line="...">` adds new command-line arguments.
   - `&`, `<` or a `DOCTYPE` in a value pasted into XML adds markup, or makes the parser read other files.
   - `${...}` in a value that ant reads as a property is expanded into another property's value.

   To protect against it:
   - Check every such value against a strict pattern of the characters you expect (for example letters, digits, `.`, `-` and `_`), and stop with an error if it doesn't match. Don't try to clean it up.
   - Pass values as separate arguments (`<arg value="..."/>`, `<param>` on `<xslt>`), never by pasting them into a larger string such as `<arg line="..."/>` or stylesheet text.
   - Escape a value for wherever it ends up: XML-escape it for XML, URL-encode it for a URL.
   - Before using a value in a file path, check that the resolved path is still inside the template or temp folder.

## Pre-process transforms

10. **Name only your own `.xslt` files in a `pre-process` `transform`.** A transform produces its main output and nothing else: the publisher refuses `xsl:result-document`, and blocks reading files and fetching URLs.

## Jekyll

11. **No Jekyll plugins.** `_plugins/`, `plugins` and `whitelist` in `_config.yml`, and a `Gemfile` or `gems.rb` are all ignored or refused. Jekyll runs with `--safe` and a fixed `--source`, and doesn't follow symlinks.

## Network

12. **Use `<get>` only with fixed URLs,** or URLs built from fixed parts, and honour `ig.networkprohibited`.

## Changes

13. **Have changes reviewed before release.** Any change to a trusted template's `scripts/` or `config.json` is reviewed before it's released. Adding a template to the trusted list is agreed with the FHIR Product Director first.
