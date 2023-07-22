# IG Publisher User Documentation

## Introduction

This is the documentation for the IG Publisher. 

## IG Requirements

Supported Implementation Guides that the IG publisher can publish are always contained in directories that 
contain a file named ```ig.ini``` with the following content:

```ini
[IG]
ig = {path}
template = {template-id}
```
Other entries are allowed, but not used by the IG-Publisher. ig - the path to the ImplementationGuide resource 
that defines. 

You can either run the Publisher with a default directory of the folder that contains the ig.ini, or use the ```-ig``` parameter.

See [Location?](todo) for documentation about the content of the IG to be built.

## Command Line Parameters 

Run mode:
* ```-help``` - produce the command line help
* ```-go-publish`` - publication mode, see below`
* ```-gui``` - run the GUI (not really supported)

Command line build mode parameters
* ```-ig``` {folder}  - the folder that contains the IG
* ```-prompt``` - ask which ig to run (default to last)
* ```-source``` - run with standard template. this is publishing lite (just a set of conformance resource, IG publisher autobuilds an IG) ?supported
* ```-fhir-settings``` - see next section
* ```-debug``` - turn on debugging (extra logging, can be verbose) 
* ```-proxy``` - proxy to use if it must be set manually (host:port)
* ```-tx``` - alternative tx server to tx.fhir.org (but still most be the same software, see [running your own copy of tx.fhir.org](https://confluence.hl7.org/display/FHIR/Running+your+own+copy+of+tx.fhir.org))
* ```-no-network``` - turn of all network access - any attempt to use the network will generate an error (offline build mode)
* ```-no-sushi``` - don't run sushi before build IG (if it's there to be run)
* ```-generation-off``` - turn narrative generation off completely to make for faster local run time
* ```-no-narrative``` - comma list of resources (type/id) to not generate narrative for (e.g. faster run)
* ```-validation-off``` - turn validation off completely to make for faster local run time
* ```-no-validate``` -  comma list of resources (type/id) to not validate (e.g. faster run)
* ```-resetTx``` - clear the local terminology cache before running
* ```-resetTxErrors``` - remove any errors from the local cache but leave other content there
* ```-auto-ig-build``` - used by the ci-build (see below) to switch on some ci-build integration features
* ```-simplifier``` - used by simplifier when running the IG publisher internally (under development)
* ```-jekyll``` - path to Jekyll (but use config, see below)
* ```-cacheVersion``` - ?not supported anymore?
* ```-spec``` - path to old spec file (deprecated and not supported)
* ```-publish``` -  ?not supported anymore?

## Configuration File

## Go Publish

(todo)

## CI-build integration 

See https://github.com/FHIR/auto-ig-builder#quick-start-guide

## UI mode

This exists but isn't well documented. 

## Shell Integration 

### Windows 

To do

### OSX 

To do 
