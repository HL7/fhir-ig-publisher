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
import pandas as pd

# Function to parse and sort version numbers
def parse_version(version):
    try:
        major, minor, patch = map(int, version.split('.'))
        return major, minor, patch
    except ValueError:
        return (0, 0, 0)  # Default value for non-version strings

def load_json_data(source):
    if source.startswith('http://') or source.startswith('https://'):
        response = requests.get(source)
        response.raise_for_status()
        data = response.json()
    else:
        with open(source, 'r') as file:
            data = json.load(file)
    return data

def calculate_dynamic_width(versions, base_width, additional_width_per_version, max_width):
    num_versions = len(versions)
    if num_versions <= 10:
        return base_width
    else:
        additional_width = (num_versions - 10) * additional_width_per_version
        return min(base_width + additional_width, max_width)

def main(source):
    data = load_json_data(source)

    build_times = {}

    version_keys = [key for key in data.keys() if key[0].isdigit()]
    sorted_versions = sorted(version_keys, key=parse_version)
    latest_version = sorted_versions[-1]

    filename = f"{latest_version}.png"

    for version, guides in data.items():
        if version == 'format-version':
            continue
        for guide, stats in guides.items():
            if guide in ['sync-date', 'date']:
                continue
            guide_name = guide
            time = stats.get('time', 0) / 1000.0  # Convert to seconds

            if guide_name not in build_times:
                build_times[guide_name] = {}
            build_times[guide_name][version] = time

    # Convert to DataFrame and replace 0 with NaN to show gaps instead of 0s
    build_times_df = pd.DataFrame(build_times).replace(0, pd.NA)

    # Define colormaps
    cmap1 = plt.get_cmap('tab20', 20)
    cmap2 = plt.get_cmap('tab20b', 20)
    combined_colors = []

    def add_colors_from_cmap(cmap, num_colors, color_list):
        for i in range(num_colors):
            color_list.append(cmap(i))

    add_colors_from_cmap(cmap1, 20, combined_colors)
    add_colors_from_cmap(cmap2, 20, combined_colors)

    color_index = 0
    handles = []
    timing_label_pairs = []

    for guide in build_times_df.columns:
        total_build_time = build_times_df[guide].sum(skipna=True)
        timing_label_pairs.append((total_build_time, guide))

    timing_label_pairs.sort(reverse=True, key=lambda x: x[0])

    for total_build_time, guide in timing_label_pairs:
        guide_times = build_times_df[guide]
        handle, = plt.plot(guide_times.index, guide_times, marker='o', label=guide, color=combined_colors[color_index % len(combined_colors)])
        handles.append(handle)
        color_index += 1

    plt.legend(handles=handles, bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.ylabel('Build Time (seconds)')
    plt.xlabel('Version')
    plt.title('Build Time for each Implementation Guide by Version')
    plt.xticks(ticks=np.arange(len(sorted_versions)), labels=sorted_versions, rotation=90, fontsize=8)

    # Calculate dynamic width based on the number of versions
    dynamic_width = calculate_dynamic_width(sorted_versions, base_width=8, additional_width_per_version=0.2, max_width=30)
    plt.gcf().set_size_inches(dynamic_width, 5)
    plt.tight_layout()

    # Save the plot
    plt.savefig(args.output)
    plt.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Visualize FHIR IG Publisher build times.')
    parser.add_argument('--source', type=str, help='The path or URL to the JSON data source')
    parser.add_argument('-o', '--output', type=str, help='Output filename with path', default='../data/publisher-build-time-trends/latest-version.png')

    args = parser.parse_args()
    args.source = args.source if args.source else 'https://raw.githubusercontent.com/HL7/fhir-ig-publisher/master/test-statistics.json'

    try:
        main(args.source)
    except Exception as e:
        print(f"Error: {str(e)}", file=sys.stderr)