package org.hl7.fhir.igtools.publisher.modules;

import java.util.Map;

import org.hl7.fhir.r5.model.CanonicalResource;

public class NullModule implements IPublisherModule {
  
  public boolean preProcess(String path) {
    //nothing
    return true;
  }

  @Override
  public String code() {
    return null;
  }

  @Override
  public String name() {
    return "Null Module";
  }

  @Override
  public boolean useRoutine(String name) {
    return false;
  }

  @Override
  public void defineTypeMap(Map<String, String> typeMap) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean resolve(String ref) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public CanonicalResource fetchCanonicalResource(String url) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean approveFragment(boolean value, String code) {
    return value;
  }

  @Override
  public boolean isNoNarrative() {
    return false;
  }

}
