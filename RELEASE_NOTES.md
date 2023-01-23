* Critical Security fix: enforce correct paths when unpacking archives (SecurityAdvisory-1082, CVE TBA)

### Security Note
The IG publisher unzips archive files to the local file system when
extracting the default ig template, deletes files based on the zip entry 
path when preparing web publications, and produces NPM packages using zip 
entry paths when run in '-compare' mode. These processes are now 
resistant to the zip-slip vulnerability.
