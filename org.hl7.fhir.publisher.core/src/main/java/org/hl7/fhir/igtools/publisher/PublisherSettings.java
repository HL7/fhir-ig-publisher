package org.hl7.fhir.igtools.publisher;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.utilities.Utilities;

import java.util.Calendar;

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
    @Getter @Setter private String targetOutputNested;
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
}