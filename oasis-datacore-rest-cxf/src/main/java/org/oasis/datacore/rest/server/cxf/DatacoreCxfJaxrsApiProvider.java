package org.oasis.datacore.rest.server.cxf;

import java.util.ArrayList;
import java.util.Map;

import org.apache.cxf.message.Exchange;
import org.oasis.datacore.common.context.DCRequestContextProvider;
import org.oasis.datacore.common.context.SimpleRequestContextProvider;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;


/**
 * Impl'd on CXF's Exchange
 * if no Exchange & for unit tests defaults to SimpleRequestContextProvider
 * within DCRequestContextProviderFactory, that must be used to access it.
 * Used also on client side.
 * 
 * @author mdutoo
 *
 */
@Component("datacore.cxfJaxrsApiProvider")
public class DatacoreCxfJaxrsApiProvider extends CxfJaxrsApiProvider implements DCRequestContextProvider {
   
   private static final Map<String, Object> EMPTY_MAP = new ImmutableMap.Builder<String, Object>().build();

   public Map<String, Object> getRequestContext() {
      Exchange exchange = this.getExchange();
      if (exchange != null) { // REST !
         ArrayList<Map<String,Object>> fallbacks = new ArrayList<Map<String,Object>>(2);
         if (exchange.getInMessage() != null) {
            fallbacks.add(exchange.getInMessage()); // case of server in request
         } else if (exchange.getOutMessage() != null) {
            fallbacks.add(exchange.getOutMessage()); // case of client out request
         }
         // NB. at least one of those will be not null, the way CXF works
         // and only one (unless done in (ClientIn or ServerOut) Response interceptor, which it is not for)
         return new MapWithReadonlyFallbacks<String,Object>(exchange, fallbacks);
      } // else not REST
      if (SimpleRequestContextProvider.shouldEnforce()) {
         throw new RuntimeException("There is no context");
      }
      // helper for unit tests :
      Map<String, Object> requestContext = SimpleRequestContextProvider.getSimpleRequestContext();
      if (requestContext == null) {
         return EMPTY_MAP; // to avoid NullPointerExceptions
      }
      return requestContext;
      /*if (requestContext != null || !isUnitTest()) {
         return requestContext;
      }
      throw new RuntimeException("SimpleRequestContextProvider should have been set up in unit test");*/
   }
   
   @Override
   public Object get(String key) {
      return this.getRequestContext().get(key);
   }
   
   @Override
   public void set(String key, Object value) {
      this.getRequestContext().put(key, value);
   }
   
}
