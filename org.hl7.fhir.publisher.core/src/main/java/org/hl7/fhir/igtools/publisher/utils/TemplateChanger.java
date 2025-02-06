package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;

public class TemplateChanger {

  private String folder;
  
  private List<File> fixed = new ArrayList<>();
  private List<File> notFixed = new ArrayList<>();

  private String thb;

  private String the;

  private String ttb;

  private String tte;

  private String tbb;

  private String tbe;

  private File thf;

  private String th;

  private File ttf;

  private String tt;

  private File tbf;

  private String tb;

  public TemplateChanger(String folder) {
    this.folder = folder;
  }

  public static void main(String[] args) throws Exception {
    new TemplateChanger(args[0]).execute("true".equals(args[1]));
  }

  private void execute(boolean logNotDone) throws IOException {
    // load publish.ini
    File iniF = new File(Utilities.path(folder, "publish.ini"));
    check(iniF.exists(), "The folder "+folder+" does not contain a publish.ini file");
    IniFile ini = new IniFile(new FileInputStream(iniF));
    
    thb = ini.getStringProperty("template", "head-begin");
    the = ini.getStringProperty("template", "head-end");
    ttb = ini.getStringProperty("template", "top-begin");
    tte = ini.getStringProperty("template", "top-end");
    tbb = ini.getStringProperty("template", "bottom-begin");
    tbe = ini.getStringProperty("template", "bottom-end");

    // load template fragments
    check(ini.hasProperty("template", "head"), "The file "+iniF.getAbsolutePath()+" does not contain a head template");
    thf = new File(Utilities.path(folder, ini.getStringProperty("template", "head")));
    check(thf.exists(), "The head template file "+thf+" does not exist");
    th = FileUtilities.fileToString(thf);
    check(thb != null, "The file "+iniF.getAbsolutePath()+" does not contain a template head-begin marker");
    check(the != null, "The file "+iniF.getAbsolutePath()+" does not contain a template head-end marker");
    
    check(ini.hasProperty("template", "top"), "The file "+iniF.getAbsolutePath()+" does not contain a top template");
    ttf = new File(Utilities.path(folder, ini.getStringProperty("template", "top")));
    check(ttf.exists(), "The top template file "+ttf+" does not exist");
    tt = FileUtilities.fileToString(ttf);
    check(ttb != null, "The file "+iniF.getAbsolutePath()+" does not contain a template top-begin marker");
    check(tte != null, "The file "+iniF.getAbsolutePath()+" does not contain a template top-end marker");

    check(ini.hasProperty("template", "bottom"), "The file "+iniF.getAbsolutePath()+" does not contain a bottom template");
    tbf = new File(Utilities.path(folder, ini.getStringProperty("template", "bottom")));
    check(tbf.exists(), "The bottom template file "+tbf+" does not exist");
    tb = FileUtilities.fileToString(tbf);
    check(tbb != null, "The file "+iniF.getAbsolutePath()+" does not contain a template bottom-begin marker");
    check(tbe != null, "The file "+iniF.getAbsolutePath()+" does not contain a template bottom-end marker");
       
    System.out.println("Update HTML template in "+folder);
    System.out.println("Head: Replace from "+thb+" to "+ the+" with "+thf.getAbsolutePath());
    System.out.println("Top: Replace from "+ttb+" to "+ tte+" with "+thf.getAbsolutePath());
    System.out.println("Bottom: Replace from "+tbb+" to "+ tbe+" with "+thf.getAbsolutePath());
    System.out.println("Go: Y/n?");
    int r = System.in.read();
    if (r != 'n') {
      // walk the paths changing the files
      processFiles(new File(folder), "");    

      System.out.println("Done. "+fixed.size()+" files changed");
      System.out.println(""+notFixed.size()+" files not changed as they did not meet the criteria");

      if (logNotDone) {
        for (File f : notFixed) {
          System.out.println("  "+f.getAbsolutePath().substring(folder.length()+1));        
        }
      }
    }
  }

  private void processFiles(File dir, String path) throws IOException {
    System.out.print("Process "+dir);
    int i = 0;
    for (File f : dir.listFiles()) {
      if (!f.isDirectory() && f.getName().endsWith(".html") && !Utilities.existsInList(f.getAbsolutePath(), thf.getAbsolutePath(), ttf.getAbsolutePath(), tbf.getAbsolutePath())) {
        i++;
        if (i % 100 == 0) {
          System.out.print(".");          
        }
        String cnt = FileUtilities.fileToString(f);
         if (hasSeps(cnt, thb, the) && hasSeps(cnt, ttb, tte) && hasSeps(cnt, tbb, tbe)) {
           fixed.add(f);
           cnt = replaceSeps(cnt, prep(th, path), thb, the);
           cnt = replaceSeps(cnt, prep(tt, path), ttb, tte);
           cnt = replaceSeps(cnt, prep(tb, path), tbb, tbe);
           FileUtilities.stringToFile(cnt, f);
         } else {
           notFixed.add(f);
         }
      }
    }
    System.out.println("");    
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        processFiles(f, path + "../");
      } 
    }
  }

  private String replaceSeps(String cnt, String tmp, String b, String e) {
    int ib = cnt.indexOf(b);
    int ie = cnt.indexOf(e);
    String start = cnt.substring(0, ib+b.length());
    String end = cnt.substring(ie);
    return start+"\r\n"+tmp+"\r\n"+end;
  }

  private String prep(String tmp, String path) {
    return tmp.replace("{{path}}", path);
  }

  private boolean hasSeps(String cnt, String b, String e) {
    int ib = cnt.indexOf(b);
    int ie = cnt.indexOf(e);
    return (ib > 0) && (ie > ib);
  }

  private void check(boolean test, String msg) {
    if (!test) {
      throw new FHIRException(msg);
    }
  }
  

}
