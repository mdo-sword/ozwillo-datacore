package org.oasis.datacore.core.entity.query.ldp;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.oasis.datacore.core.entity.EntityQueryService;
import org.oasis.datacore.core.entity.model.DCEntity;
import org.oasis.datacore.core.entity.query.QueryException;
import org.oasis.datacore.core.entity.query.sparql.EntityQueryEngineBase;
import org.oasis.datacore.core.meta.model.DCFieldTypeEnum;
import org.oasis.datacore.core.meta.model.DCModelService;
import org.oasis.datacore.rest.server.parsing.exception.ResourceParsingException;
import org.oasis.datacore.rest.server.resource.ValueParsingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * Provides W3C LDP (Linked Data Platform)-like query support by using
 * native impl of LdpEntityQueryService.
 * Not actually used since LdpEntityQueryService is directly provided
 * as REST by DatacoreApiImpl, but rather useful as an example of engine.
 * 
 * @author mdutoo
 *
 */
@Component
public class NativeLdpEntityQueryEngineImpl extends EntityQueryEngineBase {
   
   @Autowired
   private LdpEntityQueryService ldpEntityQueryService;
   @Autowired
   private DCModelService modelService;
   @Autowired
   private ValueParsingService valueParsingService;

   public NativeLdpEntityQueryEngineImpl() {
      super(EntityQueryService.LANGUAGE_LDPQL);
   }
   
   @Override
   public List<DCEntity> queryInType(String modelType, String query,
         String language) throws QueryException {
      // TODO or to be able to refactor in LdpNativeQueryServiceImpl, rather do (as getQueryParameters does) :
      /// params = JAXRSUtils.getStructuredParams((String)message.get(Message.QUERY_STRING), "&", decode, decode);
      // by getting query string from either @Context-injected CXF Message or uriInfo.getRequestUri() (BUT costlier) ??
      boolean decode = false; // don't decode, has already been decoded as part of HTTP query
      MultivaluedMap<String, String> params = JAXRSUtils.getStructuredParams(query, "&", decode, decode);
      int start = getIntParam(params, "start", 0);
      int limit = getIntParam(params, "limit", 10);
      return ldpEntityQueryService.findDataInType(modelType, params, start, limit);
   }

   /**
    * Unsupported
    */
   @Override
   public List<DCEntity> query(String query, String language)
         throws QueryException {
      // TODO LATER
      throw new QueryException("Unsupported");
   }

   private int getIntParam(MultivaluedMap<String, String> params, String name, int defaultValue)
         throws QueryException {
      List<String> startList = params.get(name);
      if (startList != null && !startList.isEmpty()) {
         String startString = startList.get(0);
         try {
            Object found = valueParsingService.parseValueFromString(DCFieldTypeEnum.INTEGER, startString);
            if (found != null) {
               return (Integer) found;
            }
         } catch (ResourceParsingException e) {
            throw new QueryException("Error parsing query parameter " + name + " : " + e.getMessage());
         }
      }
      return defaultValue;
   }

}
