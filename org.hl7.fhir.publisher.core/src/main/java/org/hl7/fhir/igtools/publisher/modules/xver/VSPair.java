package org.hl7.fhir.igtools.publisher.modules.xver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ValueSet;

public class VSPair {

  private ValueSet vs;
  private String version;
  private Map<String, Set<Coding>> codes = new HashMap<>();

  public VSPair(String version, ValueSet vs, Set<Coding> codes) {
    this.version = version;
    this.vs = vs;
    for (Coding code : codes) {
      Set<Coding> dst = this.codes.get(code.getVersionedSystem());
      if (dst == null) {
        dst = new HashSet<Coding>();
        this.codes.put(code.getVersionedSystem(), dst);        
      }
      dst.add(code);
    }
  }

  public String getVersion() {
    return version;
  }

  public ValueSet getVs() {
    return vs;
  }

  public Map<String, Set<Coding>> getCodes() {
    return codes;
  }

  public Set<Coding> getAllCodes() {
    Set<Coding> res = new HashSet<>();
    for (Set<Coding> set : codes.values()) {
      res.addAll(set);
    }
    return res;
  }

  

  
}