@echo off

set oldver=1.0.87
set newver=1.0.88

echo ..
echo =============================================================================
echo upgrade and release fhir IG Publisher from %oldver%-SNAPSHOT to %newver%-SNAPSHOT
echo =============================================================================
echo ..

call mvn versions:set -DnewVersion=%newver%-SNAPSHOT

cd C:\work\org.hl7.fhir\fhir-ig-publisher

rmdir /S/Q C:\Users\graha\.fhir\packages
call C:\work\org.hl7.fhir\test-igs\update.bat
cd C:\work\org.hl7.fhir\fhir-ig-publisher

call git commit -t v%newver% -a -m "Release new version %newver%"
call git tag v%newver%
call git push origin master

call "C:\tools\fnr.exe" -dir "C:\work\org.hl7.fhir\build" -fileMask "*.xml" -find "%oldver%-SNAPSHOT" -replace "%newver%-SNAPSHOT" -count 1
call "C:\tools\fnr.exe" -dir "C:\work\org.hl7.fhir\latest-ig-publisher" -fileMask "*.html" -find "%oldver%" -replace "%newver%" -count 1
call "C:\tools\fnr.exe" -dir "C:\work\org.hl7.fhir\latest-ig-publisher" -fileMask "*.json" -find "%oldver%" -replace "%newver%" -count 1
call "C:\tools\fnr.exe" -dir "C:\work\org.hl7.fhir\test-igs" -fileMask "*.bat" -find "%oldver%" -replace "%newver%" -count 1
call mvn clean deploy -Dmaven.test.redirectTestOutputToFile=false -DdeployAtEnd=true 
IF %ERRORLEVEL% NEQ 0 ( 
  GOTO DONE
)

rem call C:\work\org.hl7.fhir\test-igs\upgrade.bat
cd C:\work\org.hl7.fhir\fhir-ig-publisher

call "C:\tools\versionNotes.exe" -fileName C:\work\org.hl7.fhir\latest-ig-publisher\release-notes-publisher.md -version %newver% -fileDest C:\temp\current-release-notes-publisher.md -url https://storage.googleapis.com/ig-build/org.hl7.fhir.publisher.jar -maven https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=org.hl7.fhir.publisher&a=org.hl7.fhir.publisher.cli&v=%newver%-SNAPSHOT&e=jar

call gsutil cp -a public-read org.hl7.fhir.publisher.cli\target\org.hl7.fhir.publisher.cli-%newver%-SNAPSHOT.jar gs://ig-build/org.hl7.fhir.publisher.jar
cd c:\work\org.hl7.fhir\latest-ig-publisher
call git commit -a -m "Release new version %newver%-SNAPSHOT"
call git push origin master
cd ..\fhir-ig-publisher

call python c:\tools\zulip-api\zulip\zulip\send.py --stream committers/notification --subject "java IGPublisher" -m "New Java IGPublisher v%newver%-SNAPSHOT released at https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=org.hl7.fhir.publisher&a=org.hl7.fhir.publisher.cli&v=%newver%-SNAPSHOT&e=jar, and also deployed at https://storage.googleapis.com/ig-build/org.hl7.fhir.publisher.jar" --config-file zuliprc
call python c:\tools\zulip-api\zulip\zulip\send.py --stream tooling/releases --subject "IGPublisher" --config-file zuliprc < C:\temp\current-release-notes-publisher.md

del C:\temp\current-release-notes-publisher.md 

:DONE
echo ========
echo all done
echo ========
pause
 