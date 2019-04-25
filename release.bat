REM replace versions before running
REM make sure you are committed

echo

echo ===========================================================================
echo upgrade and release fhir IG Publisher from 0.9.0-SNAPSHOT to 0.9.1-SNAPSHOT
echo ===========================================================================
pause

call mvn versions:set -DnewVersion=0.9.1-SNAPSHOT
call git commit -a -m "Release new version"
call git push origin master
call "C:\tools\fnr.exe" --cl --dir "C:\work\org.hl7.fhir\build" --fileMask "*.xml" --find "0.9.0-SNAPSHOT" --replace "0.9.1-SNAPSHOT"
call mvn deploy

echo ===============================================================
echo all done
echo ===============================================================
pause
 