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

## Pre-process transforms

9. **Name only your own `.xslt` files in a `pre-process` `transform`.** A transform produces its main output and nothing else: the publisher refuses `xsl:result-document`, and blocks reading files and fetching URLs.

## Jekyll

10. **No Jekyll plugins.** `_plugins/`, `plugins` and `whitelist` in `_config.yml`, and a `Gemfile` or `gems.rb` are all ignored or refused. Jekyll runs with `--safe` and a fixed `--source`, and doesn't follow symlinks.

## Network

11. **Use `<get>` only with fixed URLs,** or URLs built from fixed parts, and honour `ig.networkprohibited`.

## Changes

12. **Have changes reviewed before release.** Any change to a trusted template's `scripts/` or `config.json` is reviewed before it's released. Adding a template to the trusted list is agreed with the FHIR Product Director first.
