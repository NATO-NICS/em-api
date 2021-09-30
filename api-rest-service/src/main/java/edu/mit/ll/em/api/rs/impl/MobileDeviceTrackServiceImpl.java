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
package edu.mit.ll.em.api.rs.impl;

import edu.mit.ll.em.api.dataaccess.EntityCacheMgr;
import edu.mit.ll.em.api.dataaccess.ICSDatastoreException;
import edu.mit.ll.em.api.rs.MDTrackServiceResponse;
import edu.mit.ll.em.api.rs.MobileDeviceTrackService;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.entity.MobileDeviceTrack;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.nicsdao.impl.MobileDeviceTrackDAOImpl;

import java.sql.Timestamp;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONException;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;


/**
 * MobileDeviceTrackServiceImpl implements the Mobile Device Track service. Receives tracks from mobile devices, and
 * persists them to a geospatial database table.
 */
public class MobileDeviceTrackServiceImpl implements MobileDeviceTrackService {

    /**
     * Logger instance.
     */
    private static final Logger LOG = LoggerFactory.getLogger(MobileDeviceTrackServiceImpl.class);

    /**
     * MDT DAO instance.
     */
    private static final MobileDeviceTrackDAOImpl mdtDao = new MobileDeviceTrackDAOImpl();

    /**
     * Instance of APIConfig.
     */
    private static final APIConfig config = APIConfig.getInstance();

    /**
     * The property key in core.properties that holds the timeThreshold value.
     */
    private static final String MDT_THRESHOLD_PROPERTY = "em.api.service.mdt.timeThreshold";

    /**
     * Default time threshold for how far in the future the track can be.
     */
    private static final Long DEFAULT_TIME_THRESHOLD = 1 * 60 * 1000L;

    /**
     * Threshold for how far in the future the track can be. If not set in the property, the default of
     * DEFAULT_TIME_THRESHOLD is used.
     */
    private Long timeThreshold;


    /**
     * Utility method to create an error response with the specified message and status.
     *
     * @param message the error message
     * @param status  the HTTP status response to set
     * @return
     */
    private Response makeErrorResponse(String message, Status status) {
        MDTrackServiceResponse mdTrackServiceResponse = new MDTrackServiceResponse();
        mdTrackServiceResponse.setMessage(message);
        return Response.ok(mdTrackServiceResponse).status(status).build();
    }

    @Override
    public Response postMDTrack(MobileDeviceTrack mobileDeviceTrack, Integer workspaceId, String validatedUsername) {
        MDTrackServiceResponse mdtrackResponse = new MDTrackServiceResponse();
        Response response;

        String verifyMsg;
        try {
            verifyMsg = verifySender(getUserByUsername(mobileDeviceTrack.getUsername()), validatedUsername);
            Response validateResponse = validateVerifyResponse(verifyMsg);
            if(validateResponse.getStatus() != Status.OK.getStatusCode()) {
                return validateResponse;
            }
        } catch(JSONException e) {
            return makeErrorResponse("Failed to process user for authentication", Status.UNAUTHORIZED);
        }

        try {
            // Set workspaceId, path param supersedes field value
            mobileDeviceTrack.setWorkspaceId(workspaceId);

            validateTrack(mobileDeviceTrack);

            // Persist the track
            boolean insertOrUpdateStatus = mdtDao.insertOrUpdate(mobileDeviceTrack);

            if(insertOrUpdateStatus) {
                mdtrackResponse.setMessage("Success");
                response = Response.ok(mdtrackResponse).status(Status.OK).build();
            } else {
                mdtrackResponse.setMessage("Fail. Unable to successfully insert or update MobileDeviceTrack");
                response = Response.ok(mdtrackResponse).status(Status.PRECONDITION_FAILED).build();
            }

        } catch(DuplicateKeyException e) {
            // TODO: shouldn't really have DAO specific failures in the API? Just want the generic DataAccessException,
            // but with a proper message?

            // TODO: if it exists, it should update... so why is this happening? It can happen when the query
            //       for an existing track fails, even though it exists, which makes it attempt an insert
            mdtrackResponse.setMessage("Fail. Track already exists");
            response = Response.ok(mdtrackResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(DataAccessException e) {
            mdtrackResponse.setMessage("Failed with DataAccessException: " + e.getMessage());
            response = Response.ok(mdtrackResponse).status(Status.INTERNAL_SERVER_ERROR).build();

            LOG.error("Error persisting MDT", e);
        } catch(Exception e) {

            mdtrackResponse.setMessage("Failed with exception: " + e.getMessage());
            response = Response.ok(mdtrackResponse).status(Status.INTERNAL_SERVER_ERROR).build();

            LOG.error("Exception persisting MDT: {}", e.getMessage(), e);
        }

        return response;

    }

    @Override
    public Response deleteMDTrack(String userId, String deviceId, Integer workspaceId, String username) {
        MDTrackServiceResponse mdtrackResponse = new MDTrackServiceResponse();
        Response response;

        String verifyMsg;
        try {
            verifyMsg = verifySender(getUserByUsername(userId), username);
            Response validateResponse = validateVerifyResponse(verifyMsg);
            if(validateResponse.getStatus() != Status.OK.getStatusCode()) {
                return validateResponse;
            }
        } catch(JSONException e) {
            return makeErrorResponse("Failed to process user for authorization", Status.FORBIDDEN);
        }

        try {
            boolean deleted = mdtDao.delete(deviceId, username, workspaceId);
            if(deleted) {
                mdtrackResponse.setMessage("Success");
                response = Response.ok(mdtrackResponse).status(Status.OK).build();
            } else {
                mdtrackResponse.setMessage("Failed. Matching MobileDeviceTrack not found to delete");
                response = Response.ok(mdtrackResponse).status(Status.NOT_FOUND).build();
            }
        } catch(DataAccessException e) {
            LOG.error("Error deleting track", e);
            mdtrackResponse.setMessage("Failed with DataAccessException: " + e.getMessage());
            response = Response.ok(mdtrackResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            LOG.error("Unhandled exception deleting track", e);
            mdtrackResponse.setMessage("Failed with Unhandled Exception: " + e.getMessage());
            response = Response.ok(mdtrackResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Helper method to parse the response from {@link MobileDeviceTrackServiceImpl#verifySender(User, String)}, and
     * build a Response object from the results.
     *
     * @param verifyMessage the return value from {@link MobileDeviceTrackServiceImpl#verifySender(User, String)}
     * @return a Response with success/fail information
     */
    private Response validateVerifyResponse(String verifyMessage) {
        Response response;
        MDTrackServiceResponse mdtrackResponse = new MDTrackServiceResponse();
        LOG.debug("Got verifyMsg: {}\n", verifyMessage);

        try {

            JSONObject verifyJson = new JSONObject(verifyMessage);
            if(verifyJson.getString("status").equals("success")) {
                LOG.debug("Got status SUCCESS: {}", verifyJson.getString("status"));
                mdtrackResponse.setMessage("Success");
                LOG.debug("Token identity matched NICS identity!");
                response = Response.ok(mdtrackResponse).status(Status.OK).build();
            } else if(verifyJson.getString("status").equals("fail")) {
                LOG.debug("Got status FAIL: {}", verifyJson.getString("status"));
                mdtrackResponse.setMessage(verifyJson.getString("message"));
                mdtrackResponse.setCount(0);
                response = Response.ok(mdtrackResponse).status(Status.EXPECTATION_FAILED).build();
            } else {
                mdtrackResponse.setMessage(verifyJson.getString("message"));
                response = Response.ok(mdtrackResponse).status(Status.EXPECTATION_FAILED).build();
                LOG.debug("Got unknown status: {}", verifyJson.getString("status"));
            }
        } catch(JSONException e) {
            mdtrackResponse.setMessage("Error processing Identity");
            mdtrackResponse.setCount(0);
            response = Response.ok(mdtrackResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Helper method to get User by username from the EntityCacheMgr.
     *
     * @param username the username of the user to fetch
     * @return the User if found, null otherwise
     */
    private User getUserByUsername(String username) {
        User user = null;
        try {
            user = EntityCacheMgr.getInstance().getUserEntityByUsername(username);
        } catch(ICSDatastoreException e1) {
            LOG.error("Datastore exception getting User entity with username {}", username, e1);
        }
        return user;
    }

    /**
     * Verifies the sender of the MDT by checking the identity in the cookie against the User that sent the MDT.
     *
     * @param user              the user that sent the MDT
     * @param validatedUsername the authenticated username
     * @return JSON with a 'message' and 'status' field where status is either 'success' or 'fail'
     *
     * @throws JSONException when there's an error processing the JSON response
     */
    private String verifySender(User user, String validatedUsername) throws JSONException {
        JSONObject ret = new JSONObject();

        if(user == null) {
            ret.put("message", "User not found");
            ret.put("status", "fail");
            return ret.toString();
        }

        String username = null;
        if(user.getUsername() != null) {
            username = user.getUsername();
        } else {
            ret.put("message", "Error getting user information from userId: " + user.getUserId());
            ret.put("status", "fail");
            return ret.toString();
        }

        try {
            if(validatedUsername != null && !validatedUsername.isEmpty() && validatedUsername.contains(username)) {
                // matches
                ret.put("message", "User's ID matches Identity");
                ret.put("status", "success");
            } else {
                /*ret.put("message", String.format("User's ID does not match identity. Sent token belongs to %s, but"
                        + " the userId maps to %s", validatedUsername, username));*/
                ret.put("message", "User's ID does not match identity");
                ret.put("status", "fail");
            }

            return ret.toString();

        } catch(Exception e) {
            ret.put("message", "Unhandled exception verifying identity: " + e.getMessage());
            ret.put("status", "fail");
        }

        return ret.toString();
    }

    /**
     * Calls isLocationInvalid and isTimeInvalid to check validity of track.
     *
     * @param mobileDeviceTrack the track to validate
     * @throws Exception if location or time is invalid TODO: create a specific exception?
     */
    private void validateTrack(MobileDeviceTrack mobileDeviceTrack) throws Exception {

        isLocationInvalid(mobileDeviceTrack);

        isTimeInvalid(mobileDeviceTrack.getTimestamp());
    }

    /**
     * Checks timestamp for being null, or too far into the future.
     *
     * @param incoming timestamp to validate
     * @return true if the timestamp isn't valid, false otherwise
     */
    private boolean isTimeInvalid(Timestamp incoming) throws Exception {
        if(incoming == null) {
            throw new Exception("Timestamp is null");
        }

        if(isTimeInFuture(incoming)) {
            throw new Exception("Timestamp is in the future!");
        }

        return false;
    }

    /**
     * Checks if the timestamp is more than a specified amount of time into the future.
     *
     * @param timestamp the timestamp to verify
     * @return true if the timestamp is in the future by more than the allowed threshold, false otherwise
     */
    private boolean isTimeInFuture(Timestamp timestamp) {

        long now = System.currentTimeMillis();
        long ts = timestamp.getTime();

        return ts > now && (ts - now) > getTimeThreshold();

    }

    /**
     * Simple checks to see whether or not the geometry is null or empty.
     *
     * @param mdt the MobileDeviceTrack with the location to check
     * @return true if the geometry is valid, false otherwise
     *
     * @throws Exception when lat or lon is null, or when lat or lon is out of bounds TODO: throw custom exception
     */
    private boolean isLocationInvalid(MobileDeviceTrack mdt) throws Exception {

        Double lat = mdt.getLatitude();
        Double lon = mdt.getLongitude();

        if(lat == null || lon == null) {
            throw new Exception("Latitude and Longitude must not be null");
        }

        if(lat < -90 || lat > 90) {
            throw new Exception("Latitude is out of bounds");
        }

        if(lon < -180 || lon > 180) {
            throw new Exception("Longitude is out of bounds");
        }

        return false;
    }

    /**
     * Gets the configured timeThreshold, or the default if the property is not set.
     *
     * @return the number of milliseconds into the future a track is allowed to be
     */
    private Long getTimeThreshold() {

        if(timeThreshold == null) {

            try {
                timeThreshold = config.getConfiguration().getLong(MDT_THRESHOLD_PROPERTY, DEFAULT_TIME_THRESHOLD);
            } catch(Exception e) {
                LOG.error("Failed reading property: ", MDT_THRESHOLD_PROPERTY, e);
                timeThreshold = DEFAULT_TIME_THRESHOLD;
            }
        }

        return timeThreshold;
    }

}