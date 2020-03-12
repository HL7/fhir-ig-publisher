package org.hl7.fhir.igtools.publisher;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.Enumeration;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.r5.utils.TranslatingUtilities.TranslationServices;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class TranslationImplementation implements TranslationServices {

  // marker is used to see what content is not being translated (debugging)
  private static final String MARKER1 = ""; // "^";
  private static final String MARKER2 = ""; // "^^";
  private Map<String, String> keys = new HashMap<>();
  // -- configuration -------------------------------------------------------
  
  private String lang; 
  private String nonAlpha = "[^a-zA-Z0-9_]+";
  private String noEndDash = "_$";
  private Locale locale = Locale.getDefault();

  private ResourceBundle bundle = ResourceBundle.getBundle("Messages");
  private boolean harvest = false;

  public String getLang() {
    return lang;
  }

  public void setLang(String lang) {
    if("harvest".equals(lang)) {
      this.harvest = true;
    } else {
      this.lang = lang;
      this.locale = Locale.forLanguageTag(lang);
      bundle = ResourceBundle.getBundle("Messages", this.locale);
    }
  }

  // -- services -------------------------------------------------------

  public String translate(String context, String string) {
    if(string == null) return null;
    String key = generateKey(context, string);
    return harvest ? string : resolve(key);
  }

  public String translate(String context, String string, Object... args) {
    if(string == null) return null;
    String key = generateKey(context, string);
    return String.format(harvest ? string : resolve(key), args);
  }

  public String gt(@SuppressWarnings("rawtypes") PrimitiveType md) {
    String sd = md.asStringValue();
    return sd == null ? null : MARKER1+sd+MARKER1;
  }

  public String toStr(int v) {
    return Integer.toString(v);
  }

  public String toStr(Date date) {
    return date.toString();
  }

  public String egt(@SuppressWarnings("rawtypes") Enumeration<? extends Enum> enumeration) {
    return MARKER1+enumeration.primitiveValue()+MARKER1;
  }

  @Override
  public String translate(String context, String value, String targetLang) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String toStr(float value) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String translateAndFormat(String contest, String lang, String string2, Object... args) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Map<String, String> translations(String value) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Set<String> listTranslations(String category) {
    // TODO Auto-generated method stub
    return null;
  }

  public void printKeys(){
    if(harvest) {
      System.out.println("Printing bundle keys:");
      keys.forEach((key, value) -> System.out.println(String.format("%s=%s", key, value)));
    } else {
      System.out.println("Not harvesting bundle keys. Nothing printed");
    }
  }

  private String generateKey(String context, String path) {
    String prefix = StringUtils.isNotBlank(context) ? context + "." : "";
    String key = prefix + path.toLowerCase()
            .replaceAll(nonAlpha, "_")
            .replaceAll(noEndDash, "");

    if(harvest) {
      if(keys.containsKey(key) && !path.equalsIgnoreCase(keys.get(key))) {
        System.out.println(String.format("Overwriting key [%s]. Old value [%s]. New value [%s].", key, keys.get(key), path));
      }
      keys.put(key, path);
    } else {
      if(!bundle.containsKey(key)) {
        System.out.println(String.format("Message key not found : [%s]", key));
      }
    }

    return key;
  }

  // TODO: Handle by custom ResourceBundle.Control instead. This is just a workaround
  // Should language properties be partitioned into bundles by context instead of current prefixing?
  private String resolve(String key) {
    return new String(bundle.getString(key).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
  }
}
