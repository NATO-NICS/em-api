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
package edu.mit.ll.em.api.test.endpoint.example;


import io.restassured.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.restassured.response.Response;


import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import static org.testng.Assert.*;


import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;


import static javax.ws.rs.core.Response.Status;


import edu.mit.ll.em.api.test.base.EndpointTest;
import edu.mit.ll.em.api.test.dataprovider.ExampleEndpointTestDataProvider;


/**
 * Exercises the EXAMPLE endpoint.
 * <p>
 * This class is meant to be a jumping off point for adding new tests. Can copy/paste/edit for new endpoints.
 */
@Test
public class ExampleEndpointTest extends EndpointTest {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(ExampleEndpointTest.class);


    /**
     * Optionally override for before test initialization
     */
    @BeforeTest
    @Override
    public void before() {

        LOG.info("Overriding before for my own use");

        // Set the endpoint you're testing
        setEndpoint("example");
    }

    /**
     * Optionally override for post test cleanup
     */
    @AfterTest
    @Override
    public void after() {
        LOG.info("Overriding after for my own use");
    }


    @Test(testName = "google", description = "Tests connection to google", enabled = false)
    public void testGoogle() {
        // Requires useProxy to be true in testng.xml OR to specify it on a request
        given().
                //.proxy(getProxyHost()).with().port(getProxyPort())
                        get("http://google.com").
                then().
                statusCode(200);
    }


    /**
     * Example get test
     */
    @Test(testName = "testGetExample",
            description = "Example test of getting",
            groups = {"get"},
            dependsOnGroups = {"connectivity"})
    public void testGetExample() {
        Response response =
                given().
                        when().
                        // Depending on settings, should return http://localhost:8080/em-api/v1/example
                                get(getApiPath()).
                        then().
                        contentType(ContentType.JSON).
                        statusCode(Status.OK.getStatusCode()).
                        extract().response();

        // Assert this way, too
        assertNotNull(response);
        assertEquals(response.statusCode(), Status.OK.getStatusCode());

    }


    /**
     * Example post test. Uses ExampleEndpointTestDataProvider
     */
    @Test(testName = "testPostExample",
            description = "Example test of posting",
            groups = {"post"},
            dependsOnGroups = {"connectivity"},
            dataProvider = "exampleDataProvider", dataProviderClass = ExampleEndpointTestDataProvider.class)
    public void testPostExample(String example) {

        Response response =
                given().
                        body("{\"somedata\":\"someval\"}").
                        when().
                        post(getApiPath()).
                        then().
                        contentType(ContentType.JSON).
                        statusCode(Status.OK.getStatusCode()).
                        body("somejsonkey", equalTo("somejsonvalue")).
                        extract().
                        response();

        // Assert some stuff this way, too
        assertNotNull(response);
        assertEquals(response.statusCode(), Status.OK.getStatusCode());
    }

}
