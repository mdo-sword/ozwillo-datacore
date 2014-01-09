package org.oasis.datacore.rest.client;

import org.oasis.datacore.rest.api.DCResource;


/**
 * Extends DatacoreClientApi with local-only, non-JAXRS client helper methods
 * especially to take advantage of client local caching.
 * @author mdutoo
 *
 */
public interface DatacoreCachedClient extends DatacoreClientApi {
   
   /**
    * Shortcut to postDataInType using resource's first type
    * (does not parse uri for that ; if none, lets server explode)
    * @param resource
    * @return
    */
   public DCResource postDataInType(DCResource resource);

   /**
    * Shortcut to putDataInType using resource's first type & id
    * (does not parse uri for that ; if none, lets server explode)
    * @param resource
    * @return
    */
   public DCResource putDataInType(DCResource resource);
   
}