package org.hl7.fhir.igtools.renderers.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.utils.ResourceSorters.CanonicalResourceSortByUrl;

public class ObligationsAnalysis {

  public class ActorInfo {
    Set<String> allObligations = new HashSet<>();
    Set<String> commonObligations = new HashSet<>();
    public Set<String> getAllObligations() {
      return allObligations;
    }
    public Set<String> getCommonObligations() {
      return commonObligations;
    }
    public String colspan() {
      return Integer.toString(hasOthers() ? commonObligations.size() + 1 : commonObligations.size());
    }
    public boolean hasOthers() {
      return commonObligations.size() < allObligations.size();
    }

  }

  public class ProfileActorObligationsAnalysis {
    Set<String> obligations = new HashSet<>();

    public Set<String> getObligations() {
      return obligations;
    }
  }

  public class ProfileObligationsAnalysis {
    private StructureDefinition profile;
    private Map<String, ProfileActorObligationsAnalysis> actors = new HashMap<>();
    public StructureDefinition getProfile() {
      return profile;
    }
    public Map<String, ProfileActorObligationsAnalysis> getActors() {
      return actors;
    }
    public boolean hasNullActor() {
      return actors.containsKey(null);
    }
    public ProfileActorObligationsAnalysis actor(String actor) {
      return actors.get(actor);
    }
  }
  private Map<String, ActorInfo> actors = new HashMap<>();
  private List<ProfileObligationsAnalysis> profiles = new ArrayList<>();


  public Map<String, ActorInfo> getActors() {
    return actors;
  }



  public List<ProfileObligationsAnalysis> getProfiles() {
    return profiles;
  }



  public static ObligationsAnalysis build(List<StructureDefinition> profiles) {
    Collections.sort(profiles, new CanonicalResourceSortByUrl());
    ObligationsAnalysis self = new ObligationsAnalysis();

    for (StructureDefinition sd : profiles) {
      for (Extension ob : sd.getExtensionsByUrl(ExtensionDefinitions.EXT_OBLIGATION_CORE)) {
        seeObligation(self, sd, null, ob);
      }
      for (ElementDefinition ed : sd.getSnapshot().getElement()) {
        for (Extension ob : ed.getExtensionsByUrl(ExtensionDefinitions.EXT_OBLIGATION_CORE)) {
          seeObligation(self, sd, ed, ob);
        }
        for (TypeRefComponent tr : ed.getType()) {
          for (Extension ob : tr.getExtensionsByUrl(ExtensionDefinitions.EXT_OBLIGATION_CORE)) {
            seeObligation(self, sd, ed, ob);
          }
        }
      }
    }
    
    self.buildCommonObligations();
    return self;
  }



  private void buildCommonObligations() {
    for (String actor : actors.keySet()) {
      ActorInfo ai = actors.get(actor);
      for (ProfileObligationsAnalysis p : profiles) {
        ProfileActorObligationsAnalysis pa = p.actor(actor);
        if (pa != null) {
          ai.allObligations.addAll(pa.obligations);
        }
      }
      for (String code : ai.allObligations) {
        int c = 0;
        for (ProfileObligationsAnalysis p : profiles) {
          ProfileActorObligationsAnalysis pa = p.actor(actor);
          if (pa != null) {
            if (pa.obligations.contains(code)) {
              c++;
            }
          }
        }
        if (c > profiles.size() / 3) {
          ai.commonObligations.add(code);
        }
      }
    }
    
  }



  private static void seeObligation(ObligationsAnalysis self, StructureDefinition sd, ElementDefinition ed, Extension ob) {
    Set<String> actors = new HashSet<>();
    for (Extension actor : ob.getExtensionsByUrl("actor")) {
      String s = actor.getValue().primitiveValue();
      if (s != null) {
        actors.add(s);
      }
    }
    Set<String> codes = new HashSet<>();
    for (Extension code : ob.getExtensionsByUrl("code")) {
      String s = code.getValue().primitiveValue();
      if (s != null) {
        codes.add(s);
      }
    }
    if (actors.isEmpty()) {
      self.seeActor(null);
    } else {
      for (String actor : actors) {
        self.seeActor(actor);
      }
    }
    ProfileObligationsAnalysis p = self.getProfile(sd);
    if (p == null) {
      p = self.new ProfileObligationsAnalysis();
      p.profile = sd;
      self.profiles.add(p);
    }
    
    if (actors.isEmpty()) {
      actorMap(self, p, null).obligations.addAll(codes);
    }
    for (String actor : actors) {
      actorMap(self, p, actor).obligations.addAll(codes);      
    }
  }

  private void seeActor(String actor) {
    if (!actors.containsKey(actor)) {
      actors.put(actor,  new ActorInfo());
    }
  }



  private static ProfileActorObligationsAnalysis actorMap(ObligationsAnalysis self, ProfileObligationsAnalysis p, String actor) {
    ProfileActorObligationsAnalysis res = p.actors.get(actor);
    if (res == null) {
      res = self.new ProfileActorObligationsAnalysis();
      p.actors.put(actor, res);
    }
    return res;
  }



  public boolean hasNullActor() {
    return actors.containsKey(null);
  }



  public ProfileObligationsAnalysis getProfile(StructureDefinition sd) {
    for (ProfileObligationsAnalysis p : profiles) {
      if (p.profile == sd) {
        return p;
      }
    }
    return null;
  } 
}
