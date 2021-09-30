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
package edu.mit.ll.em.api.test.base;

import edu.mit.ll.em.api.util.APIConfig;

import io.restassured.RestAssured;


import static io.restassured.RestAssured.*;
import static io.restassured.specification.ProxySpecification.host;


import io.restassured.http.Cookie;

import io.restassured.http.Header;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.testng.annotations.*;

import javax.ws.rs.core.Response;


/**
 * Abstract class for endpoint tests to extend
 */
public abstract class EndpointTest {

    /**
     * Template for use in String.format for the API endpoint when it has a port specified
     */
    public static final String API_TEMPLATE_WITH_PORT = "%s://%s%s/%s/%s";

    /**
     * Template for use in String.format for the API endpoint when it DOES NOT have a port specified
     */
    public static final String API_TEMPLATE_WITHOUT_PORT = "%s://%s/%s/%s";

    /**
     * Default content-type, if not specified, for the IDAM authentication query. Usually json
     */
    public static final String OPENAM_CONTENT_TYPE_DEFAULT = "application/json";

    /**
     * Logger
     */
    protected static final Logger LOG = LoggerFactory.getLogger(EndpointTest.class);

    /**
     * Config instance
     */
    private static final APIConfig CONFIG = APIConfig.getInstance();

    /**
     * Protocol of the API endpoint<br/> Default: http
     */
    private String protocol = "http";

    /**
     * host of the API endpoint, just the domain name, like "localhost" or "nics-data.ll.mit.edu"
     */
    private String host;

    /**
     * Port of the API endpoint
     */
    private String port;

    /**
     * Root of the API endpoint<br/> Default: em-api/v1
     */
    private String apiRoot = "em-api/v1";

    /**
     * Endpoint you're testing
     */
    private String endpoint;

    /**
     * OpenAM properties. See {@link EndpointTest#initOpenAm(boolean, String, String, String, String, String, String,
     * String, String)} for details.
     */
    private boolean openamRequired;
    private String openamAuthUrl;
    private String openamCookieName;
    private String openamResponseTokenKey;
    private String openamUsernameHeader;
    private String openamPasswordHeader;
    private String openamContentType;
    private String openamUsername;
    // TODO: Not secure holding onto this password, but not a big deal being it's local tests?
    // TODO: should only use a test user with limited permissions
    private String openamPassword;

    private String keycloakRemoteUsername;
    private String keycloakRemoteUserHeader;

    /**
     * The token content received from IDAM
     */
    protected String sessionToken;

    /**
     * A Cookie with the configured cookie name and token value
     */
    protected Cookie idamCookie;

    /**
     * Whether or not the proxy is to be turned on
     */
    private boolean proxyEnabled;

    /**
     * The proxy scheme: http or https
     */
    private String proxyScheme;

    /**
     * The proxy host
     */
    private String proxyHost;

    /**
     * The proxy port
     */
    private int proxyPort;

    /**
     * Username for proxy if authentication is required
     */
    private String proxyUsername;

    /**
     * Password for proxy if authentication is required
     */
    private String proxyPassword;

    /**
     * Header object built off the specified IDAM user header key and username
     */
    private Header header;


    /**
     * Initializes the proxy with the configured settings in testng.xml
     *
     * @param proxyEnabled  whether or not to enable the proxy
     * @param proxyScheme   the proxy protocol, http or https
     * @param proxyHost     the proxy host
     * @param proxyPort     the proxy port, must be specified
     * @param proxyUsername proxy username, only used if both username and password are set
     * @param proxyPassword proxy password, only used if both username and password are set
     */
    @BeforeTest
    @Parameters({"proxyEnabled", "proxyScheme", "proxyHost", "proxyPort", "proxyUsername", "proxyPassword"})
    public void initProxy(boolean proxyEnabled, String proxyScheme, String proxyHost, int proxyPort,
                          String proxyUsername, String proxyPassword) {

        setProxyEnabled(proxyEnabled);
        setProxyScheme(proxyScheme);
        setProxyHost(proxyHost);
        setProxyPort(proxyPort);
        setProxyUsername(proxyUsername);
        setProxyPassword(proxyPassword);

        if(!proxyEnabled) {
            LOG.debug("Proxy NOT being enabled");
            return;
        }

        boolean useProxyAuth = false;
        if(StringUtils.isNotEmpty(proxyUsername) && StringUtils.isNotEmpty(proxyPassword)) {
            useProxyAuth = true;
        }

        if(StringUtils.isNotEmpty(proxyScheme)) {
            if(!(proxyScheme.equalsIgnoreCase("http") ||
                    proxyScheme.equalsIgnoreCase("https"))) {

                // Unsupported, default to http
                proxyScheme = "http";
            }
        }

        RestAssured.proxy = host(proxyHost);

        if(proxyPort != 0) {
            RestAssured.proxy = RestAssured.proxy.and().withPort(proxyPort);
            // TODO: If you don't specify a port, RestAssured defaults it to 8888... have to look into how
            //  to NOT have to specify a port, can also do RestAssured.proxy("http://llproxy.llan.ll.mit.edu:8080");
            //  Also, proxyPort of "" in testng.xml breaks testing with NumberFormatException
        }

        if(useProxyAuth) {
            RestAssured.proxy = RestAssured.proxy.and().withAuth(proxyUsername, proxyPassword);
        }

        if(proxyScheme.equalsIgnoreCase("https")) {
            RestAssured.proxy = RestAssured.proxy.and().withScheme(proxyScheme);
        }

    }

    /**
     * Configures properties for API endpoint: [protocol]:\\[host]:[port][apiRoot] <br/> If left empty in testng.xml,
     * the default is used: http://localhost:8080/em-api/v1
     *
     * @param protocol the protocol the API is hosted on, generally http or https
     * @param host     the host the API is on, e.g. "localhost", or "nics-data.nics.ll.mit.edu"
     * @param port     the port the API is hosted on
     * @param apiRoot  the root of the API application, usually "/em-api/v1"
     */
    @BeforeTest
    @Parameters({"protocol", "host", "port", "apiRoot"})
    public void initHost(String protocol, String host, String port, String apiRoot) {

        if(StringUtils.isNotEmpty(protocol)) {
            setProtocol(protocol);
        }

        if(StringUtils.isNotEmpty(host)) {
            setHost(host);
        }

        if(StringUtils.isNotEmpty(port)) {
            setPort(port);
        }

        if(StringUtils.isNotEmpty(apiRoot)) {
            setApiRoot(apiRoot);
        }
    }


    /**
     * Authenticates with OpenAM to receive a token to use on requests to the API. The parameters are configured in the
     * testng.xml file.
     * <p>
     * TODO: Could make this more generic to be an IDAM config, but aside from making it work for openam and keycloak,
     * others may differ enough that it's not flexible enough
     *
     * @param openamAuthUrl          the json authenticate endpoint in openam
     * @param openamCookieName       the name of the cookie the token needs to be set to
     * @param openamResponseTokenKey the key of the token in the json response
     * @param openamUsernameHeader   the header openam gets the username from
     * @param openamPasswordHeader   the header openam gets the password from
     * @param openamContentType      the content-type of the request, just json in openam's case
     * @param openamUsername         an enabled user in openam and in the NICS instance being tested
     * @param openamPassword         the password for the user in openamUsername
     */
    @BeforeTest
    @Parameters({"openamRequired", "openamAuthUrl", "openamCookieName", "openamResponseTokenKey",
            "openamUsernameHeader",
            "openamPasswordHeader", "openamContentType", "openamUsername", "openamPassword"})
    public void initOpenAm(boolean openamRequired, String openamAuthUrl, String openamCookieName,
                           String openamResponseTokenKey,
                           String openamUsernameHeader, String openamPasswordHeader,
                           String openamContentType, String openamUsername, String openamPassword) {

        LOG.info("Initializing OpenAM");

        setOpenamRequired(openamRequired);

        if(!openamRequired) {
            LOG.info("NOTE: openamRequired is set to false, so not getting an auth token");
            return;
        }

        setOpenamAuthUrl(openamAuthUrl);
        setOpenamCookieName(openamCookieName);
        setOpenamResponseTokenKey(openamResponseTokenKey);
        setOpenamUsernameHeader(openamUsernameHeader);
        setOpenamPasswordHeader(openamPasswordHeader);
        setOpenamContentType(openamContentType);
        setOpenamUsername(openamUsername);
        setOpenamPassword(openamPassword);


        // /mdtracks requires IDAM authentication via a token placed in a Cookie, so
        // we get the token for all the tests to use here
        String token = given().
                headers(openamUsernameHeader, openamUsername,
                        openamPasswordHeader, openamPassword,
                        "Content-Type", (openamContentType == null) ? OPENAM_CONTENT_TYPE_DEFAULT : openamContentType).
                post(openamAuthUrl).asString();

        if(token != null) {
            try {
                JSONObject tokenResponse = new JSONObject(token);
                if(tokenResponse != null) {
                    sessionToken = tokenResponse.getString(openamResponseTokenKey);
                    idamCookie = new Cookie.Builder(openamCookieName, sessionToken).build();
                }
            } catch(JSONException e) {
                LOG.error("Failed to get idam token!: ", e.getMessage());
            }
        }
    }

    /**
     * TODO: implement for keycloak
     * <p>
     * TODO: Could make this more generic to be an IDAM config, but aside from making it work for openam and keycloak,
     * others may differ enough that it's not flexible enough
     *
     * @param keycloakAuthUrl          the json authenticate endpoint in keycloak
     * @param keycloakCookieName       the name of the cookie the token needs to be set to
     * @param keycloakResponseTokenKey the key of the token in the json response
     * @param keycloakUsernameHeader   the header keycloak gets the username from
     * @param keycloakPasswordHeader   the header keycloak gets the password from
     * @param keycloakContentType      the content-type of the request, just json in keycloak's case
     * @param keycloakUsername         an enabled user in keycloak and in the NICS instance being tested
     * @param keycloakPassword         the password for the user in keycloakUsername
     */
    @BeforeTest
    @Parameters({"keycloakRequired", "keycloakAuthUrl", "keycloakCookieName", "keycloakResponseTokenKey",
            "keycloakUsernameHeader",
            "keycloakPasswordHeader", "keycloakContentType", "keycloakUsername", "keycloakPassword",
            "keycloakRemoteUserHeader", "keycloakRemoteUser"})
    public void initKeycloak(boolean keycloakRequired, String keycloakAuthUrl, String keycloakCookieName,
                             String keycloakResponseTokenKey,
                             String keycloakUsernameHeader, String keycloakPasswordHeader,
                             String keycloakContentType, String keycloakUsername, String keycloakPassword,
                             String keycloakRemoteUserHeader, String keycloakRemoteUser) {


        // TODO: maybe implement getting tokens/permissions and all of that, but for now just hitting localhost, just
        //  set the configured username

        //idamCookie = new Cookie.Builder("X-Remote-User", keycloakRemoteUser).build();
        this.keycloakRemoteUserHeader = keycloakRemoteUserHeader;
        this.keycloakRemoteUsername = keycloakRemoteUser;
    }

    /**
     * Helper for getting header with keycloak remote user header and value
     *
     * @return
     */
    public Header getKeycloakHeader() {

        if(header == null) {
            header = new Header(getKeycloakRemoteUserHeader(), getKeycloakRemoteUsername());
        }

        return header;
    }

    /**
     * Can be overridden to do specific initialization necessary for tests
     */
    @BeforeTest
    public void before() {
        LOG.info("BEFORE tests. Anything else to initialize here?");
    }

    /**
     * Can be overridden to do specific clean up after a test
     */
    @AfterTest
    public void after() {
        LOG.info("After tests, any cleanup?");
    }

    @Test(testName = "testApiConnection",
            description = "Simple connectivity test to the workspace endpoint to see if the API is reachable",
            groups = {"connectivity"})
    public void testApiConnectivity() {
        //String url = buildAlternateApiPath(String.format("/workspace/system/%s", getHost()));
        String url = buildAlternateApiPath("/workspace");
        LOG.info("Using alternate API: {}", url);

        given().
                when().
                get(url).
                then().
                statusCode(Response.Status.OK.getStatusCode());
    }

    /**
     * Get's the API endpoint as configured
     *
     * @return a string containing the configured API
     */
    public String getApiPath() {

        if(port == null || port.isEmpty()) {
            return String.format(API_TEMPLATE_WITHOUT_PORT, protocol, host, apiRoot, endpoint);
        } else {
            return String.format(API_TEMPLATE_WITH_PORT, protocol, host, ":" + port, apiRoot, endpoint);
        }
    }

    /**
     * Gets the API endpoint as configured, except accepts a custom endpoint component, which will be placed after the
     * apiRoot: protocol://host[:port]/apiRoot/ENDPOINT
     *
     * @param endpoint the endpoint path to add on to the base API components
     * @return a string containing the configured API, but with an alternate endpoint as specified
     */
    public String buildAlternateApiPath(String endpoint) {

        if(port == null || port.isEmpty()) {
            return String.format(API_TEMPLATE_WITHOUT_PORT, protocol, host, apiRoot, removeSlashes(endpoint));
        } else {
            return String.format(API_TEMPLATE_WITH_PORT, protocol, host, ":" + port, apiRoot, removeSlashes(endpoint));
        }
    }

    /**
     * Removes preceding and trailing slashes from a String, leaves any slashes between text intact
     *
     * @param component the string to removed slashes from
     * @return a version of the original with preceding and trailing slashes removed
     */
    private String removeSlashes(String component) {

        if(StringUtils.isEmpty(component)) {
            return null;
        }

        if(component.startsWith("/")) {
            component = component.replaceFirst("/", "");
        }

        if(component.endsWith("/")) {
            component = component.substring(0, component.length() - 1);
        }

        return component;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getApiRoot() {
        return apiRoot;
    }

    public void setApiRoot(String apiRoot) {
        this.apiRoot = removeSlashes(apiRoot);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = removeSlashes(endpoint);
    }

    public static Configuration getCONFIG() {
        return CONFIG.getConfiguration();
    }

    public boolean isOpenamRequired() {
        return openamRequired;
    }

    public void setOpenamRequired(boolean openamRequired) {
        this.openamRequired = openamRequired;
    }

    public String getOpenamAuthUrl() {
        return openamAuthUrl;
    }

    public void setOpenamAuthUrl(String openamAuthUrl) {
        this.openamAuthUrl = openamAuthUrl;
    }

    public String getOpenamCookieName() {
        return openamCookieName;
    }

    public void setOpenamCookieName(String openamCookieName) {
        this.openamCookieName = openamCookieName;
    }

    public String getOpenamResponseTokenKey() {
        return openamResponseTokenKey;
    }

    public void setOpenamResponseTokenKey(String openamResponseTokenKey) {
        this.openamResponseTokenKey = openamResponseTokenKey;
    }

    public String getOpenamUsernameHeader() {
        return openamUsernameHeader;
    }

    public void setOpenamUsernameHeader(String openamUsernameHeader) {
        this.openamUsernameHeader = openamUsernameHeader;
    }

    public String getOpenamPasswordHeader() {
        return openamPasswordHeader;
    }

    public void setOpenamPasswordHeader(String openamPasswordHeader) {
        this.openamPasswordHeader = openamPasswordHeader;
    }

    public String getOpenamContentType() {
        return openamContentType;
    }

    public void setOpenamContentType(String openamContentType) {
        this.openamContentType = openamContentType;
    }

    public String getOpenamUsername() {
        return openamUsername;
    }

    public void setOpenamUsername(String openamUsername) {
        this.openamUsername = openamUsername;
    }

    public String getOpenamPassword() {
        return openamPassword;
    }

    public void setOpenamPassword(String openamPassword) {
        this.openamPassword = openamPassword;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyScheme() {
        return proxyScheme;
    }

    public void setProxyScheme(String proxyScheme) {
        this.proxyScheme = proxyScheme;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getKeycloakRemoteUsername() {
        return keycloakRemoteUsername;
    }

    public void setKeycloakRemoteUsername(String keycloakRemoteUsername) {
        this.keycloakRemoteUsername = keycloakRemoteUsername;
    }

    public String getKeycloakRemoteUserHeader() {
        return keycloakRemoteUserHeader;
    }

    public void setKeycloakRemoteUserHeader(String keycloakRemoteUserHeader) {
        this.keycloakRemoteUserHeader = keycloakRemoteUserHeader;
    }
}
