REM replace versions before running
REM make sure you are committed

@echo off
echo ..
echo ===========================================================================
echo upgrade and release fhir IG Publisher from 0.9.10-SNAPSHOT to 0.9.11-SNAPSHOT
echo ===========================================================================
echo ..
echo Make sure code is commmitted and check the versions...
pause

call mvn versions:set -DnewVersion=0.9.11-SNAPSHOT
call git commit -a -m "Release new version"
call git push origin master
call "C:\tools\fnr.exe" --cl --dir "C:\work\org.hl7.fhir\build" --fileMask "*.xml" --find "0.9.10-SNAPSHOT" --replace "0.9.11-SNAPSHOT"
call mvn deploy
copy org.hl7.fhir.publisher.cli\target\org.hl7.fhir.publisher.cli-0.9.11-SNAPSHOT.jar ..\igpublisher.jar
call python c:\tools\zulip-api\zulip\zulip\send.py --stream committers/notification --subject "java IGPublisher" -m "New Java IGPublisher v0.9.11-SNAPSHOT released at https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=org.hl7.fhir.publisher&a=org.hl7.fhir.publisher.cli&v=0.9.11-SNAPSHOT&e=jar" --config-file zuliprc

echo ===============================================================
echo all done
echo ===============================================================
pause
 