package org.hl7.fhir.igtools.publisher;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.utilities.Utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class PublisherSettings {

    @Getter @Setter private boolean newMultiLangTemplateFormat = false;
    @Getter @Setter private String specifiedVersion;
    @Getter @Setter private PublisherUtils.IGBuildMode mode; // for the IG publication infrastructure
    @Getter @Setter private boolean simplifierMode;
    @Getter @Setter private boolean generationOff;
    @Getter @Setter private boolean debug;
    @Getter @Setter private boolean cacheVersion;
    @Getter @Setter private String targetOutput;
    @Getter @Setter private boolean publishing = false;
    @Getter private Calendar startTime = Calendar.getInstance();
    @Getter @Setter private boolean rapidoMode;
    @Getter @Setter private boolean watchMode;
    @Getter @Setter private boolean validationOff;
    @Getter @Setter private boolean trackFragments = false;
    @Getter @Setter private String sourceDir;
    @Getter @Setter private String destDir;
    @Getter @Setter private String txServer;
    @Getter @Setter private String packageCacheFolder = null;
    @Getter @Setter private String configFile;
    @Getter @Setter private String packagesFolder;
    @Getter @Setter private PublisherUtils.CacheOption cacheOption;
    @Getter @Setter private boolean noSushi;
    @Getter @Setter private String repoSource;
    @Getter @Setter private boolean milestoneBuild;
    @Getter private final List<String> noNarratives = new ArrayList<String>();
    @Getter private final List<String> noValidate = new ArrayList<String>();
    @Getter @Setter String jekyllCommand = "jekyll";

    /**
     * The number of threads, beyond its own, that the publisher may use for work it can do in parallel.
     * 0 (the default) means none: the publisher does everything on one thread. Set with -threads.
     */
    @Getter @Setter private int threads = 0;

    /**
     * @param value the value of the -threads parameter
     * @param cores the number of cores (Runtime.availableProcessors())
     * @return the number of extra threads to use: the value asked for, but no more than cores - 1
     */
    public static int parseThreads(String value, int cores) {
        int requested;
        try {
            requested = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The -threads parameter needs a whole number of threads, not '" + value + "'");
        }
        if (requested < 0) {
            throw new IllegalArgumentException("The -threads parameter can't be negative: " + value);
        }
        return Math.min(requested, Math.max(0, cores - 1));
    }
}