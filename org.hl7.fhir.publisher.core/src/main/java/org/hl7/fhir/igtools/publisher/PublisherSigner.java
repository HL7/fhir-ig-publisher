package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.elementmodel.ParserBase;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.terminologies.utilities.ValidationResult;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.MimeType;
import org.hl7.fhir.utilities.OIDUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.hl7.fhir.validation.instance.utils.DigitalSignatureSupport;
import org.hl7.fhir.validation.instance.utils.DigitalSignatureSupport.SignedInfo;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.X509CertUtils;

public class PublisherSigner {

  public enum SignatureType { JOSE, DIGSIG };

  // this is the default key, 
  // when the ig is built for release, either on the ci-build or -go-publish, an actual 
  // private key will be used that overrrides this one

  private static final String DEF_X509_CERT = "-----BEGIN CERTIFICATE-----\n"
      + "MIIDozCCAoqgAwIBAgIBADANBgkqhkiG9w0BAQ0FADBrMQswCQYDVQQGEwJ1czER\n"
      + "MA8GA1UECAwITWlzc291cmkxDDAKBgNVBAoMA0hMNzEQMA4GA1UEAwwHaGw3Lm9y\n"
      + "ZzESMBAGA1UEBwwJQW5uIEFyYm9yMRUwEwYDVQQLDAxJRyBQdWJsaXNoZXIwHhcN\n"
      + "MjUwNjE5MDIzMDMzWhcNMjYwNjIwMDIzMDMzWjBrMQswCQYDVQQGEwJ1czERMA8G\n"
      + "A1UECAwITWlzc291cmkxDDAKBgNVBAoMA0hMNzEQMA4GA1UEAwwHaGw3Lm9yZzES\n"
      + "MBAGA1UEBwwJQW5uIEFyYm9yMRUwEwYDVQQLDAxJRyBQdWJsaXNoZXIwggEjMA0G\n"
      + "CSqGSIb3DQEBAQUAA4IBEAAwggELAoIBAgC9Us8v0UyOy+XWLRff29GHQa9axqtD\n"
      + "ao7azsWnF2/ABdg1g6dOF/0ZkrhLdqJISj8MlSP5VLou67iZIH0RxRMfSA0e/fi9\n"
      + "DE8QSzpIOlueeH2M8Q2VesKp3hIkp+xCaGPbc4L0kZVkE6EW+TUR7QTs1NkaxtwY\n"
      + "vW87gKzn6BL0Yx/5mu1UFWcJ/XtLHkiagtIbSiEXSdsjxviObJM2SaV3taCaayGK\n"
      + "VFpU6rPLD/VRart6ZP1CJQ2zlIskEEOnnUKEUuwcFpL7t5FXiHVOX0hZ5fsuGYt8\n"
      + "EuLwa7giEQvf/PaQbTrTVMOdKH/EGVJI81MfNgzEbrA/CvcG/lgG3DM+JwIDAQAB\n"
      + "o1AwTjAdBgNVHQ4EFgQUqBKo2iQ0R5r5GiNlh2sNCzEq9QIwHwYDVR0jBBgwFoAU\n"
      + "qBKo2iQ0R5r5GiNlh2sNCzEq9QIwDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQ0F\n"
      + "AAOCAQIAi8FVsqZJ4Ofiigjgp4+CeHDx6LJRuq6YoiNerxJ4l+ET4Bb7j8E/DDeE\n"
      + "febEQvwPrhlOyFOfyoszaBnF8Ep/Hk7Oj6cpLhoAHjeV3GUy+3xg3NB3DuE7Jhnx\n"
      + "99kwfRL9tOXJ5+Ll1AIhyT0JID8J6/99Q5VmwSCUeRfnhnnigZlwS4VhAoanhrqz\n"
      + "wXvpOco+HyhL0y3mmbBH/eKU+H5P+L2IFiSFL6XAL4PM3pYI/zj+NyhAXZZZwllR\n"
      + "joIKflGOO8HUc5iJiTHhYo0iKS1lPxCHoKyA+aVEaVE/goqBwoe3V9mxHAz4Uq8e\n"
      + "/aQ0OfciFmj55s6mx5eUdy5lQqfoekE=\n"
      + "-----END CERTIFICATE-----";

  private static final String DEF_X509_KEY = "-----BEGIN PRIVATE KEY-----\n"
      + "MIIEwQIBADANBgkqhkiG9w0BAQEFAASCBKswggSnAgEAAoIBAgC9Us8v0UyOy+XW\n"
      + "LRff29GHQa9axqtDao7azsWnF2/ABdg1g6dOF/0ZkrhLdqJISj8MlSP5VLou67iZ\n"
      + "IH0RxRMfSA0e/fi9DE8QSzpIOlueeH2M8Q2VesKp3hIkp+xCaGPbc4L0kZVkE6EW\n"
      + "+TUR7QTs1NkaxtwYvW87gKzn6BL0Yx/5mu1UFWcJ/XtLHkiagtIbSiEXSdsjxviO\n"
      + "bJM2SaV3taCaayGKVFpU6rPLD/VRart6ZP1CJQ2zlIskEEOnnUKEUuwcFpL7t5FX\n"
      + "iHVOX0hZ5fsuGYt8EuLwa7giEQvf/PaQbTrTVMOdKH/EGVJI81MfNgzEbrA/CvcG\n"
      + "/lgG3DM+JwIDAQABAoIBAQ3lKAOwbtgEKwg/IwNxFL3CmmYlMqiuB3ITvvn2hGMp\n"
      + "iqbS1NKsfA0GcbRILrzzhhEcWRmRmGCdOF00vzkwp6iiFyRxK3JkluDxRIPMlLDa\n"
      + "0wwnHQIdkm/5NoeuM27kTn/qyG++x6Iitq4C+FwqczQWoyCN+9VtAd7yIL6cj9eT\n"
      + "9N2qPIH4dAHfbp0gN1yTqTJi/IoIk2nAFs/6CBzjDS8jbr5qdEtX/VRVhVvb4p0k\n"
      + "ICle9wQ6VMCUM9tEJaPea7NqieJ+z7fFVKGMthFnIKqJ9VHpjpcRHgjH/6HqAvvM\n"
      + "pnRnWH3pIk9AXJSZ5s5dM4W4pLEXy3OX1Eizud7HSzdRAoGBDgmZRLl3hIJZXAO7\n"
      + "PN+wTT+J1qg3NcIXJcJvescMLTewWymkpfDISbhHlZYVtqPlkaz6pw3mpaRQRd/k\n"
      + "IY4gDmNwaxgmJ5LpGk6TGu2emkKr+95Cj2HaiYTRaOz3UVlpnTYvm7UTcNkfzXXe\n"
      + "mVXyfzJtl8SapVFaZpV5v2QobGfrAoGBDXyrANX2PD7c1NgbmGhhKs2wzpWL6o2s\n"
      + "0CHYQsAuxsvbO1YZr1e/wuPNtlmHQllIa5y1yRnhUkvjs62Ne2fvjGFFbbGew9WJ\n"
      + "kuBwGrfCziX9OYtLumSIw1ahodA1rQRpty5KImeCWXOGoTkgrMzlQ7/avlqZk1El\n"
      + "xED6gbkZpQ+1AoGBAIb/yQMmqEW1Ua2aNRk6KEzAwt2i5VQbRoHdakFcBb7X0zTn\n"
      + "SIyZGZvgpI/01N2nXCehavEsvwBDO7zDdzc9oQy/Rmar2ES+mQxmnlZa5PSoPVgG\n"
      + "LBjC+vOQYl60k8vGGe/VLgZJaq3dcfyBlkUSTRD56P+rx5YczUnEPxpmIlxtAoGB\n"
      + "DMvdX+SiRZULZ7NHs3pNvxP8DnYbk8cqUSvbibHYb+wZrRnLMu+Z1SrZEoutZwlZ\n"
      + "Sikc3Zp9i9zPRbqEQ7NguJvOCP7++SYQ6teh5ee2oGuw8Dk297nNfTEkGGh5lRhb\n"
      + "yV7VHgGBzqdq9GtEkk+xs29D91n03q6em69fP1fFejQFAoGBB/k1UBOMCjo+iNPH\n"
      + "bI+OzUqOy5NL7nd+SqB9FG53VE+w1b8XfCW3Cuf0Vm52AFudtXuAt5aEsezAJsfV\n"
      + "2XZfr+txSIgRbbFWQ39VccOFjckVzD2egXnFdL5Ac2s9JkAJZHqWU8pppJTVEuPF\n"
      + "KrYFFnz13zUOg9B7zxatELWn9rNV\n"
      + "-----END PRIVATE KEY-----";


  private IWorkerContext context;

  private X509Certificate certificate;
  private JWK jwk;

  private ValidationOptions validationOptions;

  public PublisherSigner(IWorkerContext context, String rootDir, ValidationOptions validationOptions) throws Exception {
    this.context = context;
    this.validationOptions = validationOptions;

    String pem = DEF_X509_CERT;
    String key = DEF_X509_KEY;

    File fc = new File(Utilities.path(rootDir, "input", "signing-key.pem"));
    File fk = new File(Utilities.path(rootDir, "input", "signing-key.key"));
    if (fc.exists() && fk.exists()) {
      pem = FileUtilities.fileToString(fc);
      key = FileUtilities.fileToString(fk);
    } 
    certificate = loadCertificate(pem);
    PrivateKey privateKey = loadPrivateKey(key);
    jwk = createJWK(certificate, privateKey);

    System.out.println("Signing Certificate: " + jwk.getKeyType()+" key for Subject: " + certificate.getSubjectDN()+" from: " + certificate.getIssuerDN());
  }

  /**
   * Load X.509 Certificate from PEM string using Nimbus
   */
  public static X509Certificate loadCertificate(String pemCert) throws Exception {
    // Using Nimbus X509CertUtils
    X509Certificate cert = X509CertUtils.parse(pemCert);
    if (cert == null) {
      throw new Exception("Failed to parse certificate - invalid format");
    }
    return cert;
  }

  /**
   * Load Private Key from PEM string
   */
  public static PrivateKey loadPrivateKey(String pemKey) throws Exception {
    // Remove PEM headers and whitespace
    String keyData = pemKey
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        .replaceAll("\\s", "");

    // Decode Base64
    byte[] keyBytes = Base64.decodeBase64(keyData);

    // Create key spec
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

    // Try RSA first, then EC
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePrivate(spec);
    } catch (Exception e) {
      try {
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(spec);
      } catch (Exception e2) {
        throw new Exception("Unable to load private key as RSA or EC", e2);
      }
    }
  }

  /**
   * Create JWK from X.509 Certificate and Private Key
   */
  public static JWK createJWK(X509Certificate certificate, PrivateKey privateKey) throws Exception {
    if (privateKey instanceof RSAPrivateKey) {
      return createRSAJWK(certificate, (RSAPrivateKey) privateKey);
    } else if (privateKey instanceof ECPrivateKey) {
      return createECJWK(certificate, (ECPrivateKey) privateKey);
    } else {
      throw new Exception("Unsupported private key type: " + privateKey.getClass().getName());
    }
  }

  /**
   * Create RSA JWK
   */
  private static RSAKey createRSAJWK(X509Certificate certificate, RSAPrivateKey privateKey) throws Exception {
    RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();

    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .x509CertChain(Arrays.asList(com.nimbusds.jose.util.Base64.encode(certificate.getEncoded())))
        .keyID(generateKeyId(certificate))
        .build();
  }

  /**
   * Create EC JWK
   */
  private static ECKey createECJWK(X509Certificate certificate, ECPrivateKey privateKey) throws Exception {
    ECPublicKey publicKey = (ECPublicKey) certificate.getPublicKey();

    // Get the curve from the public key
    Curve curve = Curve.forECParameterSpec(publicKey.getParams());

    return new ECKey.Builder(curve, publicKey)
        .privateKey(privateKey)
        .x509CertChain(Arrays.asList(com.nimbusds.jose.util.Base64.encode(certificate.getEncoded())))
        .keyID(generateKeyId(certificate))
        .build();
  }

  /**
   * Generate a key ID from the certificate
   */
  private static String generateKeyId(X509Certificate certificate) throws Exception {
    // Simple approach: use part of the serial number
    if (certificate.getSerialNumber().toString(16).length() <= 1) {
      return null;
    } else {
      return certificate.getSerialNumber().toString(16);
    }
  }

  /**
   * Alternative method: Create JWK directly from PEM strings
   */
  public static JWK createJWKFromPEM(String pemCert, String pemKey) throws Exception {
    X509Certificate certificate = loadCertificate(pemCert);
    PrivateKey privateKey = loadPrivateKey(pemKey);
    return createJWK(certificate, privateKey);
  }

  /**
   * Utility method to validate the loaded certificate and key match
   */
  public static boolean validateKeyPair(X509Certificate certificate, PrivateKey privateKey) throws Exception {
    // Simple validation: check if the public key from cert matches the private key
    String certPublicKeyAlgorithm = certificate.getPublicKey().getAlgorithm();
    String privateKeyAlgorithm = privateKey.getAlgorithm();

    return certPublicKeyAlgorithm.equals(privateKeyAlgorithm);
  }


  public Instant roundToNearestSecond(Instant instant) {
    // Convert to millis, round to nearest 1000ms, back to Instant
    long epochMillis = instant.toEpochMilli();
    long roundedMillis = Math.round(epochMillis / 1000.0) * 1000;
    return Instant.ofEpochMilli(roundedMillis);
  }

  public void signBundle(Element bnd, Element sig, SignatureType sigType) throws Exception {
    boolean xml = false;
    String canon = null;
    if (sig.hasChild("targetFormat")) { 
      MimeType mt = new MimeType(sig.getNamedChildValue("targetFormat"));       
      xml = mt.getBase().contains("xml");
      canon = mt.getParams().get("canonicalization");
    }
    if (canon == null) {
      canon = xml ? "http://hl7.org/fhir/canonicalization/xml" : "http://hl7.org/fhir/canonicalization/json";
      if ("document".equals(bnd.getNamedChildValue("type"))) {
        canon += "#document";
      }
    }
    Instant instant = roundToNearestSecond(Instant.now());
    if (xml) {
      sig.setChildValue("targetFormat", "application/fhir+xml;canonicalization="+canon);
    } else {
      sig.setChildValue("targetFormat", "application/fhir+json;canonicalization="+canon);      
    }
    String when = DateTimeFormatter.ISO_INSTANT.format(instant);
    sig.setChildValue("when", when);
    Element who = sig.getNamedChild("who");
    if (who != null) {
      sig.removeChild(who);
    }
    who = sig.addElement("who");
    Element id = who.addElement("identifier");
    id.setChildValue("system", "http://example.org/certificates");
    id.setChildValue("value", certificate.getSubjectX500Principal().getName());

    String purpose = null;
    String purposeDesc = null;
    List<Element> types = sig.getChildren("type");
    if (!types.isEmpty()) {
      Element type = types.get(0);
      String system = type.getNamedChildValue("system");
      String code = type.getNamedChildValue("code"); 
      if (OIDUtilities.isValidOID(code)) {
        purpose = "urn:oid:"+code;
      } else {
        purpose = system+"#"+code;
      }
      purposeDesc = type.getNamedChildValue("display");
      if (purposeDesc == null) {
        ValidationResult vr = context.validateCode(validationOptions, system, null, code, null);
        if (vr.isOk()) {
          purposeDesc = vr.getDisplay();
        }
      }
    }
    
    

    ByteArrayOutputStream ba = new ByteArrayOutputStream();
    ParserBase p = Manager.makeParser(context, xml ? FhirFormat.XML : FhirFormat.JSON);

    if (canon.endsWith("#document")) {
      p.setCanonicalFilter("Bundle.id", "Bundle.meta", "Bundle.signature");
    } else {
      p.setCanonicalFilter("Bundle.signature");
    }
    p.compose(bnd, ba, OutputStyle.CANONICAL, null);
    byte[] toSign = ba.toByteArray();

    if (sigType == SignatureType.JOSE) {
      sig.setChildValue("sigFormat", "application/jose");
      sig.setChildValue("data", Base64.encodeBase64String(removePayload(createJWS(toSign, canon, when, purpose, purposeDesc)).getBytes(StandardCharsets.US_ASCII)));
    } else {
      sig.setChildValue("sigFormat", "application/pkcs7-signature");
      sig.setChildValue("data", Base64.encodeBase64String(generateXMLDetachedSignature(toSign, instant, canon, purpose, purposeDesc)));
    }
  }

  private String removePayload(String jws) {
    String[] parts = jws.split("\\.");
    return parts[0]+".."+parts[2];
  }

  private JWK parseX509c(X509Certificate certificate) throws CertificateException {
    // Extract the public key
    PublicKey publicKey = certificate.getPublicKey();

    // Convert to JWK based on key type
    JWK jwk;
    if (publicKey instanceof RSAPublicKey) {
      jwk = new RSAKey.Builder((RSAPublicKey) publicKey).build();
    } else if (publicKey instanceof ECPublicKey) {
      jwk = new ECKey.Builder(Curve.forECParameterSpec(
          ((ECPublicKey) publicKey).getParams()), (ECPublicKey) publicKey)
          .build();
    } else {
      throw new IllegalArgumentException("Unsupported key type: " + publicKey.getAlgorithm());
    }
    return jwk;
  }

  public String createJWS(byte[] canon, Object canonMethod, String when, String purpose, String purposeDesc) throws JOSEException, ParseException, CertificateException {

    JWSHeader.Builder builder = new JWSHeader.Builder(jwk.getKeyType().toString().equals("EC") ? JWSAlgorithm.ES256 : JWSAlgorithm.RS256).type(JOSEObjectType.JOSE);
    builder.keyID(jwk.getKeyID());
    builder.customParam(DigitalSignatureSupport.JWT_HEADER_SIGT, when).customParam("canon", canonMethod);
    if (purpose != null) {
      builder.customParam(DigitalSignatureSupport.JWT_HEADER_SRCMS, makeSrCMS(purpose, purposeDesc));
    }
 // Add the certificate chain
    try {
        List<com.nimbusds.jose.util.Base64> certChain = new ArrayList<>();
        certChain.add(com.nimbusds.jose.util.Base64.encode(certificate.getEncoded()));
        builder.x509CertChain(certChain);
    } catch (CertificateEncodingException e) {
        throw new RuntimeException("Failed to encode certificate", e);
    }

    JWSHeader header = builder.build();

    // Create payload from bytes
    Payload payload = new Payload(Base64URL.encode(canon));

    // Create JWS object
    JWSObject jwsObject = new JWSObject(header, payload);


    // Create signer based on key type
    JWSSigner signer;
    if (jwk.getKeyType().toString().equals("RSA")) {
      signer = new RSASSASigner(jwk.toRSAKey());
    } else if (jwk.getKeyType().toString().equals("EC")) {
      signer = new ECDSASigner(jwk.toECKey());
    } else {
      throw new IllegalArgumentException("Unsupported key type: " + jwk.getKeyType());
    }

    // Sign the JWS
    jwsObject.sign(signer);

    // Return compact serialization
    return jwsObject.serialize();
  }


  private Object makeSrCMS(String purpose, String purposeDesc) {
    List<Object> list = new ArrayList<Object>();
    Map<String, Object> object = new HashMap<>();
    list.add(object);
    Map<String, Object> commId = new HashMap<>();
    object.put("commId", commId);
    commId.put("id", purpose);
    if (purposeDesc != null) {
      commId.put("desc", purposeDesc);
    }
    return list;
  }

  public byte[] generateXMLDetachedSignature(byte[] signThis, Instant instant, String canon, String purpose, String purposeDesc) throws Exception {

    SignedInfo signedInfo = DigitalSignatureSupport.buildSignInfo(certificate, signThis, canon, instant, "signing", purpose, purposeDesc);

    // Sign the SignedInfo with the private key
    PrivateKey privateKey = getPrivateKeyFromJWK(jwk);
    byte[] signatureValue = generateSignatureValue(signedInfo.getSignable(), privateKey);
    String signatureB64 = java.util.Base64.getEncoder().encodeToString(signatureValue);
    String certB64 = java.util.Base64.getEncoder().encodeToString(certificate.getEncoded());

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">\n");
    xml.append(signedInfo.getSource());
    xml.append("  <SignatureValue>").append(signatureB64).append("</SignatureValue>\n");
    xml.append("  <SignedBytes>").append(java.util.Base64.getEncoder().encodeToString(signThis)).append("</SignedBytes>\n");
    xml.append("  <KeyInfo>\n");
    xml.append("    <X509Data>\n");
    xml.append("      <X509Certificate>").append(certB64).append("</X509Certificate>\n");
    xml.append("    </X509Data>\n");
    xml.append("  </KeyInfo>\n");
    if (instant != null) {
      xml.append("  <Object>\n");
      xml.append("    <x:QualifyingProperties xmlns:x=\"http://uri.etsi.org/01903/v1.3.2#\" Target=\"#signature\">\n");
      // no whitespace in the XADES signed block
      xml.append("      <x:SignedProperties Id=\"SignedProperties\"><x:SignedSignatureProperties><x:SigningTime>"+DateTimeFormatter.ISO_INSTANT.format(instant)+
          "</x:SigningTime></x:SignedSignatureProperties>"+DigitalSignatureSupport.cmmId(purpose, purposeDesc)+"</x:SignedProperties>\n");
      xml.append("    </x:QualifyingProperties>");
      xml.append("  </Object>\n");
    }
    xml.append("</Signature>\n");
    
    return xml.toString().getBytes(StandardCharsets.US_ASCII);
  }

  // Helper method to generate signature value
  private static byte[] generateSignatureValue(byte[] data, PrivateKey privateKey) throws Exception {
    String signatureAlgorithm = privateKey.getAlgorithm().equals("RSA") ? 
        "SHA256withRSA" : "SHA256withECDSA";

    java.security.Signature signature = java.security.Signature.getInstance(signatureAlgorithm);
    signature.initSign(privateKey);
    signature.update(data);
    return signature.sign();
  }

  private static PrivateKey getPrivateKeyFromJWK(JWK jwk) throws Exception {
    if (jwk.getKeyType().toString().equals("RSA")) {
      return jwk.toRSAKey().toPrivateKey();
    } else if (jwk.getKeyType().toString().equals("EC")) {
      return jwk.toECKey().toPrivateKey();
    } else {
      throw new Exception("Unsupported key type: " + jwk.getKeyType());
    }
  }

}
