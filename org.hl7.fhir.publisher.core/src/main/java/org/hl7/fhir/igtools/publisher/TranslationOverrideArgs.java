package org.hl7.fhir.igtools.publisher;

import org.hl7.fhir.utilities.i18n.POGenerator;
import org.hl7.fhir.utilities.i18n.RuntimePOLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the FHIR validator CLI's {@code -po} / {@code -po-dir} / {@code -po-stale-handling}
 * options for the IG Publisher.
 * <p>
 * The validator declares these via picocli (see {@code TranslationOverrideOptions} in
 * {@code org.hl7.fhir.validation.cli.picocli.options}); the IG Publisher hand-parses its
 * args via {@link CliParams}, so this class reproduces the same surface using
 * {@link RuntimePOLoader#install(List, POGenerator.StaleHandling)}.
 * <p>
 * Filenames must follow the project convention used by {@code POGenerator}, e.g.
 * {@code validator-messages-de.po} or {@code rendering-phrases-pt_BR.po}.
 * <p>
 * Call {@link #applyIfRequested(String[])} early in {@code main}, before any message
 * bundles or the validation engine load — the bundles latch on first access.
 */
public class TranslationOverrideArgs {

  public static final String PARAM_PO = "-po";
  public static final String PARAM_PO_DIR = "-po-dir";
  public static final String PARAM_PO_STALE_HANDLING = "-po-stale-handling";

  private TranslationOverrideArgs() {
    // utility class
  }

  /**
   * Reads any {@code -po}, {@code -po-dir} and {@code -po-stale-handling} flags from
   * {@code args} and installs a runtime PO overlay. No-op if no relevant flags are
   * present.
   */
  public static void applyIfRequested(String[] args) {
    List<File> poFiles = new ArrayList<>();

    for (String f : CliParams.getNamedParams(args, PARAM_PO)) {
      poFiles.add(new File(f));
    }
    for (String d : CliParams.getNamedParams(args, PARAM_PO_DIR)) {
      File dir = new File(d);
      try {
        poFiles.addAll(RuntimePOLoader.expandDirectory(dir));
      } catch (IOException e) {
        throw new RuntimeException("Cannot read " + PARAM_PO_DIR + " " + d + ": " + e.getMessage(), e);
      }
    }

    if (poFiles.isEmpty()) {
      return;
    }

    POGenerator.StaleHandling staleHandling = POGenerator.StaleHandling.INCLUDE;
    String handling = CliParams.getNamedParam(args, PARAM_PO_STALE_HANDLING);
    if (handling != null) {
      try {
        staleHandling = POGenerator.StaleHandling.valueOf(handling.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new RuntimeException("Unrecognised " + PARAM_PO_STALE_HANDLING + " value '" + handling
            + "'. Expected one of: include, exclude, warn.", e);
      }
    }

    try {
      RuntimePOLoader.install(poFiles, staleHandling);
      System.out.println("Runtime PO overlay installed: " + poFiles.size()
          + " file(s), stale-handling=" + staleHandling);
    } catch (IOException e) {
      throw new RuntimeException("Failed to install PO overlay: " + e.getMessage(), e);
    }
  }
}
