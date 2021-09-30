/*
 * Copyright (c) 2008-2021, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.test.endpoint.mediastream;

import edu.mit.ll.em.api.rs.MediaStreamResponse;
import edu.mit.ll.em.api.test.base.EndpointTest;
import edu.mit.ll.em.api.test.dataprovider.MediaStreamServiceDataProvider;
import edu.mit.ll.nics.common.entity.MediaStream;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.testng.annotations.*;

import java.util.List;


import static javax.ws.rs.core.Response.Status;
import static org.testng.Assert.*;
import static io.restassured.RestAssured.*;
import static io.restassured.matcher.RestAssuredMatchers.*;
import static org.hamcrest.Matchers.*;
// Recommended in docs for json validation, but doesn't seem to exist
//import static io.restassured.module.jsv.JsonSchemaValidator.*;


@Test
public class MediaStreamServiceEndpointTest extends EndpointTest {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(MediaStreamServiceEndpointTest.class.getName());

    //private Header header = null;

    long stream1Id;
    long stream2Id;

    @BeforeTest
    @Override
    public void before() {
        // Set the endpoint this test suite will be covering
        setEndpoint("ws/{workspaceId}/mediastreams");

        LOG.info("Using base endpoint: {}", getApiPath());

        // TODO: clean up anything inserted by previous test run
        //  not really a nice way to do this, for now have to delete at end by way of testing the delete
        //  endpoint, and if there's a fail, have to reset manually
        if(streamExists(MediaStreamServiceDataProvider.VALID_STREAM1_TITLE)) {
            // TODO: delete it
            LOG.info("Test stream is already in database");
        }

        if(streamExists(MediaStreamServiceDataProvider.VALID_STREAM1_TITLE2)) {
            // TODO: delete it
            LOG.info("Test stream2 is already in database");
        }
    }

    //private void deleteStream(String title)

    private boolean streamExists(String title) {

        try {
            MediaStreamResponse response = given().
                    header(getKeycloakHeader()).
                    contentType(ContentType.JSON).
                    pathParam("workspaceId", 1).
                    queryParam("title", MediaStreamServiceDataProvider.VALID_STREAM1_TITLE).
                    queryParam("streamurl", MediaStreamServiceDataProvider.VALID_STREAM1_URL).
                    when().
                    get(String.format("%s/{title}/{streamurl}", getApiPath())).
                    then().
                    contentType(ContentType.JSON).
                    statusCode(Status.OK.getStatusCode()).
                    extract().
                    as(MediaStreamResponse.class);

            if(response.getStreams().size() > 0) {
                return true;
            }

        } catch(Exception e) {
            return false;
        }

        return false;
    }


    @Test(testName = "testGet", description = "Attempts to streams",
            groups = {"read"}, dependsOnGroups = {"connectivity", "write"},
            dataProvider = "validMediaStreamProvider",
            dataProviderClass = MediaStreamServiceDataProvider.class)
    public void testGet(MediaStream stream) {
        String url = getApiPath();
        LOG.info("Using URL: GET {}", url);

        Response response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                when().
                get(url).
                then().
                contentType(ContentType.JSON).
                statusCode(Status.OK.getStatusCode()).
                // Expect the json to have a message value of Success
                        body("message", equalTo("OK"))
                .extract().response();

        LOG.info("Response: {}", response.asString());

        // TODO: process result and verify result stream

    }

    @Test(testName = "testFindByTitle", description = "Search streams",
            groups = {"read"}, dependsOnGroups = {"connectivity", "write"})
    public void testFind() {
        String url = getApiPath();

        MediaStreamResponse response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                queryParam("title", MediaStreamServiceDataProvider.VALID_STREAM1_TITLE).
                when().
                get(url).
                then().
                contentType(ContentType.JSON).
                statusCode(Status.OK.getStatusCode()).
                //body("message", equalTo("OK")).
                        extract().
                        as(MediaStreamResponse.class);
        //.response();

        LOG.info("Got stream count: {}", response.getStreams().size());
        List<MediaStream> streams = (List<MediaStream>) response.getStreams();
        stream1Id = streams.get(0).getMsid();
        // TODO: assert stream.get(0).getTitle().equals(VALID_STREAM1_TITLE)

        assertEquals(streams.size(), 1);
        assertEquals(streams.get(0).getTitle(), MediaStreamServiceDataProvider.VALID_STREAM1_TITLE);

        // TODO: process result and verify result stream

        // can also use "streams[0]" to get the first item?
        //List<JsonObject> jsonStreams = response.jsonPath().getList("streams");
        //LOG.info("Got stream?: {}", jsonStreams.toString());

        /*Gson gson = new GsonBuilder().create();
        HashMap<String, Object> stream = gson.fromJson(jsonStreams.get(0), HashMap<String, Object>.class);
        LOG.info("GOT stream with title: {}", stream.getTitle());*/
        // TODO: use JsonPath to extract response mediastream.streams
        //List<MediaStream> results = gson.fromJson(response.as)

        //LOG.info("Response {}", response.asString());

        MediaStreamResponse responseBoth = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                queryParam("title", MediaStreamServiceDataProvider.VALID_STREAM1_TITLE).
                queryParam("url", MediaStreamServiceDataProvider.VALID_STREAM1_URL).
                when().
                get(url).
                then().
                contentType(ContentType.JSON).
                statusCode(Status.OK.getStatusCode()).
                extract().
                as(MediaStreamResponse.class);

        List<MediaStream> bothStreams = (List<MediaStream>) responseBoth.getStreams();
        assertEquals(bothStreams.size(), 1);

        MediaStreamResponse responseFail = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                queryParam("title", "notitle").
                queryParam("url", MediaStreamServiceDataProvider.VALID_STREAM1_URL).
                when().
                get(url).
                then().
                contentType(ContentType.JSON).
                statusCode(Status.OK.getStatusCode()).
                extract().
                as(MediaStreamResponse.class);

        LOG.info("Got response: {}", responseFail.toString());

        List<MediaStream> failStreams = (List<MediaStream>) responseFail.getStreams();
        assertEquals(failStreams.size(), 0); // Should be no results... maybe API should send 404

    }

    @Test(testName = "testPost", description = "Attempts to post a valid stream",
            groups = {"write"}, dependsOnGroups = {"connectivity"},
            dataProvider = "validMediaStreamProvider",
            dataProviderClass = MediaStreamServiceDataProvider.class)
    public void testPost(MediaStream stream) {

        String url = getApiPath();
        LOG.info("Using endpoint: {}", url);

        MediaStreamResponse response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                body(stream).
                when().
                post(url).
                then().
                //contentType(ContentType.JSON).
                // Trouble with this status code line is it throws the fail exception w/o being able to read
                // the rest out? Possibly wrap in try/catch and see if we can get a full response to help
                // with reporting/debugging?
                        statusCode(Status.OK.getStatusCode()).
                // Expect the json to have a message value of Success
                        body("message", equalTo("Success"))
                .extract().as(MediaStreamResponse.class);


        MediaStream postedStream = ((List<MediaStream>) response.getStreams()).get(0);
        stream1Id = postedStream.getMsid();
        assertTrue(stream1Id > 0);

        // Can check Response for specific things, or parse response json body, etc
        //String responseString = response.asString();
        //LOG.info("Response: " + responseString);


    }

    @Test(testName = "testPostDuplicate", description = "Attempts to post a duplicate",
            groups = {"write"}, dependsOnGroups = {"connectivity"},
            dataProvider = "validMediaStreamProvider",
            dataProviderClass = MediaStreamServiceDataProvider.class)
    public void testPostDuplicate(MediaStream stream) {

        String url = getApiPath();
        LOG.info("Using endpoint: {}", url);

        Response response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                body(stream).
                when().
                post(url).
                then().
                contentType(ContentType.JSON).
                // Expecting error
                        statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode()).
                // Expect duplicate message content
                        body("message", containsString("already exists")).
                        extract().response();

    }

    @Test(testName = "testPost2", description = "Attempts to post a valid second stream",
            groups = {"write"}, dependsOnGroups = {"connectivity"},
            dataProvider = "validMediaStreamProvider2",
            dataProviderClass = MediaStreamServiceDataProvider.class)
    public void testPost2(MediaStream stream) {

        String url = getApiPath();
        LOG.info("Using endpoint: {}", url);

        MediaStreamResponse response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                body(stream).
                when().
                post(url).
                then().
                contentType(ContentType.JSON).
                statusCode(Status.OK.getStatusCode()).
                // Expect the json to have a message value of Success
                        body("message", equalTo("Success"))
                .extract().as(MediaStreamResponse.class);

        MediaStream resultStream = ((List<MediaStream>) response.getStreams()).get(0);
        stream2Id = resultStream.getMsid();
        assertTrue(stream2Id > 0);

    }

    // TODO: test get of title, should get 1 result

    // TODO: test get of title+url

    // TODO: test get of url


    @Test(testName = "testUpdate", description = "Tests updating values on a stream",
            groups = {"update"}, dependsOnGroups = {"connectivity", "write", "read"},
            dataProvider = "validMediaStreamProvider2",
            dataProviderClass = MediaStreamServiceDataProvider.class)
    private void testUpdate(MediaStream stream) {

        stream.setMsid(stream1Id);
        stream.setTitle("Stream 1 Renamed");

        MediaStreamResponse response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                pathParam("streamId", stream1Id).
                body(stream).
                when().
                put(String.format("%s/{streamId}", getApiPath())).
                then().
                statusCode(Status.OK.getStatusCode()).
                extract().as(MediaStreamResponse.class);

        MediaStream updatedStream = ((List<MediaStream>) response.getStreams()).get(0);
        assertEquals(updatedStream.getTitle(), stream.getTitle());
    }

    @Test(testName = "testDelete1", description = "Attempts to delete stream1",
            groups = {"delete"}, dependsOnGroups = {"connectivity", "write", "read", "update"})
    private void testDelete() {
        Response response = given().
                header(getKeycloakHeader()).
                pathParam("workspaceId", 1).
                pathParam("streamId", stream1Id).
                when().
                delete(String.format("%s/{streamId}", getApiPath())).
                then().
                statusCode(Status.OK.getStatusCode()).
                extract().response();

        LOG.info("Delete stream 1 response: {}", response.asString());

        Response response2 = given().
                header(getKeycloakHeader()).
                pathParam("workspaceId", 1).
                pathParam("streamId", stream2Id).
                when().
                delete(String.format("%s/{streamId}", getApiPath())).
                then().
                statusCode(Status.OK.getStatusCode()).
                extract().response();

        LOG.info("Delete stream 2 response: {}", response.asString());
    }


}
