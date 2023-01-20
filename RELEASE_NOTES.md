* Security fix: enforce correct paths when unpacking archives (HAPI Core SecurityAdvisory-1082, CVE TBA)
* Loader: Allow to manually specify repo source when running in cloud
* Loader: Add direct parsing for StructureMaps (still to be fully tested)
* Validator: Update FHIRPath implementation for corrections to 'as' for R5
* QA: Fix typo on counts
* Publication Process: add support for hl7-eu
* Publication Process: FTP Client upload and logging improvements

## Security Note

The publisher unzips archive files to the local file system when it is
installing packages, and when it is performing publication on the HL7 
website (internal HL7 process). These processes are now resistant to 
the zip-slip vulnerability.