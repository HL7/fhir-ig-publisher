#!/bin/bash

# Exit on any error
set -e

# Check if all required parameters are provided
if [ "$#" -ne 8 ]; then
    echo "Usage: $0 -git <url> -branch <branch> -id <package> -version <version>"
    exit 1
fi

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        -git)
            GIT_URL="$2"
            shift
            shift
            ;;
        -branch)
            BRANCH="$2"
            shift
            shift
            ;;
        -id)
            PACKAGE_ID="$2"
            shift
            shift
            ;;
        -version)
            VERSION="$2"
            shift
            shift
            ;;
        *)
            echo "Unknown parameter: $1"
            exit 1
            ;;
    esac
done

# Validate parameters
if [ -z "$GIT_URL" ] || [ -z "$BRANCH" ] || [ -z "$PACKAGE_ID" ] || [ -z "$VERSION" ]; then
    echo "All parameters must be provided"
    exit 1
fi

# Choose target directory name
WEB_FOLDER="/d/web/fhir"
TARGET_DIR="/c/publish/builds/${PACKAGE_ID}#${VERSION}"
IG_REGISTRY = "/C/publish/source/ig-registry"
HISTORY = "/C/publish/source/fhir-ig-history-template"
TEMPLATES = "/C/publish/source/fhir-web-templates"

echo "Starting publishing process..." 
echo "Web Folder: $WEB_FOLDER" 
echo "Git URL: $GIT_URL" 
echo "Branch: $BRANCH" 
echo "Package: $PACKAGE_ID" 
echo "Version: $VERSION" 
echo "Target directory: $TARGET_DIR" 

zulip-send --site https://chat.fhir.org --stream hl7-publication-events --subject "FHIR Publication Bot" --message "$PACKAGE_ID#$VERSION: Starting Run" --user hl7-publication-events-bot@chat.fhir.org --api-key 80qukzU76wbu6PaMHowdQdF7n4UXqbxk

# Update publisher
echo "Updating publisher..." 
SECONDS=0
bash updatePublisher.sh -y  || { echo "Failed to update publisher"; exit 1; }
duration=$SECONDS
echo "   ... $duration seconds" 

npm install -g fsh-sushi

# Update web folder
echo "Updating web folder..." 
SECONDS=0
cd "$WEB_FOLDER" || exit 1
echo "  .. reset" 
git reset --hard HEAD   || exit 1
echo " .. clean" 
git clean -fd   || exit 1
echo " .. pull" 
git pull   || { echo "Failed to pull web folder"; exit 1; }
duration=$SECONDS
echo "   ... $duration seconds" 

# Clone or pull repository
SECONDS=0
if [ -d "$TARGET_DIR/.git" ]; then
    echo "Git Clone exists, pulling latest changes..." 
    cd "$TARGET_DIR"   || exit 1
    git fetch  || { echo "Failed to fetch updates"; exit 1; }
    git checkout "$BRANCH"   || { echo "Failed to checkout branch $BRANCH"; exit 1; }
#    git reset --hard "origin/$BRANCH"    >/dev/null || { echo "Failed to reset to origin/$BRANCH"; exit 1; }
    git pull  >/dev/null || { echo "Failed to pull latest changes"; exit 1; }
else
    echo "Cloning repository..." 
    git clone -b "$BRANCH" "$GIT_URL" "$TARGET_DIR"   || { echo "Failed to clone repository"; exit 1; }
    cd "$TARGET_DIR"  || exit 1
fi
duration=$SECONDS
echo "   ... $duration seconds" 

# Run IG publisher
echo "Running IG publisher..." 
SECONDS=0
java -jar -Xmx20000m /c/publish/publisher.jar -ig .  || { echo "Failed to run IG publisher"; exit 1; }
duration=$SECONDS
echo "   ... $duration seconds"   

if [ -f "$TARGET_DIR/output/qa.html" ]; then
  start $TARGET_DIR/output/qa.html
else
  { echo "IG publisher failed - can't find $TARGET_DIR/output/qa.html"; exit 1; }
fi

# Confirmation step
echo
echo "IG Publication completed. Please review the output and QA at $TARGET_DIR\output\qa.html ."
echo "Would you like to proceed with committing and pushing the changes? (y/n)"
read -r confirmation

if [[ $confirmation =~ ^[Yy]$ ]]; then
    echo "Proceeding with publication..."
else
    echo "Operation cancelled by user"
    exit 1
fi

echo "Doing the Publication"

SECONDS=0
zulip-send --site https://chat.fhir.org --stream hl7-publication-events --subject "FHIR Publication Bot" --message "$PACKAGE_ID#$VERSION: Approved to complete the publication run. Working on it" --user hl7-publication-events-bot@chat.fhir.org --api-key 80qukzU76wbu6PaMHowdQdF7n4UXqbxk

echo "Updating IG registry..."
cd $IG_REGISTRY || exit 1
git pull || { echo "Failed to pull ig-registry"; exit 1; }

echo "Updating the history templates..."
cd $HISTORY || exit 1
git pull || { echo "Failed to pull fhir-ig-history-template"; exit 1; }

echo "Updating the web templates..."
cd $TEMPLATES || exit 1
git pull || { echo "Failed to pull fhir-web-templates"; exit 1; }
duration=$SECONDS
echo "   ... $duration seconds"   

java -jar -Xmx20000m /C/publish/publisher.jar  \
  -go-publish  \
  -source $TARGET_DIR  \
  -web $WEB_FOLDER  \
  -registry $IG_REGISTRY \
  -history $HISTORY \
  -templates $TEMPLATES \
  -zips /D/zips || { echo "Failed to actually do the release"; exit 1; }

if [ -f "/D/zips/$PACKAGE_ID#$VERSION.log" ]; then
  echo "Publication Run Succeeded"; 
else
  { echo "IG publisher failed - can't find /D/zips/$PACKAGE_ID#$VERSION.log"; exit 1; }
fi

zulip-send --site https://chat.fhir.org --stream hl7-publication-events --subject "FHIR Publication Bot" --message "$PACKAGE_ID#$VERSION: publication run done. Committing" --user hl7-publication-events-bot@chat.fhir.org --api-key 80qukzU76wbu6PaMHowdQdF7n4UXqbxk

# Commit and push changes
echo "Committing and pushing changes..."
cd "$WEB_FOLDER" || exit 1
git add . || { echo "Failed to stage changes"; exit 1; }
git commit -m "publish $PACKAGE_ID#$VERSION from $GIT_URL $BRANCH" || { echo "Failed to commit changes"; exit 1; }
git push || { echo "Failed to push changes"; exit 1; }

# git pull on the web site 

echo "Update the actual web site"
ssh git@3.142.231.50    "cd /var/www/html/fhir; git pull; exit;"

echo "Pushing to IG registry..."
cd $IG_REGISTRY || exit 1
git commit -a -m "publish $PACKAGE_ID#$VERSION"
git pull || { echo "Failed to pull from ig-registry"; exit 1; }
git push || { echo "Failed to push to ig-registry"; exit 1; }

zulip-send --site https://chat.fhir.org --stream hl7-publication-events --subject "FHIR Publication Bot" --message "$PACKAGE_ID#$VERSION: All done" --user hl7-publication-events-bot@chat.fhir.org --api-key 80qukzU76wbu6PaMHowdQdF7n4UXqbxk

echo "Draft Announcement:"

cat "/D/zips/$PACKAGE_ID#$VERSION-announcement.txt"

echo "Publishing process completed successfully!"