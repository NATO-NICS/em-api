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

import edu.mit.ll.nics.nicsdao.mappers.CollabRoomPermissionRowMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.rabbitmq.client.AlreadyClosedException;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import edu.mit.ll.em.api.exception.DuplicateCollabRoomException;
import edu.mit.ll.em.api.rs.CollabRoomPermissionResponse;
import edu.mit.ll.em.api.rs.CollabService;
import edu.mit.ll.em.api.rs.CollabServiceResponse;
import edu.mit.ll.em.api.rs.CollabPresenceStatus;
import edu.mit.ll.em.api.rs.FieldMapResponse;
import edu.mit.ll.em.api.rs.GeoserverUtil;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.CollabRoom;
import edu.mit.ll.nics.nicsdao.impl.CollabRoomDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;

/**
 *
 */
public class CollabServiceImpl implements CollabService {

    /**
     * CollabRoom DAO
     */
    private static final CollabRoomDAOImpl collabDao = new CollabRoomDAOImpl();

    /**
     * User DAO
     */
    private static final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();
    private static final UserDAOImpl userDao = new UserDAOImpl();
    private static final OrgDAOImpl orgDao = new OrgDAOImpl();
    private static final IncidentDAOImpl incidentDao = new IncidentDAOImpl();

    private static final Logger log = LoggerFactory.getLogger(CollabServiceImpl.class);

    private RabbitPubSubProducer rabbitProducer;

    private static final int EXPECTED_ROOMS = 20;
    private static final int EXPECTED_PARTICIPANTS = 20;
    private static final String SECURE_ROOMS_ERROR = "One or more users failed to be added to the collaboration room";
    private static final String UPDATE_ROOM_NAME_ERROR = "There was an error updating the collaboration room name.";
    private ConcurrentMap<Integer, ConcurrentMap<String, CollabPresenceStatus>> statuses =
            new ConcurrentHashMap<Integer, ConcurrentMap<String, CollabPresenceStatus>>(EXPECTED_ROOMS);

    private static final String DATA_ACCESS_ERROR = "Data Access Error";
    private static final String UNHANDLED_EXCEPTION = "Unhandled Exception";
    private static final String ACCESS_DENIED = "Access Denied";
    private static final String UNSECURE_ERROR = "There was an error unsecuring the room.";
    private static final String RENAME_MESSAGE_NOT_SENT = "There was an error notifying users of collaboration room name change.";

    private static final String LAYER_CREATION_ERROR =
            "There was an error creating a layer in geoserver for the new collaboration room.";

    /**
     * Gets collab rooms accessible by the specified user on the specified incident
     *
     * @param incidentId
     * @param userId
     * @return Response
     *
     * @see CollabServiceResponse
     */
    public Response getCollabRoom(int incidentId, Integer userId, String username) {
        String incidentMap = APIConfig.getInstance().getConfiguration().getString(
                APIConfig.INCIDENT_MAP, SADisplayConstants.INCIDENT_MAP);

        Response response = null;
        CollabServiceResponse collabResponse = new CollabServiceResponse();

        if(!userDao.validateUser(username, userId)) {
            return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
        }

        try {
            List<Integer> adminRooms = new ArrayList<Integer>();
            List<CollabRoom> secureRooms = collabDao.getSecuredRooms(userId, incidentId, incidentMap);

            List<CollabRoom> collabRooms = collabDao.getAccessibleCollabRooms(userId, incidentId, incidentMap);

            for(CollabRoom room : secureRooms) {
                int roleId = collabDao.getCollabRoomSystemRole(room.getCollabRoomId(), userId);
                if(room.getName().equalsIgnoreCase(incidentMap)) {
                    if(roleId == SADisplayConstants.USER_ROLE_ID) {
                        room.setReadWriteUsers(Arrays.asList(userId));
                    } else if(roleId == SADisplayConstants.READ_ONLY_ROLE_ID) {
                        room.setReadOnlyUsers(Arrays.asList(userId));
                    } else if(roleId == SADisplayConstants.ADMIN_ROLE_ID) {
                        room.setAdminUsers(Arrays.asList(userId));
                        adminRooms.add(room.getCollabRoomId());
                    }
                    room.setIncidentMapAdmins(incidentDao.getIncidentMapAdmins(incidentId, incidentMap));
                    collabRooms.add(0, room);
                } else {
                    if(roleId == SADisplayConstants.ADMIN_ROLE_ID) {
                        adminRooms.add(room.getCollabRoomId());
                    } else if(roleId == SADisplayConstants.USER_ROLE_ID) {
                        room.setReadWriteUsers(Arrays.asList(userId));
                    } else if(roleId == SADisplayConstants.READ_ONLY_ROLE_ID) {
                        room.setReadOnlyUsers(Arrays.asList(userId));
                    }
                    collabRooms.add(room);
                }
            }

            collabResponse.setAdminRooms(adminRooms);
            collabResponse.setMessage(Status.OK.getReasonPhrase());
            collabResponse.setResults(collabRooms);
            collabResponse.setCount(collabRooms.size());
            response = Response.ok(collabResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            collabResponse.setMessage(DATA_ACCESS_ERROR);
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            collabResponse.setMessage(UNHANDLED_EXCEPTION);
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    // Not an endpoint. Method used by other EM-API classes to create a room
    public Response createCollabRoomWithPermissions(int incidentId, int orgId, int workspaceId, CollabRoom collabroom) {
        return this.postCollabRoomWithPermissions(incidentId, orgId, workspaceId, collabroom);
    }

    // Not an endpoint. Method used by other EM-API classes to create a room
    public CollabRoom createUnsecureCollabRoom(int incidentId, CollabRoom collabroom)
            throws DataAccessException, DuplicateCollabRoomException, Exception {

        CollabRoom newCollabRoom = this.createCollabRoom(incidentId, collabroom);
        newCollabRoom.setIncident(collabroom.getIncident());

        boolean layerCreated = this.createGeoserverLayer(newCollabRoom);
        if(!layerCreated) {
            log.warn("Problem creating layer for room {}", collabroom.getName());
        }

        return newCollabRoom;
    }

    /**
     * Creates a collaboration room.
     *
     * @param userOrgId the id of the userorg of the requesting user, used to check permissions, must be
     *                  admin or super to create a secure room
     * @param orgId TODO: Not used, so remove it?
     * @param workspaceId the id of the workspace this collabroom is to be created in
     * @param incidentId the id of the incident the collabroom belongs to
     * @param collabroom the collabroom entity to create
     * @param username the identity of the user making the request
     *
     * @return a Response containing the CollabRoom entity that was created if successful,
     *         or an appropriate error response if not
     */
    public Response postCollabRoom(int userOrgId, int orgId,
                                   int workspaceId, int incidentId, CollabRoom collabroom, String username) {
        Response response = null;
        CollabServiceResponse collabResponse = new CollabServiceResponse();
        CollabRoom newCollabRoom = null;

        if(collabroom.getAdminUsers() != null &&
                collabroom.getAdminUsers().size() > 0) {
            //User the username attached to the token to find the system role Id
            int systemRoleId = userOrgDao.getSystemRoleIdForUserOrg(username, userOrgId);
            if(systemRoleId == SADisplayConstants.ADMIN_ROLE_ID ||
                    systemRoleId == SADisplayConstants.SUPER_ROLE_ID) {
                return this.postCollabRoomWithPermissions(incidentId, orgId, workspaceId, collabroom);
            } else {
                collabResponse.setMessage(ACCESS_DENIED);
                response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                return response;
            }
        }

        try {
            newCollabRoom = this.createCollabRoom(incidentId, collabroom);

            //Not adding the incident object to the response from the DB
            //Use incident past from the UI
            newCollabRoom.setIncident(collabroom.getIncident());

            boolean layerCreated = this.createGeoserverLayer(newCollabRoom);

            if(!layerCreated) {
                collabResponse.setMessage(LAYER_CREATION_ERROR);
            } else {
                collabResponse.setMessage(Status.OK.getReasonPhrase());
            }

            collabResponse.setResults(Arrays.asList(newCollabRoom));
            collabResponse.setCount(1);
            response = Response.ok(collabResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            e.printStackTrace();
            collabResponse.setMessage(DATA_ACCESS_ERROR);
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(DuplicateCollabRoomException dupe) {
            dupe.printStackTrace();
            collabResponse.setMessage(dupe.getMessage());
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            e.printStackTrace();
            collabResponse.setMessage(UNHANDLED_EXCEPTION);
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                notifyChange(newCollabRoom);
            } catch(IOException | TimeoutException | AlreadyClosedException e) {
                log.error("Failed to publish CollabServiceImpl collabroom event", e);
            } catch(Exception e) {
                log.error("Failed to publish CollabServiceImpl collabroom event", e);
            }
        }

        return response;
    }

    /**
     * Same operation as CollabServiceImpl#postCollabRoom, except it takes a collection of
     * CollabRoom entities to create, and holds off sending notification until the end in
     * a batch notification.
     *
     * @return a Response containing all the successfull collabrooms created if successful,
     *          otherwise an appropriate error message
     * 
     * @see CollabServiceImpl#postCollabRoom(int, int, int, int, CollabRoom, String) 
     * 
     */
    @Override
    public Response postCollabRooms(int userOrgId, int workspaceId, int incidentId,
                                    List<CollabRoom> collabrooms, String username) {

        Response response = null;
        Collection<CollabRoom> createdRooms = new ArrayList<>();
        CollabServiceResponse collabResponse = new CollabServiceResponse();

        if(collabrooms == null || collabrooms.isEmpty()) {
            collabResponse.setMessage("Payload contains no collabrooms");
            return Response.ok(collabResponse).status(Status.BAD_REQUEST).build();
        }

        int numberOfRooms = collabrooms.size();

        CollabRoom newCollabRoom = null;
        boolean hadException = false;
        boolean createdSecure = false;
        Response postCollabRoomWithPermissionsResponse;
        List<Response> responses = new ArrayList<>();

        for(CollabRoom collabroom : collabrooms) {
            createdSecure = false;
            postCollabRoomWithPermissionsResponse = null;
            response = null;

            try {
                if(collabroom.getAdminUsers() != null && collabroom.getAdminUsers().size() > 0) {
                    int systemRoleId = userOrgDao.getSystemRoleIdForUserOrg(username, userOrgId);
                    if(systemRoleId == SADisplayConstants.ADMIN_ROLE_ID ||
                            systemRoleId == SADisplayConstants.SUPER_ROLE_ID) {

                        // TODO: planning to make a version of this call that skips the notification
                        postCollabRoomWithPermissionsResponse = this.postCollabRoomWithPermissions(incidentId,
                                -1, workspaceId, collabroom, false /* DON'T send notification */);

                        // TODO: extract room out of this response?
                        CollabRoomPermissionResponse collabServiceResponse = (CollabRoomPermissionResponse) postCollabRoomWithPermissionsResponse
                                .getEntity();

                        Collection<CollabRoom> results = (Collection<CollabRoom>)collabServiceResponse.getResults();
                        createdRooms.addAll(results);

                        responses.add(postCollabRoomWithPermissionsResponse);
                        createdSecure = true;

                        // TODO: handle response, maybe put in list as we loop
                    } else {
                        collabResponse.setMessage(ACCESS_DENIED);
                        response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                        responses.add(response);
                        // TODO: handle response, hold on to it
                        continue;
                    }
                }

                if(createdSecure) {
                    // TODO: any reason to not just continue after creation where it returned previously?
                    continue;
                }

                // Room wasn't secured, so create an unsecure room
                try {
                    // TODO: create version that doesn't send notification
                    newCollabRoom = this.createCollabRoom(incidentId, collabroom);

                    // Not adding the incident object to the response from the DB
                    // Use incident passed from the UI
                    newCollabRoom.setIncident(collabroom.getIncident());

                    boolean layerCreated = this.createGeoserverLayer(newCollabRoom);

                    if(!layerCreated) {
                        collabResponse.setMessage(LAYER_CREATION_ERROR);
                    } else {
                        collabResponse.setMessage(Status.OK.getReasonPhrase());
                    }

                    collabResponse.setResults(Arrays.asList(newCollabRoom));
                    createdRooms.add(newCollabRoom);
                    collabResponse.setCount(1);
                    response = Response.ok(collabResponse).status(Status.OK).build();
                } catch(DataAccessException e) {
                    e.printStackTrace();
                    collabResponse.setMessage(DATA_ACCESS_ERROR);
                    response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                } catch(DuplicateCollabRoomException dupe) {
                    dupe.printStackTrace();
                    collabResponse.setMessage(dupe.getMessage());
                    response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                } catch(Exception e) {
                    e.printStackTrace();
                    collabResponse.setMessage(UNHANDLED_EXCEPTION);
                    response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                }

                responses.add(response);



                // TODO: maybe didn't need to hold onto response earlier if it's only notified
                //  here...
                /*if(Status.OK.getStatusCode() == response.getStatus()) {
                    try {
                        notifyChange(newCollabRoom);
                    } catch(IOException | TimeoutException | AlreadyClosedException e) {
                        log.error("Failed to publish CollabServiceImpl collabroom event", e);
                    } catch(Exception e) {
                        log.error("Failed to publish CollabServiceImpl collabroom event", e);
                    }
                }*/



            } catch(Exception e) {
                hadException = true;
                log.error("Exception while creating collabroom in batch add collabroom", e);
                // TODO: hold onto error
            }

            // TODO: call method to take responses and do a batch notification
            // TODO: build response with basics of what happened, successfully created rooms,
            //  failed rooms, unauthorized, etc

        }

        if(createdRooms.size() > 0) {
            // send out for mass notification? Can we even? Or does it go to ONLY org topics :/
            try {
                batchNotify(incidentId, createdRooms);
            } catch(IOException e) {
                e.printStackTrace();
            } catch(TimeoutException e) {
                e.printStackTrace();
            }
        }

        String message;
        if(createdRooms.size() > 0) {
            message = String.format("Created %d of %d rooms",createdRooms.size(), numberOfRooms);
        } else {
            message = "Created no rooms.";
        }

        if(hadException) {
            message += " There were exceptions processing the rooms.";
        }

        collabResponse.setMessage(message);
        collabResponse.setCount(createdRooms.size());
        collabResponse.setResults(createdRooms);

        return Response.ok(collabResponse).status(Status.OK).build();
    }


    public Response postCollabRoomWithPermissions(int incidentId, int orgId, int workspaceId,
                                                  CollabRoom collabroom, boolean notify) {

        Response response = null;
        CollabRoom newCollabRoom = null;
        CollabRoomPermissionResponse collabResponse = new CollabRoomPermissionResponse();
        try {
            newCollabRoom = this.createCollabRoom(incidentId, collabroom);

            collabResponse =
                    this.secureRoom(newCollabRoom.getCollabRoomId(),
                            orgId, workspaceId,
                            collabroom.getAdminUsers(), collabroom.getReadWriteUsers(),
                            collabroom.getReadOnlyUsers());

            newCollabRoom.setAdminUsers(collabResponse.getAdminUsers());
            newCollabRoom.setReadWriteUsers(collabResponse.getReadWriteUsers());
            newCollabRoom.setReadOnlyUsers(collabResponse.getReadOnlyUsers());

            newCollabRoom.setIncident(collabroom.getIncident());

            boolean layerCreated = this.createGeoserverLayer(newCollabRoom);

            if(!layerCreated) {
                collabResponse.setMessage(LAYER_CREATION_ERROR);
            } else {
                collabResponse.setMessage(Status.OK.getReasonPhrase());
            }
            collabResponse.setResults(Arrays.asList(newCollabRoom));
            response = Response.ok(collabResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            collabResponse.setMessage(DATA_ACCESS_ERROR);
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(DuplicateCollabRoomException dupe) {
            collabResponse.setMessage(dupe.getMessage());
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            e.printStackTrace();
            collabResponse.setMessage(UNHANDLED_EXCEPTION);
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus() && notify) {
            try {
                notifyChange(newCollabRoom);
            } catch(IOException | TimeoutException | AlreadyClosedException e) {
                log.error("Failed to publish CollabServiceImpl collabroom event", e);
            } catch(Exception e) {
                log.error("Failed to publish CollabServiceImpl collabroom event", e);
            }
        }

        return response;
    }

    /**
     * Pass through call for calls where the notification is expected, so always sent as true.
     **/
    public Response postCollabRoomWithPermissions(int incidentId, int orgId, int workspaceId,
                                                  CollabRoom collabroom) {

        return postCollabRoomWithPermissions(incidentId, orgId, workspaceId, collabroom, true);
    }


    @Override
    public Response getCollabRoomPresence(int incidentId, int collabroomId) {
        CollabServiceResponse collabResponse = new CollabServiceResponse();

        Date idleTime = getIdleTime();
        Date missingTime = getMissingTime();

        ConcurrentMap<String, CollabPresenceStatus> members = statuses.get(collabroomId);

        Collection<CollabPresenceStatus> userStatuses = Collections.<CollabPresenceStatus>emptyList();
        if(members != null) {
            userStatuses = members.values();
        }

        Iterator<CollabPresenceStatus> i = userStatuses.iterator();
        while(i.hasNext()) {
            CollabPresenceStatus status = i.next();
            if(status.getTimestamp().before(missingTime)) {
                i.remove();
            } else if(status.getTimestamp().before(idleTime)) {
                status.setStatus(CollabPresenceStatus.Status.IDLE);
            }
        }

        collabResponse.setResults(userStatuses);
        collabResponse.setCount(userStatuses.size());
        collabResponse.setMessage(Status.OK.getReasonPhrase());
        return Response.ok(collabResponse).status(Status.OK).build();
    }


    @Override
    public Response postCollabRoomPresence(int incidentId, int collabroomId, CollabPresenceStatus status) {

        if(null == status.getStatus()) {
            status.setStatus(CollabPresenceStatus.Status.ACTIVE);
        }
        status.setTimestamp(new Date());


        ConcurrentMap<String, CollabPresenceStatus> members = statuses.get(collabroomId);
        if(members == null) {
            members = new ConcurrentHashMap<String, CollabPresenceStatus>(EXPECTED_PARTICIPANTS);

            ConcurrentMap<String, CollabPresenceStatus> previous = statuses.putIfAbsent(collabroomId, members);
            if(previous != null) {
                members = previous;
            }
        }

        CollabPresenceStatus oldStatus = null;
        if(CollabPresenceStatus.Status.LEAVING.equals(status.getStatus())) {
            oldStatus = members.remove(status.getUsername());
        } else {
            oldStatus = members.put(status.getUsername(), status);
        }

        //fire presence change on new users and changes
        if(oldStatus == null || !status.getStatus().equals(oldStatus.getStatus())) {
            try {
                notifyChange(incidentId, collabroomId, status);
            } catch(IOException | TimeoutException | AlreadyClosedException e) {
                log.error("Failed to publish CollabServiceImpl presence event", e);
            } catch(Exception e) {
                log.error("Failed to publish CollabServiceImpl presence event", e);
            }
        }

        return getCollabRoomPresence(incidentId, collabroomId);
    }

    public Response validateSubscription(int collabRoomId, String username) {
        String incidentMap = APIConfig.getInstance().getConfiguration().getString(
                APIConfig.INCIDENT_MAP, SADisplayConstants.INCIDENT_MAP);

        Response response = null;
        CollabRoomPermissionResponse collabResponse = new CollabRoomPermissionResponse();
        long userId = userDao.getUserId(username);

        //verify the user has permissions
        if(collabDao.hasPermissions(userId, collabRoomId, incidentMap)) { //Everyone can susbscribe to the incidentmap
            response = Response.ok(collabResponse).status(Status.OK).build();
        } else {
            response = Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
        }

        return response;
    }

    public Response updateCollabRoomPermission(FieldMapResponse secureUsers, int collabRoomId, long userId,
                                               int orgId, int workspaceId, String username) {
        if(userDao.getUserId(username) != userId) {
            return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
        }

        //TODO: Check for permissions

        //Remove current security if there is any...
        collabDao.unsecureRoom(collabRoomId);
        List<Integer> adminUsers = (List<Integer>) secureUsers.getData().get(0).get("admin");
        List<Integer> readWriteUsers = (List<Integer>) secureUsers.getData().get(0).get("readWrite");
        List<Integer> readOnlyUsers = (List<Integer>) secureUsers.getData().get(0).get("readOnly");

        CollabRoomPermissionResponse collabResponse = this.secureRoom(
                collabRoomId, orgId, workspaceId, adminUsers, readWriteUsers,
                readOnlyUsers);

        try {
            CollabRoom room = collabDao.getCollabRoomById(collabRoomId);
            room.setAdminUsers(collabResponse.getAdminUsers());
            room.setReadWriteUsers(collabResponse.getReadWriteUsers());
            room.setReadOnlyUsers(collabResponse.getReadOnlyUsers());
            notifyUpdateChange(room);
        } catch(IOException | AlreadyClosedException e) {
            log.error("Failed to publish CollabServiceImpl collabroom event", e);
        } catch(DataAccessException e) {
            log.error("Failed to publish CollabServiceImpl collabroom event", e);
        } catch(Exception e) {
            log.error("Failed to publish CollabServiceImpl collabroom event", e);
        }

        return Response.ok(collabResponse).status(Status.OK).build();
    }

    public Response unsecureRoom(long collabRoomId, long userId, String username) {
        Response response = null;
        CollabRoomPermissionResponse collabResponse = new CollabRoomPermissionResponse();

        if(userDao.getUserId(username) != userId) {
            return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
        }

        //verify the user has permissions
        if(collabDao.hasPermissions(userId, collabRoomId)) {
            if(collabDao.unsecureRoom(collabRoomId)) {
                collabResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(collabResponse).status(Status.OK).build();
            } else {
                collabResponse.setMessage(UNSECURE_ERROR);
                response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            collabResponse.setMessage(UNSECURE_ERROR);
            response = Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                notifyUpdateChange(collabDao.getCollabRoomById(collabRoomId));
            } catch(IOException | AlreadyClosedException e) {
                log.error("Failed to publish CollabServiceImpl collabroom event", e);
            } catch(DataAccessException e) {
                log.error("Failed to publish CollabServiceImpl collabroom event", e);
            } catch(Exception e) {
                log.error("Failed to publish CollabServiceImpl collabroom event", e);
            }
        }
        return response;
    }

    private CollabRoomPermissionResponse secureRoom(int collabRoomId, int orgId, int workspaceId,
                                                    Collection<Integer> adminUsers, Collection<Integer> readWriteUsers,
                                                    Collection<Integer> readOnlyUsers) {
        CollabRoomPermissionResponse collabResponse = new CollabRoomPermissionResponse();
        Collection<Integer> adminIds = userOrgDao.getSuperUsers();

        //Remove duplicates
        adminUsers.removeAll(adminIds);
        //Add Super users back
        adminUsers.addAll(adminIds);
        try {
            for(Integer userId : adminUsers) {
                if(!collabDao.secureRoom(collabRoomId, userId, SADisplayConstants.ADMIN_ROLE_ID)) {
                    collabResponse.addFailedAdmin(userId);
                } else {
                    collabResponse.addAdminUser(userId);
                }
            }
            if(readWriteUsers != null) {
                for(Integer userId : readWriteUsers) {
                    if(!collabDao.secureRoom(collabRoomId, userId, SADisplayConstants.USER_ROLE_ID)) {
                        collabResponse.addFailedReadWrite(userId);
                    } else {
                        collabResponse.addReadWriteUser(userId);
                    }
                }
            }
            if(readOnlyUsers != null) {
                for(Integer userId : readOnlyUsers) {
                    if(!collabDao.secureRoom(collabRoomId, userId, SADisplayConstants.READ_ONLY_ROLE_ID)) {
                        collabResponse.addFailedReadOnly(userId);
                    } else {
                        collabResponse.addReadOnlyUser(userId);
                    }
                }
            }
            if(collabResponse.getFailedAdmin().size() > 0 ||
                    collabResponse.getFailedReadWrite().size() > 0) {
                collabResponse.setMessage(SECURE_ROOMS_ERROR);
            } else {
                collabResponse.setMessage(Status.OK.getReasonPhrase());
            }
        } catch(Exception e) {
            collabResponse.setMessage(UNHANDLED_EXCEPTION);
        }
        return collabResponse;
    }

    public Response getCollabRoomSecureUsers(int collabRoomId, String username) {
        Response response = null;

        if(collabDao.hasPermissions(userDao.getUserId(username), collabRoomId)) {
            FieldMapResponse dataResponse = new FieldMapResponse();
            dataResponse.setData(collabDao.getCollabRoomSecureUsers(collabRoomId));

            dataResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(dataResponse).status(Status.OK).build();
        } else {
            response = Response.ok(Status.FORBIDDEN.toString()).status(Status.OK).build();
        }

        return response;
    }

    public Response getCollabRoomUnSecureUsers(int collabRoomId, int orgId,
                                               int workspaceId, String username) {
        Response response = null;
        FieldMapResponse dataResponse = new FieldMapResponse();

        if(collabRoomId != -1 && collabDao.hasPermissions(userDao.getUserId(username), collabRoomId)) {
            dataResponse.setData(collabDao.getUsersWithoutPermission(collabRoomId, orgId, workspaceId));

            dataResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(dataResponse).status(Status.OK).build();
        } else if(collabRoomId == -1) {
            dataResponse.setData(userDao.getUsers(orgId, workspaceId));

            dataResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(dataResponse).status(Status.OK).build();
        } else {
            response = Response.ok(Status.FORBIDDEN.toString()).status(Status.OK).build();
        }

        return response;
    }

    public Response updateCollabRoomName(CollabRoom collabroom, String username){
        CollabServiceResponse collabResponse = new CollabServiceResponse();
        int collabRoomId = collabroom.getCollabRoomId();

        if(collabDao.hasPermissions(userDao.getUserId(username), collabRoomId)) {
            int count = collabDao.updateCollabRoomName(collabRoomId, collabroom.getName());
            if (count != 1) {
                collabResponse.setMessage(UPDATE_ROOM_NAME_ERROR);
                return Response.ok(collabResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            } else {
                try {
                    this.notifyRenameChange(collabroom);
                    collabResponse.setMessage(Status.OK.toString());
                }catch(Exception e){
                    collabResponse.setMessage(RENAME_MESSAGE_NOT_SENT);
                    log.error(e.getMessage());
                    return Response.ok(collabResponse).status(Status.EXPECTATION_FAILED).build();
                }

                return Response.ok(collabResponse).status(Status.OK).build();
            }
        }
        return Response.ok(Status.FORBIDDEN.toString()).status(Status.OK).build();
    }

    private CollabRoom createCollabRoom(int incidentId, CollabRoom collabroom)
            throws DataAccessException, DuplicateCollabRoomException, Exception {

        if(collabDao.hasRoomNamed(incidentId, collabroom.getName())) {
            throw new DuplicateCollabRoomException(collabroom.getName());
        }
        // set/override incidentId if set
        collabroom.setIncidentid(incidentId);
        collabroom.setCreated(new Date());
        int newCollabId = collabDao.create(collabroom);

        return collabDao.getCollabRoomById(newCollabId);
    }

    private boolean createGeoserverLayer(CollabRoom collabroom) {

        try {
            GeoserverUtil geoserver = new GeoserverUtil(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.GEOSERVER_URL),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.GEOSERVER_USERNAME),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.GEOSERVER_PASSWORD),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.GEOSERVER_WORKSPACE_NAME),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.GEOSERVER_DATASTORE_NAME));

            return geoserver.addCollabRoomView(collabroom);
        } catch(Exception e) {
            log.error("Unhandled exception creating layer via Geoserver API", e);
            return false;
        }
    }

    private Date getIdleTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -5);
        return cal.getTime();
    }

    private Date getMissingTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -15);
        return cal.getTime();
    }

    private void notifyChange(CollabRoom collabroom) throws IOException, TimeoutException, AlreadyClosedException {
        if(collabroom != null) {
            String topic = String.format("iweb.NICS.incident.%s.newcollabroom", collabroom.getIncidentid());
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(collabroom);
            getRabbitProducer().produce(topic, message);
        }
    }

    public void batchNotify(int incidentId, Collection<CollabRoom> collabRooms) throws IOException, TimeoutException,
            AlreadyClosedException{

        if(collabRooms != null && !collabRooms.isEmpty()) {
            String topic = String.format("iweb.NICS.incident.%s.newcollabrooms", incidentId);
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(collabRooms);
            getRabbitProducer().produce(topic, message);
        }

    }

    private void notifyUpdateChange(CollabRoom collabroom)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(collabroom != null) {
            String topic = String.format("iweb.NICS.incident.%s.updatedcollabroom", collabroom.getIncidentid());
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(collabroom);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyRenameChange(CollabRoom collabroom)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(collabroom != null) {
            String topic = String.format("iweb.NICS.incident.%s.renamecollabroom", collabroom.getIncidentid());
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(collabroom);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyChange(int incidentId, int collabroomId, CollabPresenceStatus status)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(status != null) {
            String topic = String.format("iweb.NICS.collabroom.%s.presence", collabroomId);
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(status);
            getRabbitProducer().produce(topic, message);
        }
    }

    private RabbitPubSubProducer getRabbitProducer() throws IOException, TimeoutException, AlreadyClosedException {
        if(rabbitProducer == null) {
            rabbitProducer = RabbitFactory.makeRabbitPubSubProducer(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
        }
        return rabbitProducer;
    }
}

