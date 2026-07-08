package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.convertors.misc.ProfileVersionAdaptor;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;

/**
 * Produces the {@code cross-version-analysis} fragment for an <b>R5 base</b> that emits downgraded
 * R4/R4B variant packages via {@code generate-version}. It is the R5-capable analogue of
 * {@link R4ToR4BAnalyser} (which stays untouched for the legacy R4/R4B base): rather than checking
 * wire-compatibility, it reports the actual per-target conversion outcomes observed while the variant
 * packages are built - the {@link ProfileVersionAdaptor} {@code ConversionMessage} log for
 * StructureDefinition/SearchParameter, and {@code convVersion} success/failure for every other type -
 * plus resources intentionally omitted from a target version via the {@code *-inclusion} params.
 * <p>
 * All target versions are keyed by their family ({@code r4}/{@code r4b}/{@code r5} via
 * {@link PublisherIGLoader#canonicalTarget}) so tokens like {@code 4.0}, {@code 4.0.1} and {@code r4}
 * collate. Strings are English-only for now; i18n is deferred (the existing
 * {@code RenderingI18nContext.R44B_*} phrases are R4/R4B-specific and live in org.hl7.fhir.core).
 */
public class CrossVersionAnalyser {

  private IWorkerContext context;
  private List<String> targets = new ArrayList<>();
  private final Map<String, Map<String, List<String>>> problems = new LinkedHashMap<>();
  private final Map<String, Set<String>> omissions = new LinkedHashMap<>();

  public CrossVersionAnalyser() {
    super();
  }

  public void setContext(IWorkerContext context) {
    this.context = context;
  }

  /** The generate-version targets (raw tokens); used for iteration order and the "all clean" summary. */
  public void setTargets(List<String> targets) {
    if (targets != null) {
      this.targets = new ArrayList<>(targets);
    }
  }

  /** Record the {@link ProfileVersionAdaptor} conversion log for an SD/SP conversion to a target. */
  public void record(String targetVer, String resourceKey, List<ProfileVersionAdaptor.ConversionMessage> log) {
    if (log == null) {
      return;
    }
    for (ProfileVersionAdaptor.ConversionMessage m : log) {
      if (m.getStatus() != ProfileVersionAdaptor.ConversionMessageStatus.NOTE) {
        addProblem(targetVer, resourceKey, m.getMessage());
      }
    }
  }

  /** Record a single conversion problem (e.g. a caught {@code convVersion} failure) for a target. */
  public void recordProblem(String targetVer, String resourceKey, String message) {
    addProblem(targetVer, resourceKey, message);
  }

  /** Record that a resource was intentionally omitted from a target version via {@code *-inclusion}. */
  public void recordOmission(String targetVer, String resourceKey) {
    omissions.computeIfAbsent(key(targetVer), k -> new LinkedHashSet<>()).add(resourceKey);
  }

  private void addProblem(String targetVer, String resourceKey, String message) {
    if (message == null) {
      return;
    }
    problems.computeIfAbsent(key(targetVer), k -> new LinkedHashMap<>())
            .computeIfAbsent(resourceKey, k -> new ArrayList<>())
            .add(message);
  }

  private static String key(String v) {
    return PublisherIGLoader.canonicalTarget(v);
  }

  private static String name(String familyKey) {
    return VersionUtilities.getNameForVersion(familyKey);
  }

  public boolean hasContent() {
    return !problems.isEmpty() || !omissions.isEmpty();
  }

  /**
   * The analysis fragment. {@code inline} yields a terse single-line variant; otherwise a block of
   * {@code <p>}/{@code <ul>} per target version. An all-clean set of targets yields the "OK" summary
   * (non-inline) or "" (inline); no targets at all yields "".
   */
  public String generate(String pid, boolean inline) {
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    for (String t : targets) {
      keys.add(key(t));
    }
    keys.addAll(problems.keySet());
    keys.addAll(omissions.keySet());
    if (keys.isEmpty()) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    boolean any = false;
    for (String k : keys) {
      Map<String, List<String>> probs = problems.get(k);
      Set<String> oms = omissions.get(k);
      boolean hasProbs = probs != null && !probs.isEmpty();
      boolean hasOms = oms != null && !oms.isEmpty();
      if (!hasProbs && !hasOms) {
        continue;
      }
      any = true;
      String nm = name(k);
      if (inline) {
        List<String> parts = new ArrayList<>();
        if (hasProbs) {
          for (Map.Entry<String, List<String>> e : probs.entrySet()) {
            parts.add(e.getKey() + " (" + String.join("; ", e.getValue()) + ")");
          }
        }
        if (hasOms) {
          for (String o : oms) {
            parts.add(o + " not included");
          }
        }
        b.append("Conversion to " + nm + ": " + String.join(", ", parts) + ". ");
      } else {
        b.append("<p>Conversion to " + nm + ":</p>\r\n<ul>\r\n");
        if (hasProbs) {
          for (Map.Entry<String, List<String>> e : probs.entrySet()) {
            b.append("<li>" + Utilities.escapeXml(e.getKey()) + ": " + Utilities.escapeXml(String.join("; ", e.getValue())) + "</li>\r\n");
          }
        }
        if (hasOms) {
          for (String o : oms) {
            b.append("<li>" + Utilities.escapeXml(o) + " - intentionally not included in " + nm + "</li>\r\n");
          }
        }
        b.append("</ul>\r\n");
      }
    }
    if (!any) {
      return inline ? "" : "<p>All resources convert cleanly to the generated version(s): " + versionList() + ".</p>\r\n";
    }
    return b.toString();
  }

  private String versionList() {
    List<String> names = new ArrayList<>();
    for (String t : targets) {
      String n = name(key(t));
      if (!names.contains(n)) {
        names.add(n);
      }
    }
    return String.join(", ", names);
  }
}
