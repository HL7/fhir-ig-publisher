package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.DependentIGFinder.DepInfo;
import org.hl7.fhir.igtools.publisher.DependentIGFinder.DeplistSorter;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JSONUtil;
import org.hl7.fhir.utilities.json.JsonTrackingParser;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.ToolsVersion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class DependentIGFinder {

  public class DeplistSorter implements Comparator<DepInfo> {

    @Override
    public int compare(DepInfo arg0, DepInfo arg1) {
      return arg0.pid.compareTo(arg1.pid);
    }

  }

  public class DepInfo {
    private String path;
    private String pid;
    private String cibuild;
    private String ballot;
    private String published;
    public DepInfo(String pid, String path) {
      super();
      this.pid = pid;
      this.path = path;
    }    
  }

  private List<DepInfo> deplist = new ArrayList<>();
  
  private String id; // the id of the IG in question
  private String outcome; // html rendering
  private boolean debug = false;
  private boolean working = true;
  private FilesystemPackageCacheManager pcm;
  

  public DependentIGFinder(String id) throws IOException {
    super();
    this.id = id;
    pcm = new  FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION);
    pcm.setSilent(true);
    outcome = "Finding Dependent IGs not done yet";

    startThread();

  }

  private void startThread() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          analyse();
        } catch (Exception e) {
          if (debug) System.out.println("Processing DependentIGs failed: "+e.getMessage());
        }
      }

    }).start();
  }

  private void analyse() {
    try {
      JsonObject json = JsonTrackingParser.fetchJson("https://raw.githubusercontent.com/FHIR/ig-registry/master/fhir-ig-list.json");
      for (JsonObject guide : JSONUtil.objects(json, "guides")) {
        checkIGDependencies(guide);
        outcome = render(false);
      }
      outcome = render(true);
      working = false;
    } catch (Exception e) {
      outcome = "<span style=\"color: maroon\">Error analysing dependencies: "+Utilities.escapeXml(e.getMessage())+"</span>";
    }
  }

  private String render(boolean done) {
    Collections.sort(deplist, new DeplistSorter());
    StringBuilder b = new StringBuilder();
    boolean found = false;
    int c = 0;
    b.append("<table>");
    b.append("<tr>");
    b.append("<td>id</td>");
    b.append("<td>published</td>");
    b.append("<td>ballot</td>");
    b.append("<td>current</td>");
    b.append("</tr>");
    for (DepInfo dep : deplist) {
      if (isFound(dep.ballot) || isFound(dep.cibuild) || isFound(dep.published)) {
        c++;
        b.append("<tr>");
        b.append("<td><a href=\""+dep.path+"\">"+dep.pid+"</a></td>");
        b.append("<td>"+present(dep.published, true)+"</td>");
        b.append("<td>"+present(dep.ballot, true)+"</td>");
        b.append("<td>"+present(dep.cibuild, false)+"</td>");
        b.append("</tr>");
        found = true;
      }
    }
    b.append("</table>");
    String s = done ? "" : " <span style=\"color: navy\">Still in progress</span>";
    if (!found) {
      return "(no references)"+s;
    } else {
      return "<p>"+c+" "+Utilities.pluralize("guide", c)+". <span id=\"sdep-ig-list\" onClick=\"flipg('dep-ig-list')\" style=\"color: navy\">Show the list</span></p><div id=\"dep-ig-list\" style=\"display: none\">"+b.toString()+s+"</div>";
    }
  }

  private String present(String s, boolean currentWrong) {
    if (s == null) {
      return "";
    }
    if (s.equals("??")) {
      return "<span style=\"color: maroon\">??</span>";
    }
    if (s.equals("current") && currentWrong) {
      return "<span style=\"color: maroon\">current</span>";
    }      
    return s;
  }

  private boolean isFound(String s) {
    return s != null && !s.equals("??");
  }

  private void checkIGDependencies(JsonObject guide) {
    // we only check the latest published version, and the CI build
    try {
      JsonObject pl = JsonTrackingParser.fetchJson(Utilities.pathURL(guide.get("canonical").getAsString(), "package-list.json"));
      String pid = JSONUtil.str(guide, "npm-name");
      String canonical = JSONUtil.str(guide, "canonical");
      DepInfo dep = new DepInfo(pid, Utilities.path(canonical, "history.html"));
      deplist.add(dep);
      for (JsonObject list : JSONUtil.objects(pl, "list")) {
        boolean ballot = true;
        String version = JSONUtil.str(list, "version");
        if (!"current".equals(version)) {
          String status = JSONUtil.str(list, "status");
          if ("ballot".equals(status)) {
            if (!ballot) {
              ballot = true;
              dep.ballot = checkForDependency(pid, version);          
            }
          } else {
            dep.published = checkForDependency(pid, version);                    
            break;
          }
        }
      }
      dep.cibuild = checkForDependency(pid, "current");
    } catch (Exception e) {
      if (debug) System.out.println("Dpendency Analysis - Unable to process "+JSONUtil.str(guide, "name")+": " +e.getMessage());
    }    
  }

  private String checkForDependency(String pid, String version) throws IOException {
    try {
      NpmPackage npm = pcm.loadPackage(pid, version);
      for (String dep : npm.dependencies()) {
        if (dep.startsWith(id+"#")) {
          return dep.substring(dep.indexOf("#")+1); 
        }
      }
    } catch (Exception e) {
      if (debug) System.out.println("Dependency Analysis - Unable to process "+pid+": " +e.getMessage());
      return "??";
    }    
    return null;
  }

  public String getId() {
    return id;
  }

  public String getOutcome(boolean wait) {
    while (working && wait) {
      try {
        Thread.sleep(1000);
        System.out.println("Waiting for dependency analysis");
      } catch (InterruptedException e) {
      }
    }
    return outcome;
  }



}
