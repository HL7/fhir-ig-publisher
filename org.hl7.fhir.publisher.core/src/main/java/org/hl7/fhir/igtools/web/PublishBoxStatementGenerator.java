package org.hl7.fhir.igtools.web;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class PublishBoxStatementGenerator {
  
  /**
   * The fragment of HTML this generates has 3 parts 
   * 
   * 1. statement of what this is 
   * 2. reference to current version
   * 3. reference to list of published versions
   * 
   * @param version
   * @param root
   * @param canonical
   * @return
   */
  public static String genFragment(PackageList ig, PackageListEntry version, PackageListEntry root, String canonical, boolean currentPublication, boolean isCore) {
    return genFragment(ig, version, root, canonical, currentPublication, isCore, false);
  }

  /**
   * @param dynamicCurrentVersion when true, the "current version" reference is emitted as a
   *   static, version-agnostic placeholder filled in client-side from package-list.json (see
   *   {@link #dynamicCurrentVersionBlock}). The fragment is then identical on every page of every
   *   version regardless of which version is current, so cutting a new milestone never has to
   *   rewrite the publish box in past versions. Opt-in (publish-setup.json website.dynamic-publish-box).
   */
  public static String genFragment(PackageList ig, PackageListEntry version, PackageListEntry root, String canonical, boolean currentPublication, boolean isCore, boolean dynamicCurrentVersion) {
    String p1, p2, p3;
    if ("withdrawn".equals(version.status())) {
      p1 = ig.title()+" Withdrawal notice (v"+version.version()+": "+state(ig, version)+").";
      p2 = "";
      p3 = " For a full list of versions prior to withdrawal, see the <a data-no-external=\"true\" href=\""+canonical+"/history.html\">Directory of published versions</a>";
      return "This page is the "+p1+" "+p3;
    } else {
      p1 = ig.title()+" (v"+version.version()+": "+state(ig, version)+")";
      if (!isCore) {
        p1 = p1 + (version.fhirVersion() != null ? (isCDA(canonical) ? " generated with " : " based on ")+"<a data-no-external=\"true\" href=\"http://hl7.org/fhir/"+getPath(version.fhirVersion())+"\">FHIR (HL7® FHIR® Standard) "+fhirRef(version.fhirVersion())+"</a>" : "")+". ";
      } else {
        p1 = p1 + ". ";
      }

      if (dynamicCurrentVersion) {
        // version-agnostic: which version is current is resolved client-side, so this fragment
        // does not name root.version() and is identical no matter what the current version is.
        p2 = dynamicCurrentVersionBlock(version, canonical);
      } else if (root == null) {
        p2 = "No current official version has been published yet";
      } else if (version == root) {
        p2 = "This is the current published version"+(currentPublication ? "" : " in its permanent home (it will always be available at this URL)");
      } else if ("withdrawn".equals(root.status())){
        p2 = "This specification was withdrawn after the publication of this version: see <a data-no-external=\"true\" href=\""+(root.path().startsWith(canonical) ? canonical : root.path())+"{{fn}}\">Withdrawal Notice</a>";
      } else if (VersionUtilities.compareVersions(root.version(), version.version()) > 0) {
        p2 = "The current version which supersedes this version is <a data-no-external=\"true\" href=\""+(root.path().startsWith(canonical) ? canonical : root.path())+"{{fn}}\">"+root.version()+"</a>";
      } else {
        p2 = "This version is a pre-release. The current official version is <a data-no-external=\"true\" href=\""+(root.path().startsWith(canonical) ? canonical : root.path())+"{{fn}}\">"+root.version()+"</a>";
      }
      if (canonical.equals("http://hl7.org/fhir")) {
        p3 = " For a full list of available versions, see the <a data-no-external=\"true\" href=\""+canonical+"/directory.html\">Directory of published versions</a>";
      } else if (root != null && "withdrawn".equals(root.status())) {
        p3 = " For a full list of versions prior to withdrawal, see the <a data-no-external=\"true\" href=\""+canonical+"/history.html\">Directory of published versions</a>";
      } else {
        p3 = " For a full list of available versions, see the <a data-no-external=\"true\" href=\""+canonical+"/history.html\">Directory of published versions</a>";
      }
      return "This page is part of the "+p1+p2+". "+p3;
    }
  }

  /**
   * The version-agnostic "current version" statement. Emits all three wordings (superseded /
   * is-current / pre-release) with the superseded wording shown by default (no-JS fallback). A
   * small inline script reads package-list.json (same-origin) at page load, fills in the current
   * version number + link, and shows the wording that matches this page's own version. Because the
   * markup names neither the current version nor a per-file deep link ({{fn}}), it is byte-identical
   * on every page of every version, so a new milestone never rewrites past versions' publish boxes.
   */
  private static String dynamicCurrentVersionBlock(PackageListEntry version, String canonical) {
    String v = Utilities.escapeXml(version.version());
    String c = Utilities.escapeXml(canonical);
    String curLink = "<a class=\"fhir-pb-current-link\" data-no-external=\"true\" href=\""+c+"\"><span class=\"fhir-pb-current-version\">the current version</span></a>";
    StringBuilder b = new StringBuilder();
    b.append("<span class=\"fhir-pb-dynamic\" data-pb-version=\""+v+"\" data-pb-canonical=\""+c+"\">");
    b.append("<span class=\"fhir-pb-superseded\">The current version which supersedes this version is "+curLink+"</span>");
    b.append("<span class=\"fhir-pb-iscurrent\" style=\"display:none\">This is the current published version</span>");
    b.append("<span class=\"fhir-pb-prerelease\" style=\"display:none\">This version is a pre-release. The current official version is "+curLink+"</span>");
    b.append("</span>");
    b.append(CURRENT_VERSION_SCRIPT);
    return b.toString();
  }

  // Self-contained, idempotent. One fetch of package-list.json (same-origin, derived from the
  // canonical path so it works under https without mixed-content) drives both:
  //   (1) the current-version reference (.fhir-pb-dynamic) — mirrors server-side current-detection
  //       (current===true, not the ci-build "current" entry, path under canonical) and toggles wording;
  //   (2) the "Page versions:" list (.fhir-pb-page-versions) — links this page across milestone folders,
  //       HEAD-probing each so only milestones that actually contain this page are shown (matching the
  //       server's behaviour) without baking the list into the page (so a new milestone never rewrites it).
  private static final String CURRENT_VERSION_SCRIPT =
    "<script type=\"text/javascript\">"
    + "(function(){"
    + "function cmp(a,b){if(a===b)return 0;function p(v){var s=String(v).split('-');return{n:s[0].split('.').map(function(x){return parseInt(x,10)||0;}),pre:s.length>1?s.slice(1).join('-'):null};}var pa=p(a),pb=p(b),i,m=Math.max(pa.n.length,pb.n.length);for(i=0;i<m;i++){var d=(pa.n[i]||0)-(pb.n[i]||0);if(d!==0)return d>0?1:-1;}if(pa.pre&&!pb.pre)return -1;if(!pa.pre&&pb.pre)return 1;if(pa.pre&&pb.pre)return pa.pre<pb.pre?-1:(pa.pre>pb.pre?1:0);return 0;}"
    + "function sh(el,on){if(el)el.style.display=on?'':'none';}"
    + "function pn(u){try{return new URL(u,document.baseURI).pathname.replace(/\\/+$/,'');}catch(e){return null;}}"
    + "function init(){"
    + "var dyn=document.querySelectorAll('span.fhir-pb-dynamic');"
    + "var pv=document.querySelectorAll('span.fhir-pb-page-versions');"
    + "if(!dyn.length&&!pv.length)return;"
    + "var canonical=dyn.length?dyn[0].getAttribute('data-pb-canonical'):null;"
    + "if(!canonical)return;"
    // locate package-list.json relative to THIS page's served location (so it works under any base
    // path - preview/staging/mirror - not only when served at the canonical path). Derive the IG root
    // from this page's own version folder in the URL; fall back to the canonical path.
    + "var pgVer=(dyn.length?dyn[0]:pv[0]).getAttribute('data-pb-version');"
    + "var igRoot=null;"
    + "if(pgVer){var seg='/'+pgVer+'/';var si=location.pathname.indexOf(seg);if(si>=0)igRoot=location.pathname.slice(0,si);}"
    + "if(igRoot===null)igRoot=pn(canonical);"
    + "if(igRoot===null)return;"
    + "var plUrl=igRoot+'/package-list.json';"
    + "fetch(plUrl,{cache:'no-cache'}).then(function(r){return r.json();}).then(function(pl){"
    + "var list=(pl&&pl.list)||[];"
    + "var cur=null;list.forEach(function(e){if(e&&e.current===true&&e.version!=='current'&&typeof e.path==='string'&&e.path.indexOf(canonical)===0){cur=e;}});"
    + "if(cur)Array.prototype.forEach.call(dyn,function(box){"
    + "if(box.getAttribute('data-pb-done'))return;box.setAttribute('data-pb-done','1');"
    + "var thisVer=box.getAttribute('data-pb-version');"
    + "Array.prototype.forEach.call(box.querySelectorAll('.fhir-pb-current-version'),function(s){s.textContent=cur.version;});"
    + "Array.prototype.forEach.call(box.querySelectorAll('a.fhir-pb-current-link'),function(a){a.setAttribute('href',cur.path);});"
    + "var d=cmp(cur.version,thisVer);"
    + "sh(box.querySelector('.fhir-pb-superseded'),d>0);sh(box.querySelector('.fhir-pb-iscurrent'),d===0);sh(box.querySelector('.fhir-pb-prerelease'),d<0);"
    + "});"
    + "var ms=list.filter(function(e){return e&&e.milestoneName&&typeof e.path==='string'&&e.path.indexOf(canonical)===0;});"
    + "Array.prototype.forEach.call(pv,function(span){"
    + "if(span.getAttribute('data-pb-done'))return;span.setAttribute('data-pb-done','1');"
    + "var thisVer=span.getAttribute('data-pb-version');"
    + "var mine=list.filter(function(e){return e.version===thisVer;})[0];"
    + "var myBase=mine?pn(mine.path):null;var here=location.pathname;"
    + "var rel=(myBase&&here.indexOf(myBase+'/')===0)?here.slice(myBase.length+1):here.substring(here.lastIndexOf('/')+1);"
    + "Promise.all(ms.map(function(m){var href=pn(m.path)+'/'+rel;"
    + "if(m.version===thisVer)return Promise.resolve({m:m,href:href,ok:true,self:true});"
    + "return fetch(href,{method:'HEAD'}).then(function(r){return{m:m,href:href,ok:r.ok,self:false};}).catch(function(){return{m:m,href:href,ok:false};});"
    + "})).then(function(res){var parts=res.filter(function(x){return x.ok;}).map(function(x){return x.self?('<b>'+x.m.milestoneName+'</b>'):('<a data-no-external=\"true\" href=\"'+x.href+'\">'+x.m.milestoneName+'</a>');});"
    + "if(parts.length){span.innerHTML='. Page versions: '+parts.join(' ');}});"
    + "});"
    + "}).catch(function(){});"
    + "}"
    + "if(document.readyState!=='loading'){init();}else{document.addEventListener('DOMContentLoaded',init);}"
    + "})();"
    + "</script>";

  private static boolean isCDA(String canonical) {
    return canonical.startsWith("http://hl7.org/cda");
  }


  private static String getPath(String v) {
    if ("5.0.0".equals(v))
      return "R5";
    if ("4.0.1".equals(v))
      return "R4";
    if ("4.0.0".equals(v))
      return "R4";
    if ("3.5a.0".equals(v))
      return "2018Dec";
    if ("3.5.0".equals(v))
      return "2018Sep";
    if ("3.3.0".equals(v))
      return "2018May";
    if ("3.2.0".equals(v))
      return "2018Jan";
    if ("3.0.0".equals(v))
      return "STU3";
    if ("3.0.1".equals(v))
      return "STU3";
    if ("3.0.2".equals(v))
      return "STU3";
    if ("1.8.0".equals(v))
      return "2017Jan";
    if ("1.6.0".equals(v))
      return "2016Sep";
    if ("1.4.0".equals(v))
      return "2016May";
    if ("1.1.0".equals(v))
      return "2015Dec";
    if ("1.0.2".equals(v))
      return "DSTU2";
    if ("1.0.0".equals(v))
      return "2015Sep";
    if ("0.5.0".equals(v))
      return "2015May";
    if ("0.4.0".equals(v))
      return "2015Jan";
    if ("0.0.82".equals(v))
      return "DSTU1";
    if ("0.11".equals(v))
      return "2013Sep";
    if ("0.06".equals(v))
      return "2013Jan";
    if ("0.05".equals(v))
      return "2012Sep";
    if ("0.01".equals(v))
      return "2012May";
    if ("current".equals(v))
      return "2011Aug";
    return v;
  }

  private static String fhirRef(String v) {
    if (VersionUtilities.isR2Ver(v))
      return "R2";
    if (VersionUtilities.isR3Ver(v))
      return "R3";
    if (VersionUtilities.isR4Ver(v))
      return "R4";    
    return "v"+v;
  }

  private static String state(PackageList ig, PackageListEntry version) {
    String status = version.status();
    String sequence = version.sequence();
    if ("trial-use".equals(status))
      return decorate(sequence);
    else if ("release".equals(status))
      return "Release";
    else if ("preview".equals(status) || "qa-preview".equals(status))
      return "QA Preview";
    else if ("ballot".equals(status)) {
      String bc = ballotCount(ig, sequence, version);
      if (Utilities.noString(bc)) {
        return decorate(sequence+" Ballot");
      } else {
        return decorate(sequence)+" Ballot "+bc;
      }
    } else if ("public-comment".equals(status))
      return decorate(sequence)+" Public Comment";
    else if ("draft".equals(status))
      return decorate(sequence)+" Draft";
    else if ("update".equals(status))
      return decorate(sequence)+" Update";
    else if ("normative+trial-use".equals(status))
      return decorate(sequence+" - Mixed Normative and STU");
    else if ("normative".equals(status))
      return decorate(sequence+" - Normative");
    else if ("informative".equals(status))
      return decorate(sequence+" - Informative");
    else if ("corrected".equals(status))
      return decorate(sequence+" - Replaced");
    else if ("withdrawn".equals(status))
      return decorate(sequence+" - Withdrawn");
    else 
      throw new Error("unknown status "+status);
  }

  private static String decorate(String sequence) {
    sequence = sequence.replace("Normative", "<a data-no-external=\"true\" href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Normative Standard\">Normative</a>");
    if (sequence.contains("DSTU"))
      return sequence.replace("DSTU", "<a data-no-external=\"true\" href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Draft Standard for Trial-Use\">DSTU</a>");
    else
      return sequence.replace("STU", "<a data-no-external=\"true\" href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Standard for Trial-Use\">STU</a>");
  }

  private static String ballotCount(PackageList ig, String sequence, PackageListEntry version) {
    int c = 1;
    for (int i = ig.list().size() - 1; i >= 0; i--) {
      PackageListEntry o = ig.list().get(i);
      if (o == version) {
        return c == 0 ? "" : Integer.toString(c);
      }
      if (Utilities.existsInListNC(o.status(), "trial-use", "normative")) {
        c = 0;
      }
      if (sequence.equals(o.sequence()) && "ballot".equals(o.status())) {
        c++;
      }
    }
    return "1";
  }

}
