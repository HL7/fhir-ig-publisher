package org.hl7.fhir.igtools.publisher.modules;

import java.util.Map;

import org.hl7.fhir.r5.model.CanonicalResource;

// modules are triggered from ig.ini 
public interface IPublisherModule {

  // names = the name of the routine in this interface 
  boolean useRoutine(String name);
  
  // the code used to invoke this module in ig.ini 
  public String code(); 
  
  // publicly stated name of the module 
  public String name();
  
  // this runs after sushi (if in play) but before anything else happens.
  // returns true if it's OK to go on with the run. If it's not, the reason
  // should be documented clearly in the console output
  // all the actions will be taken on the files in the path (which is the root of the IG that contains ig.ini)
  public boolean preProcess(String path);

  // if the module knows of type aliases, define them for the rendering system
  public void defineTypeMap(Map<String, String> typeMap);

  boolean resolve(String ref);

  CanonicalResource fetchCanonicalResource(String url);

  // approve the generation of a fragment - mainly for performance reasons
  boolean approveFragment(boolean value, String code);

  boolean isNoNarrative();
}
