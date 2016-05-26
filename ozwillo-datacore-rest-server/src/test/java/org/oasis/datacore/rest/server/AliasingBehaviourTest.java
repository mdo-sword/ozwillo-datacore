package org.oasis.datacore.rest.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis.datacore.common.context.DCRequestContextProvider;
import org.oasis.datacore.common.context.DCRequestContextProviderFactory;
import org.oasis.datacore.common.context.SimpleRequestContextProvider;
import org.oasis.datacore.core.meta.SimpleUriService;
import org.oasis.datacore.core.meta.model.DCModelBase;
import org.oasis.datacore.core.meta.model.DCModelService;
import org.oasis.datacore.core.meta.pov.DCProject;
import org.oasis.datacore.rest.api.DCResource;
import org.oasis.datacore.rest.api.DatacoreApi;
import org.oasis.datacore.rest.client.DatacoreCachedClient;
import org.oasis.datacore.rest.server.resource.ResourceException;
import org.oasis.datacore.rest.server.resource.ResourceService;
import org.oasis.datacore.sample.CityCountrySample;
import org.oasis.datacore.server.uri.BadUriException;
import org.oasis.datacore.server.uri.UriService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.NotFoundException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * User: schambon
 * Date: 2/17/16
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:oasis-datacore-rest-server-test-context.xml" })
public class AliasingBehaviourTest {

    @Autowired
    private CityCountrySample cityCountrySample;

    @Autowired
    @Qualifier("datacoreApiCachedJsonClient")
    private DatacoreCachedClient datacoreApiClient;

    @Autowired
    private DCModelService modelService;

    @Value("${datacoreApiClient.containerUrl}")
    private String containerUrl;

    @Before
    public void before() {
        // set sample project :
        SimpleRequestContextProvider.setSimpleRequestContext(new ImmutableMap.Builder<String, Object>()
                .put(DatacoreApi.PROJECT_HEADER, DCProject.OASIS_SAMPLE).build());
    }

    @Test
    public void createAlias() throws ResourceException, BadUriException {
        cityCountrySample.cleanDataOfCreatedModels();
        cityCountrySample.initData();

//        DCModelBase modelBase = modelService.getModelBase(CityCountrySample.CITY_MODEL_NAME);
//        assertNotNull(modelBase);
//        assertNotNull(modelBase.getIdFieldNames());
//        assertEquals(2, modelBase.getIdFieldNames().size());

        DCResource resource = datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Lyon");
        assertNotNull(resource);
        String prefix = containerUrl + "/dc/type/" + CityCountrySample.CITY_MODEL_NAME;

        assertEquals(Collections.singletonList(prefix + "/France/Lyon"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Lyon"));

        // let's try updating the resource
        resource.set("n:name", "Lugdunum");
        datacoreApiClient.putDataInType(resource, CityCountrySample.CITY_MODEL_NAME, "France/Lyon");

        DCResource updatedResource = datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Lyon");
        assertEquals(prefix + "/France/Lugdunum", updatedResource.getUri());
        Long version = updatedResource.getVersion();

        // check that the "new" uri points to the same resource, too, with the same version number
        updatedResource = datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Lugdunum");
        assertEquals(prefix + "/France/Lugdunum", updatedResource.getUri());
        assertEquals(version, updatedResource.getVersion());

        assertEquals(Arrays.asList(prefix + "/France/Lugdunum", prefix + "/France/Lyon"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Lyon"));
        assertEquals(Arrays.asList(prefix + "/France/Lugdunum", prefix + "/France/Lyon"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Lugdunum"));

        // update again!
        updatedResource.set("n:name", "Leo");
        datacoreApiClient.putDataInType(updatedResource, CityCountrySample.CITY_MODEL_NAME, "France/Lyon");// notice that we use /Lyon: it's all the same really'
        updatedResource = datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Lyon");

        // make sure the version has been incemented
        assertEquals(new Long(version.longValue() + 1), updatedResource.getVersion());
        assertEquals(prefix + "/France/Leo", updatedResource.getUri());

        assertEquals(Arrays.asList(prefix + "/France/Leo", prefix + "/France/Lugdunum", prefix + "/France/Lyon"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Lyon"));
        assertEquals(Arrays.asList(prefix + "/France/Leo", prefix + "/France/Lugdunum", prefix + "/France/Lyon"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Lugdunum"));
        assertEquals(Arrays.asList(prefix + "/France/Leo", prefix + "/France/Lugdunum", prefix + "/France/Lyon"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Leo"));

        // try looping back to Lyon, make sure that all aliases are still there
        updatedResource.set("n:name", "Lyon");
        datacoreApiClient.putDataInType(updatedResource, CityCountrySample.CITY_MODEL_NAME, "France/Leo");
        updatedResource = datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Leo");

        assertEquals(new Long(version.longValue() + 2), updatedResource.getVersion());
        assertEquals(prefix + "/France/Lyon", updatedResource.getUri());

        // check that all the uris we have used are still valid
        assertEquals(prefix + "/France/Lyon", datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Lyon").getUri());
        assertEquals(prefix + "/France/Lyon", datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Lugdunum").getUri());
        assertEquals(prefix + "/France/Lyon", datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, "France/Leo").getUri());

        assertEquals(Arrays.asList(prefix + "/France/Lyon", prefix + "/France/Leo", prefix + "/France/Lugdunum"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Lyon"));
        assertEquals(Arrays.asList(prefix + "/France/Lyon", prefix + "/France/Leo", prefix + "/France/Lugdunum"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Lugdunum"));
        assertEquals(Arrays.asList(prefix + "/France/Lyon", prefix + "/France/Leo", prefix + "/France/Lugdunum"),
                datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, "France/Leo"));

        // delete the resource
        datacoreApiClient.deleteData(updatedResource);

        // check that all aliases have been deleted too
        ImmutableList.of("France/Lyon", "France/Lugdunum", "France/Leo").stream()
                .forEach(iri -> {
                    try {
                        datacoreApiClient.getData(CityCountrySample.CITY_MODEL_NAME, iri);
                        fail("Should be null!");
                    } catch(NotFoundException e) {
                        // ok
                    }
                    try {
                        datacoreApiClient.getAliases(CityCountrySample.CITY_MODEL_NAME, iri);
                        fail("Should be null!");
                    } catch(NotFoundException e) {
                        // ok
                    }
                });

    }

}
