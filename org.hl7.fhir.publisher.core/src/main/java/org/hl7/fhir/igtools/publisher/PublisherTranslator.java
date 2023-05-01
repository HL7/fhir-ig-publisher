package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.LanguageUtils;
import org.hl7.fhir.r5.model.Resource;
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

  public PublisherTranslator(SimpleWorkerContext context, String baseLang, String defaultTranslationLang, List<String> translationLangs) {
    super();
    this.context = context;
    this.baseLang = baseLang;
    this.translationLangs.addAll(translationLangs);
    if (!this.translationLangs.contains(defaultTranslationLang)) {
      this.translationLangs.add(defaultTranslationLang);
    }
  }

  public void start(String dst) throws IOException {
    Utilities.createDirectory(dst);
    Utilities.createDirectory(Utilities.path(dst, "po"));
    Utilities.createDirectory(Utilities.path(dst, "xliff"));
    Utilities.createDirectory(Utilities.path(dst, "json"));

    po = new PoGetTextProducer(Utilities.path(dst, "po"));
    xliff = new XLIFFProducer(Utilities.path(dst, "xliff"));
    json = new JsonLangFileProducer(Utilities.path(dst, "json"));
  }

  public void translate(FetchedFile f, FetchedResource r) throws IOException {
    for (String lang : translationLangs) {
      genTranslations(f, r, lang, translationLangs.size() > 0); 
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
    if (res != null && LanguageUtils.handlesAsResource(res)) {

      List<TranslationUnit> translations = LanguageUtils.generateTranslations(res, lang);
      String srcFile = res.getUserString("source.filename");
      
      po.produce(srcFile, baseLang, lang, translations, srcFile+".po");
      xliff.produce(srcFile, baseLang, lang, translations, srcFile+".xliff");
      json.produce(srcFile, baseLang, lang, translations, srcFile+".json");
    } else if (LanguageUtils.handlesAsElement(r.getElement())) {
      
    }
  }
  
}
