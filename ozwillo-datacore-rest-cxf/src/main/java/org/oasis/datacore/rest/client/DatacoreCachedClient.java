package org.oasis.datacore.rest.client;

import org.oasis.datacore.rest.api.DCResource;
import org.springframework.cache.Cache;


/**
 * Use this interface to use the Datacore cached client.
 * 
 * Extends DatacoreClientApi with local-only, non-JAXRS client helper methods
 * especially to take advantage of client local caching.
 * 
 * Along with DCResource's builder methods, it eases up the client developer's
 * job.
 * 
 * @author mdutoo
 *
 */
public interface DatacoreCachedClient extends DatacoreClientApi {
   
   /**
    * Shortcut to postDataInType using resource's first type
    * (does not parse uri for that ; if none, lets server explode)
    * @param resource
    * @return
    * @throws ClientException if bad URI, & other JAXRS exceptions accordingly
    */
   DCResource postDataInType(DCResource resource) throws IllegalArgumentException;

   /**
    * Shortcut to putDataInType using resource's first type & id
    * (does not parse uri for that ; if none, lets server explode)
    * @param resource
    * @return
    * @throws ClientException if bad URI, & other JAXRS exceptions accordingly
    */
   DCResource putDataInType(DCResource resource) throws IllegalArgumentException;

   /**
    * Shortcut to getData (modelType, iri, version) using provided resource's
    * @param resource
    * @return
    * @throws ClientException if bad URI, & other JAXRS exceptions accordingly
    */
   DCResource getData(DCResource resource) throws IllegalArgumentException;

   /**
    * Shortcut to deleteData (modelType, iri, version) using provided resource's
    * @param resource
    * @throws ClientException if bad URI, & other JAXRS exceptions accordingly
    */
   void deleteData(DCResource resource) throws IllegalArgumentException;
   
   /**
    * Provide access to cache, ex. to clear it or evict some Resources from it
    */
   Cache getCache();


   ///////////////////////////////////////////////////////////////////////
   // HELPERS WITH PROJECT
   
   DCResource postDataInTypeInProject(DCResource r, String projectName);
   /** to avoid POST merge */
   DCResource putDataInTypeInProject(DCResource r, String projectName);
   
}
