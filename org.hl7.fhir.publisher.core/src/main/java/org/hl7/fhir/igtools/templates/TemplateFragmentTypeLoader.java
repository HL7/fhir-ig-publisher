package org.hl7.fhir.igtools.templates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TemplateFragmentTypeLoader {

  // Regex pattern to match Liquid include statements
  private static final Pattern INCLUDE_PATTERN =
          Pattern.compile("\\{%\\s*include\\s+['\"]?([^'\"\\s%]+)['\"]?\\s*%\\}");

  /**
   * Represents a prefix with its list of suffixes
   */
  public class PrefixGroup {
    public String prefix;
    public List<String> suffixes;

    public PrefixGroup(String prefix) {
      this.prefix = prefix;
      this.suffixes = new ArrayList<>();
    }

    public void addSuffix(String suffix) {
      if (!suffixes.contains(suffix)) {
        suffixes.add(suffix);
      }
    }

    @Override
    public String toString() {
      return prefix + ": " + suffixes;
    }
  }

  /**
   * Finds all .html files in a directory and its subdirectories
   */
  public List<Path> findHtmlFiles(String directoryPath) throws IOException {
    Path startPath = Paths.get(directoryPath);

    try (Stream<Path> paths = Files.walk(startPath)) {
      return paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().toLowerCase().endsWith(".html"))
              .collect(Collectors.toList());
    }
  }

  /**
   * Extracts all include filenames from a single file
   */
  public Set<String> extractIncludes(Path filePath) throws IOException {
    Set<String> includes = new HashSet<>();
    String content = Files.readString(filePath);

    Matcher matcher = INCLUDE_PATTERN.matcher(content);
    while (matcher.find()) {
      includes.add(matcher.group(1));
    }

    return includes;
  }

  /**
   * Finds all included files across all .html files in a directory
   */
  public List<String> findAllIncludes(String directoryPath) throws IOException {
    List<Path> htmlFiles = findHtmlFiles(directoryPath);
    Set<String> allIncludes = new HashSet<>();

    for (Path file : htmlFiles) {
      Set<String> includes = extractIncludes(file);
      allIncludes.addAll(includes);
    }

    return new ArrayList<>(allIncludes);
  }

  /**
   * Process a list of include files and group by prefix
   * Pattern: <prefix>-{{[id]}}-<suffix>{{[langsuffix]}}.xhtml
   *
   * Only processes files containing {{[id]}}
   * Removes .xhtml extension and {{[langsuffix]}}
   * Splits into prefix and suffix, preserving hyphens within suffix
   */
  public Map<String, PrefixGroup> processIncludes(List<String> includes) {
    Map<String, PrefixGroup> groupedByPrefix = new LinkedHashMap<>();

    // Pattern to match files with {{[id]}}
    // Captures: (prefix)-{{[id]}}-(suffix){{[langsuffix]}}.xhtml
    Pattern pattern = Pattern.compile("^(.+?)-\\{\\{\\[id\\]\\}\\}-(.+?)(?:\\{\\{\\[langsuffix\\]\\}\\})?\\.xhtml$");

    for (String include : includes) {
      // Only process files that contain {{[id]}}
      if (!include.contains("{{[id]}}")) {
        continue;
      }

      Matcher matcher = pattern.matcher(include);
      if (matcher.matches()) {
        String prefix = matcher.group(1);
        String suffix = matcher.group(2);

        // Get or create prefix group
        PrefixGroup group = groupedByPrefix.computeIfAbsent(prefix, PrefixGroup::new);
        if (suffix.contains("{{format}}")) {
          group.addSuffix(suffix.replace("{{format}}", "json"));
          group.addSuffix(suffix.replace("{{format}}", "xml"));
          group.addSuffix(suffix.replace("{{format}}", "ttl"));
        } else {
          group.addSuffix(suffix);
        }
      }
    }

    // Sort suffixes within each group
    for (PrefixGroup group : groupedByPrefix.values()) {
      Collections.sort(group.suffixes);
    }

    return groupedByPrefix;
  }

  /**
   * Get the grouped results as a structured data format
   */
  public Map<String, List<String>> getGroupedAsMap(Map<String, PrefixGroup> grouped) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (Map.Entry<String, PrefixGroup> entry : grouped.entrySet()) {
      result.put(entry.getKey(), new ArrayList<>(entry.getValue().suffixes));
    }
    return result;
  }

  // Example usage
  public Map<String, PrefixGroup> process(String directoryPath) {
    try {
      System.out.println("Scanning directory: " + directoryPath);
      System.out.println("=".repeat(70));

      // Find all includes
      List<String> includes = findAllIncludes(directoryPath);

      // Process and group includes
      Map<String, PrefixGroup> grouped = processIncludes(includes);
      return grouped;
    } catch (IOException e) {
      System.err.println("Error processing files: " + e.getMessage());
      return null;
    }
  }

}
