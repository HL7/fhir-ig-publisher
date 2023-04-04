package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.util.List;

import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.LangaugeUtils;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.LanguageFileProducer;
import org.hl7.fhir.utilities.i18n.LanguageFileProducer.LanguageProducerLanguageSession;
import org.hl7.fhir.utilities.i18n.LanguageFileProducer.LanguageProducerSession;
import org.hl7.fhir.utilities.i18n.LanguageFileProducer.TextUnit;
import org.hl7.fhir.utilities.i18n.PoGetTextProducer;
import org.hl7.fhir.utilities.i18n.XLIFFProducer;

public class PublisherTranslator {

  private SimpleWorkerContext context;
  private String destination;
  private XLIFFProducer xliff;
  private PoGetTextProducer po;
  private String defaultTranslationLang;
  private List<String> translationLangs;

  public PublisherTranslator(SimpleWorkerContext context, String defaultTranslationLang, List<String> translationLangs) {
    super();
    this.context = context;
    this.defaultTranslationLang = defaultTranslationLang;
    this.translationLangs = translationLangs;
  }

  public void start(String dst) throws IOException {
    this.destination = dst;
    Utilities.createDirectory(dst);

    Utilities.createDirectory(Utilities.path(dst, "po"));
    po = new PoGetTextProducer(Utilities.path(dst, "po"));

    Utilities.createDirectory(Utilities.path(dst, "xliff"));
    xliff = new XLIFFProducer(Utilities.path(dst, "xliff"));

  }

  public void translate(FetchedFile f, FetchedResource r) throws IOException {
    translate(f, r, po);
    translate(f, r, xliff);
  }

  private void translate(FetchedFile f, FetchedResource r, LanguageFileProducer lp) throws IOException {
    LanguageProducerSession session = lp.startSession(r.fhirType()+"-"+r.getId(), defaultTranslationLang);
    for (String lang : translationLangs) {
      LanguageProducerLanguageSession langSession = session.forLang(lang);
      LangaugeUtils utils = new LangaugeUtils(context);
      utils.generateTranslations(r.getElement(), langSession);
      langSession.finish();
    }
    session.finish();    
  }

  

  public void finish() throws IOException {
    po.finish();
    xliff.finish();

  }

}
