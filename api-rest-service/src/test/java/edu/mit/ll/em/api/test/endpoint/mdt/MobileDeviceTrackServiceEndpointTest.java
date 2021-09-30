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
package edu.mit.ll.em.api.test.endpoint.mdt;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;


import edu.mit.ll.em.api.test.base.EndpointTest;
import edu.mit.ll.em.api.test.dataprovider.MDtrackServiceDataProvider;
import edu.mit.ll.nics.common.entity.MobileDeviceTrack;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import javax.ws.rs.core.Response.Status;

import org.locationtech.jts.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;


/**
 * Tests for the MobileDeviceTrackService endpoint TODO:
 * <ul>
 *     <li>TODO: Can use gson/groovy dot(.) notation to access response json fields for validation</li>
 *     <li>TODO: Should more explicitly verify responses</li>
 *     <li>TODO: Expose a getMDT method on the endpoint to help test updates?</li>
 *     <li>TODO: Some tests not really meaningful since we can't reset the test data before we run</li>
 * </ul>
 */
@Test
public class MobileDeviceTrackServiceEndpointTest extends EndpointTest {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(MobileDeviceTrackServiceEndpointTest.class);


    @BeforeTest
    @Override
    public void before() {
        // Set the endpoint this test suite will be covering
        setEndpoint("mdtracks/{workspaceId}");

        LOG.info("Using base endpoint: {}", getApiPath());
    }


    @Test(testName = "addMdt",
            description = "Attempts to post a valid MDT",
            groups = {"write"}, dependsOnGroups = {"connectivity"},
            dataProvider = "validMdtProvider", dataProviderClass = MDtrackServiceDataProvider.class)
    public void testPostMdt(MobileDeviceTrack mdt) {
        String url = getApiPath();
        LOG.info("Using endpoint: {}", url);

        // Set username to same as idam
        mdt.setUsername(getKeycloakRemoteUsername());

        Response response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                body(mdt).
                when().
                post(url).
                then().
                contentType(ContentType.JSON).
                statusCode(Status.OK.getStatusCode()).
                // Expect the json to have a message value of Success
                        body("message", equalTo("Success"))
                .extract().response();

        // Can check Response for specific things, or parse response json body, etc
        String responseString = response.asString();
    }


    @Test(testName = "badUser",
            description = "Attempts to post an MDT with a username that doesn't exist (and won't match the idam token",
            groups = {"write"}, dependsOnGroups = {"connectivity"},
            dataProvider = "validMdtProvider", dataProviderClass = MDtrackServiceDataProvider.class)
    public void testAddBadUser(MobileDeviceTrack mdt) {

        // Set to username that doesn't exist, and won't match token
        mdt.setUsername("idontexist@p8iouqewoirjf.net");

        Response response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                body(mdt).
                when().
                post(getApiPath()).
                then().
                contentType(ContentType.JSON).
                statusCode(Status.EXPECTATION_FAILED.getStatusCode()).
                body("message", containsString("User not found")).
                extract().
                response();

        LOG.info("Responded with: {}", response.asString());

    }


    @Test(testName = "deleteTrack",
            description = "Attempts to delete a user's MDT",
            groups = {"delete"}, dependsOnGroups = {"connectivity", "write"},
            dataProvider = "validMdtProvider", dataProviderClass = MDtrackServiceDataProvider.class)
    public void testDeleteMdt(MobileDeviceTrack mdt) {

        mdt.setUsername(getKeycloakRemoteUsername());

        Response response = given().
                header(getKeycloakHeader()).
                contentType(ContentType.JSON).
                pathParam("workspaceId", 1).
                pathParam("userId", mdt.getUsername()).
                pathParam("deviceId", mdt.getDeviceId()).
                delete(String.format("%s/user/{userId}/device/{deviceId}", getApiPath()));

        LOG.info("testDelete: Status Code: {}", response.statusCode());
        Assert.equals(Status.OK.getStatusCode(), response.statusCode());

        Assert.equals(response.contentType(), ContentType.JSON.toString());



                /*then().
                contentType(ContentType.JSON).
                statusCode(Status.OK.getStatusCode()).
                //body("message", containsString("Error getting user")).
                extract().
                response();*/

        LOG.info("Responded with {}", response.asString());
    }

}