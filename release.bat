REM replace versions before running
REM make sure you are committed

echo

echo ===========================================================================
echo upgrade and release fhir IG Publisher from 0.9.3-SNAPSHOT to 0.9.4-SNAPSHOT
echo ===========================================================================
pause

call mvn versions:set -DnewVersion=0.9.4-SNAPSHOT
call git commit -a -m "Release new version"
call git push origin master
call "C:\tools\fnr.exe" --cl --dir "C:\work\org.hl7.fhir\build" --fileMask "*.xml" --find "0.9.3-SNAPSHOT" --replace "0.9.4-SNAPSHOT"
call mvn deploy
copy org.hl7.fhir.publisher.cli\target\org.hl7.fhir.publisher.cli-0.9.4-SNAPSHOT.jar ..\igpublisher.jar
call python c:\tools\zulip-api\zulip\zulip\send.py --stream committers/notification --subject "java IGPublisher" -m "New Java IGPublisher v0.9.4-SNAPSHOT released." --config-file zuliprc

echo ===============================================================
echo all done
echo ===============================================================
pause
 