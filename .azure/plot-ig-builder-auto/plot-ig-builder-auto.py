# Description:
# This script reads the output log of the IG Publisher release build process (either from the filesystem or the URL)
# It parses the time info, converts the millisecond values to seconds (for visualization purposes),
# and then plots the results for each tested IG.
#
# Usage:
# Run it from the command line, providing the path or URL as an argument.
# If no argument is passed, it defaults to the github test-statistics URL
#
# For example:
#   python script.py path/to/yourfile.json
# or
#  python script.py https://raw.githubusercontent.com/HL7/fhir-ig-publisher/master/test-statistics.json
#
# Output:
# The plotted graph is stored as a png in a subdirectory:
# ../data/publisher-build-time-trends/{latest-version}.png

import argparse
import json
import matplotlib.pyplot as plt
import requests
import sys
import os

# Function to parse and sort version numbers
def parse_version(version):
    # Split version into major, minor, and patch, and convert them to integers
    try:
        # Original code is now in the 'try' block, indented.
        major, minor, patch = map(int, version.split('.'))
        return major, minor, patch
    except ValueError:  # Handling non-integer splits
        return (0, 0, 0)  # Default value for non-version strings

def load_json_data(source):
    if source.startswith('http://') or source.startswith('https://'):
        # Fetch the JSON data from a URL
        response = requests.get(source)
        response.raise_for_status()  # Raise an exception if the request failed
        data = response.json()
    else:
        # Load the JSON data from a local file
        with open(source, 'r') as file:
            data = json.load(file)
    return data

def main(source):
    data = load_json_data(source)

    # Prepare data for visualization
    build_times = {}  # Structure to hold the build times

    # Extracting the keys, which represent version numbers
    version_keys = list(data.keys())
    version_keys = [key for key in version_keys if key[0].isdigit()]

    # Sorting the version numbers
    sorted_versions = sorted(version_keys, key=parse_version)

    # The latest version is the last one in the sorted list
    latest_version = sorted_versions[-1]

    # ... [The script continues here. The rest of the script remains unchanged.]


    # Construct the filename using the version number
    filename = f"{latest_version}.png"

    # Process the JSON data
    for version, guides in data.items():
        if version == 'format-version':
            continue  # Skip the 'format-version' entry

        for guide, stats in guides.items():
            if guide in ['sync-date', 'date']:
                continue  # Skip non-guide entries

            guide_name = guide
            time = stats.get('time', 0) / 1000.0  # Convert milliseconds to seconds

            if guide_name not in build_times:
                build_times[guide_name] = {}
            build_times[guide_name][version] = time

    # Create the visualization
    for guide, times in build_times.items():
        sorted_items = sorted(times.items())
        versions = [item[0] for item in sorted_items]
        timings = [item[1] for item in sorted_items]

        plt.plot(versions, timings, marker='o', label=guide)

    plt.ylabel('Build Time (seconds)')  # Update label to reflect new units
    plt.xlabel('Version')
    plt.title('Build Time for each Implementation Guide by Version')
    plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.xticks(rotation=45)
    plt.tight_layout()

    # NEW: Specify the directory and create it if it doesn't exist
    # directory = "../data/publisher-build-time-trends"
    # if not os.path.exists(directory):
    #    os.makedirs(directory)

    # NEW: Modify the filename to include the directory path
    # filename = os.path.join(directory, filename)

    # Save the figure
    plt.savefig(args.output)
    # plt.show()

    plt.close(args.output)

if __name__ == "__main__":
    # Set up the command-line argument parser
    parser = argparse.ArgumentParser(description='Visualize FHIR IG Publisher build times.')
    parser.add_argument('--source', type=str, help='The path or URL to the JSON data source')
    parser.add_argument('-o', '--output', type=str, help='Output filename with path', default='../data/publisher-build-time-trends/latest-version.png')  # You can change the default to any relevant path or filename.

    # Parse the arguments
    args = parser.parse_args()
    args.source = args.source if args.source else 'https://raw.githubusercontent.com/HL7/fhir-ig-publisher/master/test-statistics.json'

    try:
        main(args.source)
    except Exception as e:
        print(f"Error: {str(e)}", file=sys.stderr)
    args.source = args.source if (args.source is not None) else 'https://raw.githubusercontent.com/HL7/fhir-ig-publisher/master/test-statistics.json'