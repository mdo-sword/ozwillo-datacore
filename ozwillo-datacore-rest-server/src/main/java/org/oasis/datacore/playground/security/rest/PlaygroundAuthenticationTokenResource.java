package org.oasis.datacore.playground.security.rest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.binary.Base64;
import org.apache.cxf.jaxrs.client.WebClient;
import org.oasis.datacore.playground.security.TokenEncrypter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Resource that handles playground's Ozwillo OAuth2 token exchange.
 * 
 * Ozwillo OAuth2 is done as in    https://github.com/ozwillo/ozwillo-kernel/blob/master/HOWTO/login.md
 * * (see *Login*java) redirect browser to https://oasis-demo.atolcd.com/a/auth?response_type=code&client_id=29ef97c0-bb33-4f96-be22-3fd480e05d5f&scope=openid%20datacore&redirect_uri=https://portal.ozwillo.com/callback
 * + TODO LATER state and nonce randoms : provide and check at the end
 * * then handle browser redirect to https://portal.ozwillo.com/callback?code=eyJpZCI6IjA2MzBhNGRjLThhMWYtNDdkNi05NDY3LWU1N2NmNzM0ZDE3Yy9mUTNoY05nWmpvLWdhejFuUU9RME9nIiwiaWF0IjoxNDE1MjcyNDM3NzQ0LCJleHAiOjE0MTUyNzI0OTc3NDR9
 * to get token, by POST to  
 *    'Authorization:Basic ' + new Buffer(conf.app_client_id + ':' + conf.app_client_secret).toString("base64")
      grant_type: 'authorization_code',
      //redirect_uri: 'http://requestb.in/' + requestBinId // NO since Sept. 2014 has to be an approved app ex. portal
      redirect_uri: 'https://portal.ozwillo.com/callback',
      code: code
 * and JSON.parse(body).access_token
 * * put it in a cookie (LATER encrypted, using server-generated salt ?!?!) 
 * * then add a security filter that (LATER decrypts it and) checks it with kernel directly or by reusing spring flow or bean
 * 
 * TODO LATER2 extract to shared project...
 * 
 * @author mdutoo
 *
 */
@Path("dc/playground/token")
@Component("datacore.playground.tokenResource") // else can't autowire Qualified
public class PlaygroundAuthenticationTokenResource extends PlaygroundAuthenticationResourceBase {
   
   @Autowired
   private TokenEncrypter tokenEncrypter;

   /** to parse user & token info */
   public ObjectMapper jsonNodeMapper = new ObjectMapper();
   
   @PostConstruct
   protected void init() {
      cookieSecure = !devmode; // cookie secure requires HTTPS see https://www.owasp.org/index.php/SecureFlag
   }
   
   @GET
   @Path("")
   public void handleKernelCodeRedirectAndExchangeForToken(
         @QueryParam("code") @DefaultValue("") String code, @QueryParam("state") String state,
         @QueryParam("error") @DefaultValue("") String error,
         @QueryParam("error_description") @DefaultValue("") String errorDescription)
               throws BadRequestException, ClientErrorException, InternalServerErrorException {
      if (code == null || code.trim().length() == 0) {
         throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
               .entity("Kernel callback raises error : " + error + " (" + errorDescription + ")")
                     .type(MediaType.TEXT_PLAIN).build());
      }
      
      String clientIdColonSecret = datacoreOAuthClientId + ':' + datacoreOAuthClientSecret;
      String tokenExchangeBasicAuth = "Basic " + new String(Base64.encodeBase64(clientIdColonSecret.getBytes()));
      WebClient tokenExchangeClient = WebClient.create(accountsTokenEndpointUrl) // "http://requestb.in/saf6sosa"
            //.type(MediaType.APPLICATION_FORM_URLENCODED)
            .header(HttpHeaders.AUTHORIZATION, tokenExchangeBasicAuth);
      Response tokenExchangeRes;
      try {
         tokenExchangeRes = tokenExchangeClient.form(new Form()
               .param("grant_type", "authorization_code").param("code", code)
               .param("redirect_uri", playgroundTokenExchangeRedirectUrl));
      } catch (Exception ex) {
         // TODO or ServiceUnavailableException ?
         throw new InternalServerErrorException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
               .entity("Error calling Kernel to exchange token from code  : " + ex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      String tokenExchangeResBody = tokenExchangeRes.readEntity(String.class);
      if (Status.Family.SUCCESSFUL != tokenExchangeRes.getStatusInfo().getFamily()) {
         // ex. bad client_id/secret...
         throw new WebApplicationException(Response.status(tokenExchangeRes.getStatus())
               .entity(tokenExchangeRes.getStatus() + " kernel error exchanging code for access_token ("
                     + tokenExchangeResBody + ")")
                     .type(MediaType.TEXT_PLAIN).build());
      }
      
      // parsing response to get token :
      // NB. no need to go beyond explicit bare JSON parsing (MessageBodyProvider etc.)
      JsonNode tokenExchangeResJsonNode;
      try {
         tokenExchangeResJsonNode = jsonNodeMapper.readTree(tokenExchangeResBody);
      } catch (IOException ioex) {
         int errorStatus = (Status.Family.SUCCESSFUL != tokenExchangeRes.getStatusInfo().getFamily())
               ? tokenExchangeRes.getStatus() : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
         throw new WebApplicationException(Response.status(errorStatus)
               .entity("Error parsing as JSON tokenExchangeResBody returned by Kernel ("
                     + tokenExchangeResBody + ") : " + ioex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      JsonNode errorNode = tokenExchangeResJsonNode.get("error");
      if (errorNode != null) {
         String errorMsg = errorNode.asText(); // NB. more lenient thant textValue()
         int errorStatus = (Status.Family.SUCCESSFUL != tokenExchangeRes.getStatusInfo().getFamily())
               ? tokenExchangeRes.getStatus() : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
         throw new WebApplicationException(Response.status(errorStatus)
               .entity("Returned tokenExchangeResBody for exchanging code is error : " + errorMsg)
                     .type(MediaType.TEXT_PLAIN).build());
      }
      // also checking for error status even if no JSON error info :
      if (Status.Family.SUCCESSFUL != tokenExchangeRes.getStatusInfo().getFamily()) {
         throw new WebApplicationException(Response.status(tokenExchangeRes.getStatus())
               .entity("Error calling Kernel to exchange token from code : "
                     + "response status is not successful (2xx) but " + tokenExchangeRes.getStatus())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      String token = null;
      String id_token = null;
      try {
         token = tokenExchangeResJsonNode.get(RESPONSE_ACCESS_TOKEN).textValue();
         id_token = tokenExchangeResJsonNode.get(RESPONSE_ID_TOKEN).textValue();
         // NB. also expires_in, scope, id_token
      } catch (Exception ex) {
         throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
               .entity("Error getting " + RESPONSE_ACCESS_TOKEN+ "or " + RESPONSE_ID_TOKEN
                     + " from tokenExchangeResJsonNode returned by Kernel ("
                     + tokenExchangeResJsonNode + ") : " + ex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      if (token == null || id_token == null) {
         throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
               .entity("No " + RESPONSE_ACCESS_TOKEN + "or " + RESPONSE_ID_TOKEN
                     + " in tokenExchangeResJsonNode returned by Kernel ("
                     + tokenExchangeResJsonNode + ")")
                     .type(MediaType.TEXT_PLAIN).build());
      }
      
      // TODO LATER check JWT signature see schambon's OpenIdCService using nimbus
      
      // getting user info :
      WebClient userInfoClient = WebClient.create(kernelUserInfoEndpointUrl) // "http://requestb.in/saf6sosa"
            .header(HttpHeaders.AUTHORIZATION, BEARER_AUTH_PREFIX + token);
      Response userInfoRes;
      try {
         userInfoRes = userInfoClient.get();
      } catch (Exception ex) {
         // TODO or ServiceUnavailableException ?
         throw new InternalServerErrorException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
               .entity("Error calling Kernel to get user info  : " + ex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      String userInfoResBody = userInfoRes.readEntity(String.class); // ex. {"email":"m.d@openwide.fr","email_verified":true,"locale":"und","name":"Marc Dutoo","nickname":"Marc Dutoo","sub":"9...c","updated_at":1426608912,"zoneinfo":"Europe/Paris"}
      if (Status.Family.SUCCESSFUL != userInfoRes.getStatusInfo().getFamily()) {
         throw new WebApplicationException(Response.status(userInfoRes.getStatus())
               .entity(userInfoRes.getStatus() + " error calling Kernel to get user info ("
                     + userInfoResBody + ")")
                     .type(MediaType.TEXT_PLAIN).build());
      }
      
      // parsing response to get token :
      // NB. no need to go beyond explicit bare JSON parsing (MessageBodyProvider etc.)
      Map<String,Object> userInfo;
      try {
         @SuppressWarnings("unchecked")
         Map<String,Object> parsedUserInfo = jsonNodeMapper.readValue(userInfoResBody, Map.class);
         userInfo = parsedUserInfo;
      } catch (IOException ioex) {
         int errorStatus = (Status.Family.SUCCESSFUL != userInfoRes.getStatusInfo().getFamily())
               ? userInfoRes.getStatus() : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
         throw new WebApplicationException(Response.status(errorStatus)
               .entity("Error parsing as JSON userInfoResBody returned by Kernel ("
                     + userInfoResBody + ") : " + ioex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      /*JsonNode errorNode = tokenExchangeResJsonNode.get("error");
      if (errorNode != null) {
         String error = errorNode.asText(); // NB. more lenient thant textValue()
         int errorStatus = (Status.Family.SUCCESSFUL != tokenExchangeRes.getStatusInfo().getFamily())
               ? tokenExchangeRes.getStatus() : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
         throw new WebApplicationException(Response.status(errorStatus)
               .entity("Returned tokenExchangeResBody for exchanging code is error : " + error)
                     .type(MediaType.TEXT_PLAIN).build());
      }
      // also checking for error status even if no JSON error info :
      if (Status.Family.SUCCESSFUL != tokenExchangeRes.getStatusInfo().getFamily()) {
         throw new WebApplicationException(Response.status(tokenExchangeRes.getStatus())
               .entity("Error calling Kernel to exchange token from code : "
                     + "response status is not successful (2xx) but " + tokenExchangeRes.getStatus())
                     .type(MediaType.TEXT_PLAIN).build());
      }*/
      
      // getting token info (sub_groups...) :
      WebClient tokenInfoClient = WebClient.create(kernelTokenInfoEndpointUrl)
            .header(HttpHeaders.AUTHORIZATION, tokenExchangeBasicAuth);
      Response tokenInfoRes;
      try {
         tokenInfoRes = tokenInfoClient.form(new Form()
               .param("token_type_hint", "access_token").param("token", token));
      } catch (Exception ex) {
         // TODO or ServiceUnavailableException ?
         throw new InternalServerErrorException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
               .entity("Error calling Kernel to get token info  : " + ex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      String tokenInfoResBody = tokenInfoRes.readEntity(String.class); // ex. {"active":true,"exp":1433778460,"iat":1433774860,"scope":"datacore","client_id":"dc","sub":"9cf96195-dab0-41f8-9300-08881da13abc","token_type":"Bearer","sub_groups":["0...e","c...7","5...e"]}
      if (Status.Family.SUCCESSFUL != tokenInfoRes.getStatusInfo().getFamily()) {
         throw new WebApplicationException(Response.status(tokenInfoRes.getStatus())
               .entity(tokenInfoRes.getStatus() + " error calling Kernel to get token info ("
                     + tokenInfoResBody + ")")
                     .type(MediaType.TEXT_PLAIN).build());
      }
      // parsing response to get token :
      // NB. no need to go beyond explicit bare JSON parsing (MessageBodyProvider etc.)
      try {
         @SuppressWarnings("unchecked")
         Map<String,Object> tokenInfo = jsonNodeMapper.readValue(tokenInfoResBody, Map.class);
         userInfo.putAll(tokenInfo);
         userInfo.put(RESPONSE_ID_TOKEN,id_token);
      } catch (IOException ioex) {
         int errorStatus = (Status.Family.SUCCESSFUL != userInfoRes.getStatusInfo().getFamily())
               ? userInfoRes.getStatus() : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
         throw new WebApplicationException(Response.status(errorStatus)
               .entity("Error parsing as JSON tokenInfoResBody returned by Kernel ("
                     + tokenInfoResBody + ") : " + ioex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      
      String userTokenInfo;
      try {
         userTokenInfo = jsonNodeMapper.writeValueAsString(userInfo);
      } catch (JsonProcessingException jpex) {
         int errorStatus = (Status.Family.SUCCESSFUL != userInfoRes.getStatusInfo().getFamily())
               ? userInfoRes.getStatus() : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
         throw new WebApplicationException(Response.status(errorStatus)
               .entity("Error writing as JSON userTokenInfo ("
                     + userInfo + ") : " + jpex.getMessage())
                     .type(MediaType.TEXT_PLAIN).build());
      }
      
      // set auth cookie while redirecting to app :
      try {
         String authHeader = BEARER_AUTH_PREFIX + tokenEncrypter.encrypt(token);
         NewCookie encryptedTokenCookie = new NewCookie("authorization", authHeader,
               "/", // WARNING if no ;Path=/ it is not set after a redirect see http://stackoverflow.com/questions/1621499/why-cant-i-set-a-cookie-and-redirect
               null, null, cookieMaxAge, cookieSecure);
         NewCookie userInfoCookie = new NewCookie("userinfo", userTokenInfo,
               "/", // WARNING if no ;Path=/ it is not set after a redirect see http://stackoverflow.com/questions/1621499/why-cant-i-set-a-cookie-and-redirect
               null, null, cookieMaxAge, cookieSecure);
         throw new WebApplicationException(Response.seeOther(new URI(playgroundUiUrl))
               .cookie(encryptedTokenCookie).cookie(userInfoCookie)
               .build());
         // or + 
      } catch (URISyntaxException usex) {
         throw new WebApplicationException(usex, Response.serverError()
               .entity("Redirection target " + baseUrl + " should be an URI").build());
      }
   }
   
}
