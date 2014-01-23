package org.oasis.datacore.rights.rest.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiImplicitParam;
import com.wordnik.swagger.annotations.ApiImplicitParams;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * 
 * @author agiraudon
 *
 */

@Path("dc/r")
@Api(value = "/dc/r", 
	 description = "Rights management (add/remove/flush)",
     authorizations = "OASIS OAuth and required Datacore Resource authorizations")
public interface RightsApi {

	 	
	   @Path("/{type}/{iri}/{version}")
	   @POST
	   @Consumes(value = { MediaType.APPLICATION_JSON })
	   @ApiOperation(
			   value = "Add rights on a resource",
			   notes = "You must provide the type, id and version of the resource " +
			   		   "you need to add rights on." +
					   "One of the tree set (writers, readers, owners) must be defined." +
			   		   "The DCRights object will add rights on your resource only if they are not already on it." +
					   "Only owners are allowed to add rights on a resource." +
					   "(The scope implementation will be added in the future)"
	   )
	   @ApiResponses(value = {
			   @ApiResponse(code = 500, message = "Internal server error"),
			   @ApiResponse(code = 404, message = "Resource not found"),
			   @ApiResponse(code = 400, message = "Bad request"),
			   @ApiResponse(code = 200, message = "Rights have been added on the resource")
	   })
	   @ApiImplicitParams({
		    @ApiImplicitParam(
		    		name=HttpHeaders.AUTHORIZATION,
		    		paramType="header",
		    		dataType="string",
		            value="OAuth2 Bearer or (DEV MODE ONLY) Basic Auth",
		            defaultValue="Basic YWRtaW46YWRtaW4="
		    )
	   })
	   public void addRightsOnResource(
			@PathParam("type") String modelType,
			@PathParam("iri") String iri,
			@PathParam("version") long version,
			@ApiParam(value = "readers/writers/owners collections to add to existents collections (union)") DCRights dcRights
	   );
	   
	   @Path("/{type}/{iri}/{version}")
	   @DELETE
	   @Consumes(value = { MediaType.APPLICATION_JSON })
	   @ApiOperation(
			   value = "Remove rights on a resource",
			   notes = "You must provide the type, id and version of the resource " +
			   		   "you need to remove rights on." +
					   "One of the tree set (writers, readers, owners) must be defined" +
					   "Only owners are allowed to remove rights on a resource" +
					   "(The scope implementation will be added in the future)"
	   )
	   @ApiResponses(value = {
			   @ApiResponse(code = 500, message = "Internal server error"),
			   @ApiResponse(code = 404, message = "Resource not found"),
			   @ApiResponse(code = 400, message = "Bad request"),
			   @ApiResponse(code = 200, message = "Rights have been correctly remove from the resource")
	   })
	   @ApiImplicitParams({
		    @ApiImplicitParam(
		    		name=HttpHeaders.AUTHORIZATION,
		    		paramType="header",
		    		dataType="string",
		            value="OAuth2 Bearer or (DEV MODE ONLY) Basic Auth",
		            defaultValue="Basic YWRtaW46YWRtaW4="
		    )
	   })
	   public void removeRightsOnResource(
			@PathParam("type") String modelType,
			@PathParam("iri") String iri,
			@PathParam("version") long version,
			@ApiParam(value = "readers/writers/owners collections to remove from existents collections (difference)") DCRights dcRights
	   );
	   
	   @Path("/f/{type}/{iri}/{version}")
	   @PUT
	   @ApiOperation(
			   value = "Remove all rights defined on resource except owners",
			   notes = "You must provide the type, id and version of the resource " +
			   		   "you need to remove writer and reader rights on."
	   )
	   @ApiResponses(value = {
			   @ApiResponse(code = 500, message = "Internal server error"),
			   @ApiResponse(code = 404, message = "Resource not found"),
			   @ApiResponse(code = 400, message = "Bad request"),
			   @ApiResponse(code = 200, message = "Readers and writers have been correctly removed from the resource")
	   })
	   @ApiImplicitParams({
		    @ApiImplicitParam(
		    		name=HttpHeaders.AUTHORIZATION,
		    		paramType="header",
		    		dataType="string",
		            value="OAuth2 Bearer or (DEV MODE ONLY) Basic Auth",
		            defaultValue="Basic YWRtaW46YWRtaW4="
		    )
	   })
	   public void flushRightsOnResource(
			@PathParam("type") String modelType,
			@PathParam("iri") String iri,
			@PathParam("version") long version
	   );
	   
	   
	   @Path("/{type}/{iri}/{version}")
	   @GET
	   @ApiOperation(
			   value = "Get rights list of a resource",
			   notes = "You must provide the type, id and version of the resource " +
			   		   "to get the rights associated to it.",
			   response = DCRights.class
	   )
	   @ApiResponses(value = {
			   @ApiResponse(code = 500, message = "Internal server error"),
			   @ApiResponse(code = 404, message = "Resource not found"),
			   @ApiResponse(code = 400, message = "Bad request"),
			   @ApiResponse(code = 200, message = "Rights have been correctly retrieved")
	   })
	   @ApiImplicitParams({
		    @ApiImplicitParam(
		    		name=HttpHeaders.AUTHORIZATION,
		    		paramType="header",
		    		dataType="string",
		            value="OAuth2 Bearer or (DEV MODE ONLY) Basic Auth",
		            defaultValue="Basic YWRtaW46YWRtaW4="
		    )
	   })
	   public DCRights getRightsOnResource(
			@PathParam("type") String modelType,
			@PathParam("iri") String iri,
			@PathParam("version") long version
	   );
	   	
}