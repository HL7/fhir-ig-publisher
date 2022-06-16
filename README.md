<!---
 ____________________
|                    |
|  N  O  T  I  C  E  |
|____________________|

Please maintain this README.md as a linkable document, as other documentation may link back to it. The following sections should appear consistently in all updates to this document to maintain linkability:

## Building this Project
## Running this Project
## Releases
## CI/CD
## Maintenance

--->

# HL7 FHIR IG Publisher Artifacts

| CI Status (master) | Release Pipeline | Current Release | Latest SNAPSHOT |
| :---: | :---: | :---: | :---: |
| [![Build Status][Badge-AzureMasterPipeline]][Link-AzureMasterPipeline] | [![Build Status][Badge-AzureReleasePipeline]][Link-AzureReleasePipeline] | [![Release Artifacts][Badge-SonatypeReleases]][Link-GithubZipRelease] | [![Snapshot Artifact][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] |

This is the code for the HL7 IG publisher: a tool to take a set of inputs
and create a standard FHIR IG. The HL7 FHIR IG publisher does not provide 
an authoring environment - that's left to other parties.

## Building this Project

This project uses [Apache Maven][Link-Maven] to build. To build:

```
mvn install
```

Note that unit tests will run, but are currently not set to fail the build as they do not all pass. This is being worked on.

To skip unit tests:

```
mvn -Dmaven.test.skip install
```

## Running this Project

Once built, there are two common tasks that can be accomplished using the fhir-ig-publisher, which are described in detail in the documentation below:

* [Publishing Packages](https://hl7.github.io/docs/publishing_packages)
* [Publishing Templates](https://hl7.github.io/docs/publishing_templates)

## Releases

The built binary for the FHIR IG publisher is released through [GitHub releases][Link-GithubReleases] and can be downloaded directly [here][Link-GithubZipRelease].

## CI/CD

All intergration and delivery done on Azure pipelines. Azure project can be viewed [here][Link-AzureProject].

* **Pull Request Pipeline** is automatically run for every Pull Request to ensure that the project can be built by maven. [[Azure Pipeline]][Link-AzurePullRequestPipeline] [[source]](pull-request-pipeline.yml)
* **Master Branch Pipeline** is automatically run whenever code is merged to the master branch and builds the SNAPSHOT binaries distributed to OSSRH [[Azure Pipeline]][Link-AzureMasterPipeline][[source]](master-branch-pipeline.yml)
* **Release Branch Pipeline** is run manually whenever a release is ready to be made. It builds the [release binaries](#releases) and distributes them to artifact repositories. [[Azure Pipeline]][Link-AzureReleasePipeline][[source]](release-branch-pipeline.yml)


A brief overview of our publishing process is [here][Link-Publishing].

For more detailed instructions on cutting a release, please read [the wiki][Link-PublishingRelease]

## Maintenance

Have you found an issue? Do you have a feature request? Great! Submit it [here][Link-GithubIssues] and we'll try to fix it as soon as possible.

This project is maintained by [Grahame Grieve][Link-grahameGithub] and [Lloyd McKenzie][Link-lloydmckenzie] on behalf of the FHIR community.


[Link-AzureMasterPipeline]: https://dev.azure.com/fhir-pipelines/ig-publisher/_build/latest?definitionId=33&branchName=master
[Link-AzureReleasePipeline]: https://dev.azure.com/fhir-pipelines/ig-publisher/_build/latest?definitionId=34&branchName=master
[Link-AzurePullRequestPipeline]: https://dev.azure.com/fhir-pipelines/ig-publisher/_build?definitionId=32
[Link-GithubIssues]: https://github.com/HL7/fhir-ig-publisher/issues
[Link-GithubReleases]: https://github.com/HL7/fhir-ig-publisher/releases
[Link-GithubZipRelease]: https://github.com/HL7/fhir-ig-publisher/releases/latest/download/publisher.jar "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=org.hl7.fhir.publisher&a=org.hl7.fhir.publisher.cli&v=LATEST "Sonatype Snapshots"
[Link-AzureProject]: https://dev.azure.com/fhir-pipelines/ig-publisher
[Link-Maven]: http://maven.apache.org

[Link-PublishingRelease]: https://hl7.github.io/docs/ci-cd-building-release
[Link-Publishing]: https://hl7.github.io/docs/ci-cd-publishing-binaries

[Link-grahameGithub]: https://github.com/grahamegrieve
[Link-lloydmckenzie]: https://github.com/lmckenzi

[Badge-AzureMasterPipeline]: https://dev.azure.com/fhir-pipelines/ig-publisher/_apis/build/status/Master%20Branch%20Pipeline?branchName=master
[Badge-AzureReleasePipeline]: https://dev.azure.com/fhir-pipelines/ig-publisher/_apis/build/status/Release%20Branch%20Pipeline?branchName=master
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/org.hl7.fhir.publisher/org.hl7.fhir.publisher.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/org.hl7.fhir.publisher/org.hl7.fhir.publisher.svg "Sonatype Snapshots"
