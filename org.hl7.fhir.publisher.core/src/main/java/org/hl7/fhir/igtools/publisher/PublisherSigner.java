package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.Base64URL;

public class PublisherSigner {

  // this is the default key, 
  // when the ig is built for release, either on the ci-build or -go-publish, an actual 
  // private key will be used that overrrides this one
  
  // alternatively, an implementer can provide their own in /input/jwk.json
  private static final String DEF_JWKS_JSON = "{ \n"
      + "  \"jwk\" : {\n"
      + "    \"p\": \"93dPpZDAt7KN7OdwForp4Xr9q3FU23gUO9Ate_uoc7liaB7CNxKZVE5ZKJ33p_bczIAKzeuQjxSCgR8LXFT6uvHUuCH8oxBXC182EWlWljhbselqCGN7YQShSM7suSeVzif6CXUeafsR-XcBERxHHy5dSoh5zYq7JRdARzZCg78\",\n"
      + "    \"kty\": \"RSA\",\n"
      + "    \"q\": \"ku2zF8DQrfiXBuzlk0KC_xJGDKQfb07VEidzFYE1C-uBRqQytTlT6BGTF90UDcsZ_CjD3x6dp78Ec13GbF_WVzzZNrjIMsVSEC5dPsXdheLqdKLt8fRG35-hLl3DwYu6d55vVhDbP-QAfRE60bX0yScMkBjP20O_0mpUJjCDoas\",\n"
      + "    \"d\": \"DHy_h_C-zhPhPg7dvUJJc3HvnLtuQxCD_215WqJBrOZ6cbZX1Cwd6cp2QVEK7p5U4jbHdZpsKuRLQKDlpkSqjxNiVRmP8_5MHzs0oi7m40OKdOQ8HTw39lhTxzJx9XT2VWEdXwYwyNM9tGYcnml61fiBXHqXt-DSXpNkGrf1fht2fQacTF7oFG28VVWrGbAiitcir5JdAUW3MpZjSgWra3P3H7Dku_AjO_XfT5WGGpiUeXUEqLQxWjVSxZSonKgRmuKE3snaDUNJzd66k0Cvjm4irFtb0owJC0LZf9nVzkF-uqWeEIBzDXGDK604vLhWj6cCMYRvvgQnw5hlG6PSWQ\",\n"
      + "    \"e\": \"AQAB\",\n"
      + "    \"use\": \"sig\",\n"
      + "    \"kid\": \"K_E0WTm6hVrXvYFrK4hJjF0etvhRsh0LAPUFsS6gzKg\",\n"
      + "    \"qi\": \"KZXSpxETiz4ILyxzwI_pMIm1E8P0cMaMuDjoTy2IvClgdkCBBcFZ0W37hOMXaoIM5Hy72uYydV1lljYGMwc8_VQMZLh_22xSSFJnV8PPjTzW5do4ryfizJUg52AsPBhNn_i6pa2L5jkmb98muD2HbDHXOFCEV57YuEEiSWRVCJA\",\n"
      + "    \"dp\": \"xTFWz0jkuLzYqWHXCK-TJTD7eKUriGNMRElkJTrpBaZBC1UPUBFLC0oPc_VExpxJX8_cTDCdFdazE68oP2AcF-HirwOuLEY2BoLNM9yrubKZJtEnxB150Fp_JuR08CniDs_-R5EDNlJyBUbWG8tbxTYN8vmDjc0xyaGYf-Z15EM\",\n"
      + "    \"alg\": \"RS256\",\n"
      + "    \"dq\": \"Dtrdeo9SCeTSUC7vXx4gZG2Si4CkdPqBbF50sj3oARaEcYH0ZoIvS41LU-RUPLjGHcp5UzujMOyNJKTchOSDpTpPs8qm4ws0KtKlNs2GghzZG4XFjOrnp4BaKXftbMoVxjZMh2UY5bLFod92FPHSl-vMx1za1w5YfIunilzpUhU\",\n"
      + "    \"n\": \"jgfSAIuhDB14fR3vp9qfT4AuJaIfCJCehSy69kwTguUMARntGJx7mPD8K1Te10Uywx4E_je0LHMT2zgrrlp-hbqTWR6z6vOnYzl8MkXj1uC1pSEC0eXAUFcs2igJl6H6mEc1aOv_mdB8B6I2DIiKmBh-wVpgnA8HK7UfdercdbJOQmem-FG0uP98_CSiCw8NpWtq5ci7nEqf_ylneeL7pqOkQtL_huDZCShgW0WK7vTgYtp_zHZH0OQ17WCWYdqAaJ7Q964JPnvWYlNEMA8uSH0z5hd5lVTC4FKr5gSGFAyEg99RwXGw4usJjmHvhnxi9RJ_7elN7MbAwi_ITgYflQ\"\n"
      + "  },\n"
      + "  \"claims\" : {\n"
      + "    \"iss\" : \"http://hl7.org/fhir/tools/Device/ig-publisher\",\n"
      + "    \"sub\" : \"IG Publisher\"\n"
      + "  }\n"
      + "}";

  
  private String jwk;
  private JsonObject claims;


  private SimpleWorkerContext context;
  public PublisherSigner(SimpleWorkerContext context, String rootDir) throws IOException {
    this.context = context;
    setJson(JsonParser.parseObject(DEF_JWKS_JSON));
    File f = new File(Utilities.path(rootDir, "input", "signing-key.json"));
    if (f.exists()) {
      setJson(JsonParser.parseObject(f));
    }
  }

  public void setJwk(String jwk) throws JsonException, IOException {
    setJson(JsonParser.parseObject(jwk));
  }

  private void setJson(JsonObject json) {
    jwk = JsonParser.compose(json.getJsonObject("jwk"));
    claims = json.getJsonObject("claims");
  }

  public void signBundle(Element bnd, Element sig) throws FHIRException, IOException, JOSEException, ParseException {
    Instant instant = Instant.now();
    sig.setChildValue("targetFormat", "application/fhir+json+canonicalization=http://hl7.org/fhir/canonicalization/json");
    sig.setChildValue("sigFormat", "application/jose");
    sig.setChildValue("when", DateTimeFormatter.ISO_INSTANT.format(instant));
    Element who = sig.getNamedChild("who");
    if (who != null) {
      sig.removeChild(who);
    }
    who = sig.addElement("who");
    who.setChildValue("reference", claims.asString("iss"));
    who.setChildValue("display", claims.asString("sub"));
    
    Element data = sig.getNamedChild("data");
    sig.removeChild(data);
    
    ByteArrayOutputStream ba = new ByteArrayOutputStream();
    Manager.compose(context, bnd, ba, FhirFormat.JSON, OutputStyle.CANONICAL, null);
    byte[] toSign = ba.toByteArray();
 
    sig.setChildValue("data", createJWS(toSign, "http://hl7.org/fhir/canonicalization/json", instant));    
  }

  public String createJWS(byte[] canon, Object canonMethod, Instant instant) throws JOSEException, ParseException {
    JWK key = JWK.parse(jwk);
    
    JWSHeader.Builder builder = new JWSHeader.Builder(key.getKeyType().toString().equals("EC") ? JWSAlgorithm.ES256 : JWSAlgorithm.RS256).type(JOSEObjectType.JOSE);
    builder.customParam("iat", instant.getEpochSecond()).customParam("canon", canonMethod);
    for (String p : claims.getNames()) {
      builder.customParam(p, claims.asString(p));
    }    
    JWSHeader header = builder.build();

    // Create payload from bytes
    Payload payload = new Payload(Base64URL.encode(canon));

    // Create JWS object
    JWSObject jwsObject = new JWSObject(header, payload);

    
    // Create signer based on key type
    JWSSigner signer;
    if (key.getKeyType().toString().equals("RSA")) {
      signer = new RSASSASigner(key.toRSAKey());
    } else if (key.getKeyType().toString().equals("EC")) {
      signer = new ECDSASigner(key.toECKey());
    } else {
      throw new IllegalArgumentException("Unsupported key type: " + key.getKeyType());
    }

    // Sign the JWS
    jwsObject.sign(signer);

    // Return compact serialization
    return jwsObject.serialize();
  }

}
