package org.oasis.datacore.model.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.oasis.datacore.common.context.DCRequestContextProvider;
import org.oasis.datacore.common.context.DCRequestContextProviderFactory;
import org.oasis.datacore.common.context.SimpleRequestContextProvider;
import org.oasis.datacore.core.entity.model.DCEntity;
import org.oasis.datacore.core.entity.query.QueryException;
import org.oasis.datacore.core.entity.query.ldp.LdpEntityQueryService;
import org.oasis.datacore.core.init.InitableBase;
import org.oasis.datacore.core.meta.DataModelServiceImpl;
import org.oasis.datacore.core.meta.ModelException;
import org.oasis.datacore.core.meta.model.DCModelBase;
import org.oasis.datacore.core.meta.model.DCModelService;
import org.oasis.datacore.core.meta.pov.DCProject;
import org.oasis.datacore.rest.api.DCResource;
import org.oasis.datacore.rest.server.event.EventService;
import org.oasis.datacore.rest.server.resource.ResourceEntityMapperService;
import org.oasis.datacore.rest.server.resource.ResourceException;
import org.oasis.datacore.sample.ResourceModelIniter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


/**
 * TODO from client rather than from local service, for Italian Datacore 
 * 
 * @author mdutoo
 *
 */
@Component
public class LoadPersistedModelsAtInit extends InitableBase {

   @Autowired
   private LdpEntityQueryService ldpEntityQueryService;
   @Autowired
   protected ResourceEntityMapperService resourceEntityMapperService; // to unmap LdpEntityQueryService results
   @Autowired
   protected ModelResourceMappingService mrMappingService;
   @Autowired
   private DCModelService dataModelService;
   @Autowired
   private DataModelServiceImpl dataModelAdminService;
   
   @Autowired
   protected EventService eventService;
   
   @Autowired
   ///@Qualifier("datacore.cxfJaxrsApiProvider")
   //protected DCRequestContextProvider requestContextProvider;
   protected DCRequestContextProviderFactory requestContextProviderFactory;

   @Override
   public int getOrder() {
      return 1000000;
   }

   @Override
   protected void doInit() {
      try {
         loadProjects();
      } catch (QueryException qex) {
         throw new RuntimeException(qex); // should not happen
      }
   }

   
   private void loadModels(String projectName) throws QueryException {
      // now, reload all models that had been persisted in mongo in last execution : TODO wrap in projects
      List<DCResource> modelResources = findDataInType(
            ResourceModelIniter.MODEL_MODEL_NAME, ResourceModelIniter.MODEL_NAME_PROP, projectName);
      HashMap<String,ResourceException> previousModelsInError = null, modelsInError = null;
      List<String> loadedModelAbsoluteNames = new ArrayList<String>(modelResources.size());
      do {
         previousModelsInError = modelsInError;
         modelsInError = new HashMap<String,ResourceException>(); // not clear() because previousModelsInError
         
         for (DCResource modelResource : modelResources) {
            String modelName = (String) modelResource.get(ResourceModelIniter.MODEL_NAME_PROP);
            String modelProjectName = (String) modelResource.get("dcmo:pointOfViewAbsoluteName");
            String modelAbsoluteName = modelProjectName + "." + modelName; // TODO ????
            if (previousModelsInError != null && !previousModelsInError.containsKey(modelAbsoluteName)) {
               continue; // not first time and already imported successfully
            }
            try {
               if (!projectName.equals(modelProjectName)) {
                  throw new ResourceException("Found model " + modelName
                        + " having wrong project " + modelProjectName + " while loading project, "
                        + "possibly obsolete one that must be deleted from " + projectName + "."
                        + dataModelAdminService.getStorageModel(dataModelAdminService.getModel(
                              ResourceModelIniter.MODEL_MODEL_NAME)).getCollectionName() + " collection.",
                        modelResource, dataModelAdminService.getProject(projectName));
               }
               
               // set context project before loading :
               DCModelBase model = new SimpleRequestContextProvider<DCModelBase>() {
                  protected DCModelBase executeInternal() throws ResourceException {
                     ///eventService.triggerResourceEvent(DCResourceEvent.Types.UPDATED, modelResource);
                     // NB. handled by ModelResourceDCListener
                     
                     DCModelBase model = mrMappingService.toModelOrMixin(modelResource);
                     dataModelAdminService.addModel(model);
                     return model;
                     // TODO LATER once all is loaded, ((clean cache and)) repersist all in case were wrong
                  }
               }.execInContext(new ImmutableMap.Builder<String, Object>()
                     .put(DCRequestContextProvider.PROJECT, modelProjectName).build());
               loadedModelAbsoluteNames.add(model.getAbsoluteName());
               modelsInError.remove(model.getAbsoluteName());

            } catch (RuntimeException rex) {
               if (!(rex.getCause() instanceof ResourceException)) {
                  throw rex;
               }
               modelsInError.put(modelAbsoluteName, (ResourceException) rex.getCause());
            } catch (Exception rex) {
               logger.debug("Unexpected Exception reloading model resource from persistence " + modelResource, rex);
            }
         }
      } while (!modelsInError.isEmpty() && !modelsInError.keySet().equals(previousModelsInError.keySet()));

      logger.info("Loaded " + loadedModelAbsoluteNames.size() + " models");
      logger.debug("   loaded models details : " + loadedModelAbsoluteNames);
      
      if (!modelsInError.isEmpty()) {
         logger.error("Unable to reload from persistence models with absolute names : "
               + modelsInError);
      }
   }

   /**
    * Loads projects & povs & models from oasis.main project only, and from there their models.
    * @throws QueryException
    */
   private void loadProjects() throws QueryException {
      List<DCResource> projectResources = findDataInType(
            ResourceModelIniter.MODEL_PROJECT_NAME, ResourceModelIniter.POINTOFVIEW_NAME_PROP);
      HashMap<String,ResourceException> previousProjectsInError, projectsInError = new HashMap<String,ResourceException>();
      List<String> loadedProjectNames = new ArrayList<String>(projectResources.size());
      do {
         previousProjectsInError = projectsInError;
         projectsInError = new HashMap<String,ResourceException>(); // not clear() because previousModelsInError
         
         for (DCResource projectResource : projectResources) {
            //String projectProjectName = (String) projectResource.get("dcmo:pointOfViewAbsoluteName");
            String projectName = (String) projectResource.get(ResourceModelIniter.POINTOFVIEW_NAME_PROP);
            if (previousProjectsInError != null && !previousProjectsInError.containsKey(projectName)) {
               continue; // not first time and already imported successfully
            }
            try {
               // NB. not in context either since ini oasis.main project
               DCProject project = mrMappingService.toProject(projectResource); // NB. loads visible projects but not models
               DCProject foundProject = dataModelAdminService.getProject(project.getName());
               if (foundProject != null) {
                  // keeping existing project, ONLY REQUIRED IN CASE OF METAMODEL
                  // TODO LATER put them in oasis.model and handle it separately
                  project.getLocalVisibleProjects().stream().forEach(lvp -> foundProject.addLocalVisibleProject(lvp));
                  project = foundProject;
               }
               dataModelAdminService.addProject(project); // must add project BEFORE adding models
               loadModels(projectName); // NB. NOT yet using project.visibleProjects to conf relationship
               loadedProjectNames.add(project.getName());
               // TODO LATER once all is loaded, ((clean cache and)) repersist all in case were wrong
               projectsInError.remove(projectName);

            } catch (ResourceException rex) {
               projectsInError.put(projectName, (ResourceException) rex); // missing local model or visible project
            } catch (Exception rex) {
               logger.debug("Unexpected Exception reloading project resource from persistence " + projectResource, rex);
            }
         }
      } while (!projectsInError.isEmpty() && !projectsInError.keySet().equals(previousProjectsInError.keySet()));
      
      logger.info("Loaded " + loadedProjectNames.size() + " projects : " + loadedProjectNames);
      logger.debug("   loaded projects details : " + loadedProjectNames);
   }

   /**
    * Do range query based paginated queries to get all Resources (in current pov or main project).
    * @param modelType
    * @param nameProp indexed field to be used to do range queries
    * @return
    * @throws QueryException if at any pagination step query fails (and prevents to go further),
    * actually if the first one is OK the next ones should also be
    */
   private List<DCResource> findDataInType(String modelType, String nameProp) throws QueryException {
      List<DCResource> allResources = new ArrayList<DCResource>();
      List<DCEntity> entities = null;
      Map<String, List<String>> nextProjectsByName = new HashMap<String, List<String>>(2);
      nextProjectsByName.put(DCResource.KEY_DCCREATED, // ResourceModelIniter.POINTOFVIEW_NAME_PROP
            new ImmutableList.Builder<String>().add("+").build()); // older first
      while (!(entities = ldpEntityQueryService.findDataInType(modelType,
            nextProjectsByName, 0, ldpEntityQueryService.getMaxLimit())).isEmpty()) {
         List<DCResource> resources = resourceEntityMapperService.entitiesToResources(entities);
         // NB. this doesn't check model or anything
         allResources.addAll(resources);
         
         // preparing next find :
         String lastResourceName = (String) resources.get(resources.size() - 1).get(nameProp);
         nextProjectsByName.put(nameProp, new ImmutableList.Builder<String>()
               .add(">\"" + lastResourceName + "\"+").build()); // older first
      }
      return allResources;
   }
   
   /**
    * Same but in project or pov
    * @param modelType
    * @param nameProp
    * @param project
    * @return
    * @throws QueryException
    */
   private List<DCResource> findDataInType(String modelType, String nameProp, String project) throws QueryException {
      try {
         List<DCResource> allResources = new SimpleRequestContextProvider<List<DCResource>>() {
            // set context project before loading :
            protected List<DCResource> executeInternal() throws QueryException {
               return findDataInType(modelType, nameProp);
            }
         }.execInContext(new ImmutableMap.Builder<String, Object>()
               .put(DCRequestContextProvider.PROJECT, project).build());
         return allResources;

      } catch (RuntimeException rex) {
         if (!(rex.getCause() instanceof QueryException)) {
            throw rex;
         }
         throw (QueryException) rex.getCause();
      } catch (Exception rex) {
         throw new RuntimeException("Unexpected error while querying in project", rex);
      }
   }

}
