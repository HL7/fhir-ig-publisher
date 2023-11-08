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
import numpy as np


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

    # Determine the number of unique guides to plot
    num_guides = len(build_times)

    # Define the colormaps
    # More on colormaps: https://matplotlib.org/stable/gallery/color/colormap_reference.html
    cmap1 = plt.get_cmap('tab20', 20) # This map has 20 distinct colors
    cmap2 = plt.get_cmap('tab20b', 20) # This map has 20 distinct colors too
    cmap3 = plt.get_cmap('tab20c', 20)

    # Initialize an empty list to store the colors
    combined_colors = []

    # Function to add colors to the list from a given colormap
    def add_colors_from_cmap(cmap, num_colors, color_list):
        for i in range(num_colors):
            color_list.append(cmap(i))

    # Add colors from each colormap to the combined list
    add_colors_from_cmap(cmap1, 20, combined_colors)
    add_colors_from_cmap(cmap2, 20, combined_colors)
    #add_colors_from_cmap(cmap3, 20, combined_colors)

    # Create the visualization
    color_index = 0

    # Assuming 'build_times' is a dictionary where keys are guide names and values are dictionaries
    # of version: build_time pairs.
    # Start by collecting all timings and labels
    timing_label_pairs = []

    for guide, times in build_times.items():
        # Extract the total build time for the current guide
        total_build_time = sum(times.values())
        # Append the total build time and the guide label to the list as a tuple
        timing_label_pairs.append((total_build_time, guide))

    # Sort the list by timings in descending order
    timing_label_pairs.sort(reverse=True, key=lambda x: x[0])

    # Now we plot in the sorted order and collect handles for the legend
    handles = []
    for total_build_time, guide in timing_label_pairs:
        times = build_times[guide]
        sorted_items = sorted(times.items())
        versions = [item[0] for item in sorted_items]
        timings = [item[1] for item in sorted_items]
        
        # Use the next color in the color list
        handle, = plt.plot(versions, timings, marker='o', label=guide, color=combined_colors[color_index % len(combined_colors)])
        handles.append(handle)
        color_index += 1

    # Update the legend with the sorted handles
    plt.legend(handles=handles, bbox_to_anchor=(1.05, 1), loc='upper left')

    plt.ylabel('Build Time (seconds)')  # Update label to reflect new units
    plt.xlabel('Version')
    plt.title('Build Time for each Implementation Guide by Version')
   
    # Set x-axis ticks to correspond to the actual versions present in the data
    plt.xticks(ticks=np.arange(len(sorted_versions)), labels=sorted_versions, rotation=90, fontsize=8)

    # Assume 'sorted_versions' is the list of version strings from the JSON data
    base_width = 8  # Base width for up to 10 versions
    additional_width_per_version = 0.2  # Additional width for each version above 10
    max_reasonable_width = 30  # Maximum width to keep the plot reasonable
    fixed_height = 5 # Fixed height in inches

    # Calculate the dynamic width based on the number of versions
    dynamic_width = calculate_dynamic_width(sorted_versions, base_width, additional_width_per_version, max_reasonable_width)

    # Set the dynamic figure size
    plt.gcf().set_size_inches(dynamic_width, fixed_height)
    plt.tight_layout()

    # Save the figure
    plt.savefig(args.output)
    # plt.show()

    plt.close(args.output)

def calculate_dynamic_width(versions, base_width, additional_width_per_version, max_width):
    num_versions = len(versions)
    if num_versions <= 10:
        return base_width
    else:
        additional_width = (num_versions - 10) * additional_width_per_version
        return min(base_width + additional_width, max_width)

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