package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.LanguageUtils;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.JsonLangFileProducer;
import org.hl7.fhir.utilities.i18n.LanguageFileProducer.TranslationUnit;
import org.hl7.fhir.utilities.i18n.PoGetTextProducer;
import org.hl7.fhir.utilities.i18n.XLIFFProducer;

public class PublisherTranslator {

  private SimpleWorkerContext context;
  private XLIFFProducer xliff;
  private PoGetTextProducer po;
  private JsonLangFileProducer json;
  private List<String> translationLangs = new ArrayList<>();
  private String baseLang;
  private LanguageUtils lu;

  public PublisherTranslator(SimpleWorkerContext context, String baseLang, String defaultTranslationLang, List<String> translationLangs) {
    super();
    this.context = context;
    this.lu = new LanguageUtils(context);
    this.baseLang = baseLang;
    this.translationLangs.addAll(translationLangs);
    if (!this.translationLangs.contains(defaultTranslationLang)) {
      this.translationLangs.add(defaultTranslationLang);
    }
  }

  public void start(String dst) throws IOException {
    FileUtilities.createDirectory(dst);
    
    if (translationLangs.size() > 1) {
      for (String l : translationLangs) {
        if (baseLang == null || !baseLang.equals(l)) {
          FileUtilities.createDirectory(Utilities.path(dst, l, "po"));
          FileUtilities.createDirectory(Utilities.path(dst, l, "xliff"));
          FileUtilities.createDirectory(Utilities.path(dst, l, "json"));
          
        }
      }
    } else {
      FileUtilities.createDirectory(Utilities.path(dst, "po"));
      FileUtilities.createDirectory(Utilities.path(dst, "xliff"));
      FileUtilities.createDirectory(Utilities.path(dst, "json"));
    }

    po = new PoGetTextProducer(dst, "po", translationLangs.size() > 1);
    xliff = new XLIFFProducer(dst, "xliff", translationLangs.size() > 1);
    json = new JsonLangFileProducer(dst, "json", translationLangs.size() > 1);
  }

  public void translate(FetchedFile f, FetchedResource r) throws IOException {
    for (String lang : translationLangs) {
      if (baseLang == null || !baseLang.equals(lang)) {
        genTranslations(f, r, lang, translationLangs.size() > 0);
      }
    }
  }
//
//  private void translate(FetchedFile f, FetchedResource r, LanguageFileProducer lp) throws IOException {
//    LanguageProducerSession session = lp.startSession(r.fhirType()+"-"+r.getId(), defaultTranslationLang);
//    for (String lang : translationLangs) {
//      LanguageProducerLanguageSession langSession = session.forLang(lang);
//      LanguageUtils utils = new LanguageUtils(context);
//      utils.generateTranslations(r.getElement(), langSession);
//      langSession.finish();
//    }
//    session.finish();    
//  }

  public void finish() throws IOException {
  }
  
  private void genTranslations(FetchedFile f, FetchedResource r, String lang, boolean langInId) throws IOException {
    Resource res = r.getResource();
    if (res != null && lu.handlesAsResource(res)) {

      List<TranslationUnit> translations = lu.generateTranslations(res, lang);
      String srcFile = res.hasUserData(UserDataNames.pub_source_filename) ? res.getUserString(UserDataNames.pub_source_filename) : r.fhirType()+"-"+r.getId();
      
      po.produce(srcFile, baseLang, lang, translations, srcFile+".po");
      xliff.produce(srcFile, baseLang, lang, translations, srcFile+".xliff");
      json.produce(srcFile, baseLang, lang, translations, srcFile+".json");
      
    } else if (lu.handlesAsElement(r.getElement())) {
      
      List<TranslationUnit> translations = lu.generateTranslations(r.getElement(), lang);
      String srcFile = r.fhirType()+"-"+r.getId();
      
      po.produce(srcFile, baseLang, lang, translations, srcFile+".po");
      xliff.produce(srcFile, baseLang, lang, translations, srcFile+".xliff");
      json.produce(srcFile, baseLang, lang, translations, srcFile+".json");
    }
  }

  public LanguageUtils getLu() {
    return lu;
  }
  
}
