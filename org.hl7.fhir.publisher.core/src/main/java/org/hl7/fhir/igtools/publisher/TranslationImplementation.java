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

    private final Map<String, String> keys = new HashMap<>();
    // Setting the Implementation Guide Parameter language to "harvest" results in all strings passed to
    // TranslationImplementation during IG-creation being harvested and printed to console.
    public static final String HARVEST_LANGUAGE_PROPERTIES = "harvest";

    @SuppressWarnings("FieldCanBeLocal")
    private final String PATTERN_NON_ALPHANUMERIC = "[^a-zA-Z0-9_]+";
    @SuppressWarnings("FieldCanBeLocal")
    private final String PATTERN_END_DASH = "_$";
    private final String LANGUAGE_RESOURCE_BUNDLE_NAME = "Messages";

    private ResourceBundle bundle = ResourceBundle.getBundle(LANGUAGE_RESOURCE_BUNDLE_NAME);

    // -- configuration -------------------------------------------------------
    private String lang;

    public String getLang() {
        return lang;
    }

    /**
     Sets the current language of this TranslationImplementation class, loading the most fitting language resource bundle.

     @param lang Language tag compliant to IETF BCP 47
     */
    public void setLang(String lang) {
        this.lang = lang;
        if (!HARVEST_LANGUAGE_PROPERTIES.equals(lang)) {
            bundle = ResourceBundle.getBundle(LANGUAGE_RESOURCE_BUNDLE_NAME, Locale.forLanguageTag(lang));
        }
    }

    // -- services -------------------------------------------------------

    /**
     Returns a localized version of a given string for a context.

     @param context Context in which string is used.
     @param string  String to be retrieved.
     @return A localized version of the string or the string passed in if a corresponding key does not exist in
     language resource bundle
     */
    public String translate(String context, String string) {
        if (string == null) return null;
        String key = generateKey(context, string);
        return harvestEnabled() ? string : resolve(key).orElse(string);
    }

    /**
     Returns a formatted localized version of a given string for a context.
     Returns a formatted string using the localized version of format string and arguments.

     @param context Context in which string is used.
     @param string  String to be retrieved.
     @param args    Arguments referenced by the format specifiers in the localized format string.
     @return A formatted, localized, version of the string, or the string passed in if a corresponding key does not
     exist in language resource bundle
     */
    @SuppressWarnings("unused")
    public String translate(String context, String string, Object... args) {
        if (string == null) return null;
        String key = generateKey(context, string);
        return String.format(harvestEnabled() ? string : resolve(key).orElse(string), args);
    }

    public String gt(PrimitiveType md) {
        String sd = md.asStringValue();
        return sd == null ? null : MARKER1 + sd + MARKER1;
    }

    @SuppressWarnings("unused")
    public String toStr(int v) {
        return Integer.toString(v);
    }

    public String toStr(Date date) {
        return date.toString();
    }

    public String egt(Enumeration<? extends Enum> enumeration) {
        return MARKER1 + enumeration.primitiveValue() + MARKER1;
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

    /**
     Prints a list of all strings that have been passed to TranslationImplementation and their respective generated keys
     in the format: generated_key=string
     This list is printed to System.out
     For details on key generation see {@link #generateKey(String, String)} ()}
     */
    public void printKeys() {
        System.out.println("Printing bundle keys:");
        keys.forEach((key, value) -> System.out.println(String.format("%s=%s", key, value)));
    }

    /**
     Returns harvesting state

     @return True if harvest is enabled
     */
    public boolean harvestEnabled() {
        return HARVEST_LANGUAGE_PROPERTIES.equals(getLang());
    }

    /**
     Generates a property file key from a string by the following rules:
     All non-alphanumeric characters in string are replaced with underscore.
     Multiple occurrences of non-alphanumeric characters are replaced with a single underscore.
     Possible trailing underscore is removed.
     If context has content it is prefixed to the string, separated with a period.

     If, during harvesting of strings, a key already exist and does not contain the same value as has been passed to the
     method, a note is written to System.out.

     @param context Context in which string is used
     @param string  String to be converted to a key
     @return The generated key
     */
    private String generateKey(String context, String string) {
        String prefix = StringUtils.isNotBlank(context) ? context + "." : "";
        String key = prefix + string.toLowerCase()
                .replaceAll(PATTERN_NON_ALPHANUMERIC, "_")
                .replaceAll(PATTERN_END_DASH, "");

        if (harvestEnabled()) {
            if (keys.containsKey(key) && !string.equalsIgnoreCase(keys.get(key))) {
                System.out.println(String.format(
                        "Overwriting key [%s]. Old value [%s]. New value [%s].",
                        key,
                        keys.get(key),
                        string)
                );
            }
            keys.put(key, string);
        }

        return key;
    }

    /**
     Retrieve a localized string from an UTF8 encoded property file.

     @param key Key to retrieve from bundle
     @return Localized string
     */
    private Optional<String> resolve(String key) {
        return bundle.containsKey(key)
                ? Optional.of(new String(bundle.getString(key).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8))
                : Optional.empty();

    }
}
