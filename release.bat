@echo off

set oldver=1.0.24
set newver=1.0.25


echo ..
echo =============================================================================
echo upgrade and release fhir IG Publisher from %oldver%-SNAPSHOT to %newver%-SNAPSHOT
echo =============================================================================
echo ..
echo Make sure code is commmitted and check the versions...
pause

call mvn versions:set -DnewVersion=%newver%-SNAPSHOT

pause

call git commit -a -m "Release new version %newver%-SNAPSHOT"
call git push origin master
call "C:\tools\fnr.exe" -dir "C:\work\org.hl7.fhir\build" -fileMask "*.xml" -find "%oldver%-SNAPSHOT" -replace "%newver%-SNAPSHOT" -count 1
call "C:\tools\fnr.exe" -dir "C:\work\org.hl7.fhir\latest-ig-publisher" -fileMask "*.html" -find "%oldver%" -replace "%newver%" -count 1
call "C:\tools\fnr.exe" -dir "C:\work\org.hl7.fhir\latest-ig-publisher" -fileMask "*.json" -find "%oldver%" -replace "%newver%" -count 1
call mvn clean deploy -Dmaven.test.redirectTestOutputToFile=false -DdeployAtEnd=true 
IF %ERRORLEVEL% NEQ 0 ( 
  GOTO DONE
)
copy org.hl7.fhir.publisher.cli\target\org.hl7.fhir.publisher.cli-%newver%-SNAPSHOT.jar ..\latest-ig-publisher\org.hl7.fhir.publisher.jar
cd ..\latest-ig-publisher
call git commit -a -m "Release new version %newver%-SNAPSHOT"
call git push origin master
cd ..\fhir-ig-publisher
call python c:\tools\zulip-api\zulip\zulip\send.py --stream committers/notification --subject "java IGPublisher" -m "New Java IGPublisher v%newver%-SNAPSHOT released at https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=org.hl7.fhir.publisher&a=org.hl7.fhir.publisher.cli&v=%newver%-SNAPSHOT&e=jar, and also deployed at https://fhir.github.io/latest-ig-publisher/org.hl7.fhir.publisher.jar" --config-file zuliprc

:DONE
echo ========
echo all done
echo ========
pause
 