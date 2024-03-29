package org.hl7.fhir.igtools.publisher.modules;

import java.util.Map;

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

}
