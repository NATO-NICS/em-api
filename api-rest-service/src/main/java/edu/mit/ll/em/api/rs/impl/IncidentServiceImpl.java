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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AlreadyClosedException;
import edu.mit.ll.em.api.exception.DuplicateCollabRoomException;
import edu.mit.ll.em.api.rs.CollabService;
import edu.mit.ll.em.api.rs.FieldMapResponse;
import edu.mit.ll.em.api.rs.IncidentService;
import edu.mit.ll.em.api.rs.IncidentServiceResponse;
import edu.mit.ll.em.api.rs.QueryConstraintParms;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.email.JsonEmail;
import edu.mit.ll.nics.common.entity.CollabRoom;
import edu.mit.ll.nics.common.entity.FormType;
import edu.mit.ll.nics.common.entity.Incident;
import edu.mit.ll.nics.common.entity.IncidentOrg;
import edu.mit.ll.nics.common.entity.IncidentType;
import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.entity.datalayer.Folder;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.FolderDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserSessionDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.WorkspaceDAOImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DuplicateKeyException;


/**
 * @AUTHOR sa23148
 */
public class IncidentServiceImpl implements IncidentService {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(IncidentServiceImpl.class);

    private static final String WORKING_MAP = "Working Map";

    private static final String DUPLICATE_NAME = "Incident name already exists.";

    private static final String MISSING_INCIDENT_TYPE = "You must choose at least one Incident Type.";

    private static final String NO_INCIDENT_FOLDER_FOUND = "No Incident Folder Found.";

    /**
     * The Incident DAO
     */
    private static final IncidentDAOImpl incidentDao = new IncidentDAOImpl();

    /**
     * The Org DAO
     */
    private static final OrgDAOImpl orgDao = new OrgDAOImpl();

    /**
     * The User DAO
     */
    private static final UserDAOImpl userDao = new UserDAOImpl();

    /**
     * The User DAO
     */
    private static final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();

    /**
     * Folder DAO
     */
    private static final FolderDAOImpl folderDao = new FolderDAOImpl();

    /**
     * The User DAO
     */
    private static final WorkspaceDAOImpl workspaceDao = new WorkspaceDAOImpl();

    /**
     * The UserSession DAO
     */
    private static final UserSessionDAOImpl userSessDao = new UserSessionDAOImpl();

    private RabbitPubSubProducer rabbitProducer;

    private IncidentOrgManager incOrgManager = new IncidentOrgManager(orgDao, incidentDao);

    /**
     * Gets a list of orgIds the given userId has access to
     *
     * @param userId userid of the User to get orgIds for
     * @return a list of orgIds the user has access to, null if none found or invalid userId
     */
    private List<Integer> getUserAccessibleOrgIds(Integer userId, Integer workspaceId) {
        User user = null;
        if(userId == null || (user = userDao.getUserById(userId)) == null) {
            return null;
        }

        List<Integer> orgIds = new ArrayList<>();

        boolean isSuper = userOrgDao.isUserRole(user.getUsername(), SADisplayConstants.SUPER_ROLE_ID);
        if(isSuper) {
            // TODO: getIncidents already does an isSuper check, so no need to get different orgIds here,
            // TODO: but this may still be useful?
            orgIds = orgDao.getOrganizationIds(workspaceId);

            return orgIds;
        }

        try {
            List<Org> userOrgs = orgDao.getUserOrgs(userId, workspaceId);
            for(Org org : userOrgs) {
                orgIds.add(org.getOrgId());
            }

            orgIds.addAll(orgDao.getAllChildren(orgIds));

            log.debug("GOT {} orgIds user is in", orgIds.size());

        } catch(Exception e) {
            log.error("Exception getting user's orgs: {}", e.getMessage(), e);
        }

        return orgIds;
    }

    /**
     * Read and return all Incident items.
     *
     * @param workspaceId
     * @param accessibleByUserId
     * @return Response IncidentResponse containing all Incidents with the specified workspace
     *
     * @see IncidentServiceResponse
     */
    public Response getIncidents(Integer workspaceId, Integer accessibleByUserId) {

        Response response;
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        List<edu.mit.ll.nics.common.entity.Incident> incidents = null;
        User user = null;

        if(accessibleByUserId == null || (user = userDao.getUserById(accessibleByUserId)) == null) {
            incidentResponse.setMessage("FAILED. accessibleByUserId not specified, or no user found");
            return Response.ok(incidentResponse).status(Status.PRECONDITION_FAILED).build();
        }

        try {
            String username = user.getUsername();
            if(userOrgDao.isUserRole(username,
                    SADisplayConstants.SUPER_ROLE_ID)) {
                incidents = incidentDao.getIncidents(workspaceId);
            } else {
                List<Integer> orgIds = getUserAccessibleOrgIds(accessibleByUserId, workspaceId);

                if(orgIds == null) {
                    incidentResponse.setMessage("FAILED. accessibleByUserId not specified, or no user found");
                    return Response.ok(incidentResponse).status(Status.PRECONDITION_FAILED).build();
                }

                incidents = incidentDao.getIncidents(workspaceId, orgIds);
            }

            incidentResponse.setIncidents(incidents);
            incidentResponse.setCount(incidents.size());
            incidentResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(incidentResponse).status(Status.OK).build();
        } catch(DataAccessException e) {

            incidentResponse.setMessage("Data access failure. Unable to read all incidents.");
            log.error("Data access exception while getting Incidents in (workspaceid, accessibleByUserId): {}, {}",
                    workspaceId, accessibleByUserId, e);
            incidentResponse.setCount(incidentResponse.getIncidents().size());
            response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            log.error("Unhandled exception while getting Incidents in (workspaceid, accessibleByUserId): {}, {}",
                    workspaceId, accessibleByUserId, e);

            incidentResponse.setMessage("Unhandled exception. Unable to read all incidents.");
            response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    public Response findIncidents(Integer workspaceId, String orgPrefix, String name,
                                  int incidentTypeId, boolean archived,
                                  QueryConstraintParms params, String requestingUser) {

        long startDate = -1;
        long endDate = -1;
        if(params != null) {
            if(params.getFromDate() != null) {
                startDate = params.getFromDate();
            }
            if(params.getToDate() != null) {
                endDate = params.getToDate();
            }
        }

        FieldMapResponse mapResponse = new FieldMapResponse();
        try {
            Long userId = (Long) userDao.getUserId(requestingUser);
            List<Integer> orgIds = getUserAccessibleOrgIds((userId != null ? userId.intValue() : null), workspaceId);

            if(orgPrefix != null) {
                mapResponse.setData(
                        incidentDao.findIncidentsByPrefix(workspaceId, orgPrefix, name, incidentTypeId, archived,
                                startDate, endDate, orgIds));
            } else if(name != null) {
                mapResponse.setData(incidentDao.findIncidentsByName(workspaceId, name, incidentTypeId, archived,
                        startDate, endDate, orgIds));
            } else if(incidentTypeId != -1) {
                mapResponse.setData(incidentDao.findIncidentsByIncidentTypeId(workspaceId, incidentTypeId, archived,
                        startDate, endDate, orgIds));
            } else if(startDate != -1 || endDate != -1) {
                mapResponse.setData(incidentDao.findIncidentsByTimeFrame(workspaceId, archived,
                        startDate, endDate, orgIds));
            } else {
                mapResponse.setData(incidentDao.findIncidents(workspaceId, archived, orgIds));
            }

            return Response.ok(mapResponse).status(Status.OK).build();
        } catch(Exception e) {
            return Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    public Response activateIncident(int workspaceId, int incidentId, String username) {
        if(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) ||
                incidentDao.isAdmin(workspaceId, incidentId, username)) {

            boolean ret = incidentDao.setIncidentActive(incidentId, true, null);
            if(ret) {
                String topic = String.format("iweb.NICS.ws.%s.newIncident", workspaceId);
                try {
                    this.notifyIncidentRestricted(incidentDao.getIncident(incidentId), topic);
                    return Response.ok(Status.OK.toString()).status(Status.OK).build();
                } catch(Exception e) {
                    return Response.ok("The incident was activated successfully, but there "
                            + "was an error notifying the users.").status(Status.INTERNAL_SERVER_ERROR).build();
                }
            }
            return Response.ok("There was an error activating the incident").status(Status.INTERNAL_SERVER_ERROR)
                    .build();
        } else {
            return Response.ok(Status.FORBIDDEN).status(Status.FORBIDDEN).build();
        }
    }

    public Response archiveIncident(int workspaceId, int incidentId, String username, String folderId) {
        if(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) ||
                incidentDao.isAdmin(workspaceId, incidentId, username)) {
            boolean ret = incidentDao.setIncidentActive(incidentId, false, folderId);
            if(ret) {
                String topic = String.format("iweb.NICS.ws.%s.removeIncident", workspaceId);
                try {
                    this.notifyIncident(incidentId, topic);
                    return Response.ok(Status.OK.toString()).status(Status.OK).build();
                } catch(Exception e) {
                    return Response.ok("The incident was archived successfully, but there "
                            + "was an error notifying the users.").status(Status.INTERNAL_SERVER_ERROR).build();
                }
            }
            return Response.ok("There was an error archiving the incident").status(Status.INTERNAL_SERVER_ERROR)
                    .build();
        } else {
            return Response.ok(Status.FORBIDDEN).status(Status.FORBIDDEN).build();
        }

    }

    public Response getActiveIncidents(Integer workspaceId, Integer orgId, String requestingUser) {
        return this.getIncidents(workspaceId, orgId, true, requestingUser, null);
    }

    public Response getArchivedIncidents(Integer workspaceId, Integer orgId, String requestingUser, String folderId) {
        return this.getIncidents(workspaceId, orgId, false, requestingUser, folderId);
    }

    private Response getIncidents(Integer workspaceId, Integer orgId, boolean active, String requestingUser,
                                  String folderId) {

        User user = null;
        FieldMapResponse incidentResponse = new FieldMapResponse();

        if((user = userDao.getUser(requestingUser)) == null) {
            incidentResponse.setMessage("FAILED. Parameter requestingUser not specified, or no user found");
            return Response.ok(incidentResponse).status(Status.PRECONDITION_FAILED).build();
        }

        List<Map<String, Object>> incidents = new ArrayList<>();

        try {
            String username = user.getUsername();
            if(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID)) {
                incidents = incidentDao.getActiveIncidents(workspaceId, orgId, active, folderId);
            } else {
                List<Integer> orgIds = getUserAccessibleOrgIds(user.getUserId(), workspaceId);

                if(orgIds != null && orgIds.contains(orgId)) {
                    incidents = incidentDao.getActiveIncidents(workspaceId, orgId, active, folderId);
                    incidentResponse.setMessage(incidents.isEmpty() ? "No incidents found" : "Found incidents");
                } else {
                    incidentResponse.setMessage("User has no permissions on this org");
                }
            }
        } catch(Exception e) {
            log.error("Unhandled exception getting {} Incidents for orgId {}",
                    (active ? "Active" : "Archived"), orgId);

            incidentResponse.setMessage(String.format("Failed to get %s Incidents for orgId %d",
                    (active ? "Active" : "Archived"), orgId));

            return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        incidentResponse.setData(incidents);
        return Response.ok(incidentResponse).status(Status.OK).build();
    }

    /**
     * Read and return all Incident items.
     *
     * @param workspaceId
     * @param accessibleByUserId
     * @return Response IncidentResponse containing all Incidents with the specified workspace and there children
     *
     * @see IncidentServiceResponse
     * <p>
     * public Response getIncidentsTree(Integer workspaceId, Integer accessibleByUserId) {
     * @see IncidentServiceResponse
     */

    public Response getIncidentsTree(Integer workspaceId, Integer accessibleByUserId) {
        Response response = null;
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        List<edu.mit.ll.nics.common.entity.Incident> incidents = null;
        try {
            List<Integer> orgIds = getUserAccessibleOrgIds(accessibleByUserId, workspaceId);
            incidents = incidentDao.getIncidentsTreeRestricted(workspaceId, orgIds);

            incidentResponse.setIncidents(incidents);
            incidentResponse.setCount(incidents.size());
            incidentResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(incidentResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            incidentResponse.setMessage("Data access failure. Unable to read all incidents.");
            log.error("Data access exception while getting Incidents Tree in (workspaceid, accessibleByUserId): {}, {}",
                    workspaceId, accessibleByUserId, e);
            incidentResponse.setCount(incidentResponse.getIncidents().size());
            response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            log.error("Unhandled exception while getting Incidents Tree in (workspaceid, accessibleByUserId): {}, {}",
                    workspaceId, accessibleByUserId, e);

            incidentResponse.setMessage("Unhandled exception. Unable to read all incidents.");
            response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    public Response getIncidentTypes() {
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        try {
            incidentResponse.setIncidentTypes(this.incidentDao.getIncidentTypes());
            incidentResponse.setMessage(Status.OK.toString());
            return Response.ok(incidentResponse).status(Status.OK).build();
        } catch(Exception e) {
            incidentResponse.setMessage(e.getMessage());
        }
        return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @Override
    public Response getIncidentOwner(Integer workspaceId, Integer incidentId, String requestingUser) {
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();

        try {
            Integer orgId = incidentDao.getOwningOrgId(incidentId);
            Org organization = orgDao.getOrganization(orgId);
            incidentResponse.setMessage("OK");
            incidentResponse.setOwningOrg(organization);
            return Response.ok(incidentResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            log.error("Failed to retrieve owning org: " + e.getMessage(), e);
        }

        incidentResponse.setMessage("Failed to retrieve owning org");
        return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * Updates the incident that was sent
     *
     * @param workspaceId
     * @param incident    to update
     * @return Response IncidentResponse containing the updated incident
     *
     * @see IncidentServiceResponse
     */
    public Response updateIncident(Integer workspaceId, Incident incident, String requestingUser) {
        log.debug("Update Incident");

        Response response = null;
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        Incident updatedIncident = null;

        List<Integer> incidentIds = incidentDao.getIncidentIdsAccessibleToUser(
                getUserAccessibleOrgIds(userDao.getUser(requestingUser)
                        .getUserId(), workspaceId));

        if(!incidentIds.contains(incident.getIncidentid())) {
            incidentResponse.setMessage(String.format("User %s does not have access to Incident %d",
                    requestingUser, incident.getIncidentid()));
            return Response.ok(incidentResponse).status(Status.UNAUTHORIZED).build();
        }

        Incident dIncident = incidentDao.getIncidentByName(incident.getIncidentname(), workspaceId);
        if(dIncident != null && (!dIncident.getIncidentid().equals(incident.getIncidentid()))) {
            incidentResponse.setMessage(DUPLICATE_NAME);
            return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(incident.getIncidentIncidenttypes() == null || incident.getIncidentIncidenttypes().isEmpty()) {
            incidentResponse.setMessage(MISSING_INCIDENT_TYPE);
            return Response.ok(incidentResponse).status(Status.EXPECTATION_FAILED).build();
        }

        try {
            updatedIncident = incidentDao.updateIncident(workspaceId, incident);

            if(updatedIncident != null) {
                incidentResponse.setCount(1);
                incidentResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(incidentResponse).status(Status.OK).build();
            } else {
                incidentResponse.setMessage("Error updating incident.");
                incidentResponse.setCount(0);
                response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }

        } catch(Exception e) {
            incidentResponse.setMessage("Error updating incident.");
            log.error("Data access exception while updating Incident {}",
                    incident.getIncidentname(), e);
            incidentResponse.setCount(incidentResponse.getIncidents().size());
            response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }


        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                String topic = String.format("iweb.NICS.incident.%d.update", incident.getIncidentid());
                notifyIncident(updatedIncident, topic);
            } catch(Exception e) {
                log.error("Failed to publish a update Incident message event", e);
            }
        }

        return response;
    }

    /**
     * Creates single Incident
     *
     * <p>
     * TODO: not used by mobile, AND needs fully implemented for use with the ui-framework incident module. This call
     * simply persists an Incident entity w/o all the other setup the SA/message does
     * </p>
     *
     * @param workspaceId
     * @param incident    Incident to be persisted
     * @return Response
     *
     * @throws Exception
     * @throws DuplicateCollabRoomException
     * @throws DataAccessException
     * @see IncidentServiceResponse
     */

    public Response postIncident(Integer workspaceId, Integer orgId, Integer userId, Incident incident)
            throws DataAccessException, DuplicateCollabRoomException, Exception {

        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        Response response = null;
        JsonEmail email = null;

        if(incidentDao.getIncidentByName(incident.getIncidentname(), workspaceId) != null) {
            incidentResponse.setMessage(DUPLICATE_NAME);
            return Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(incident.getIncidentIncidenttypes() == null || incident.getIncidentIncidenttypes().isEmpty()) {
            incidentResponse.setMessage(MISSING_INCIDENT_TYPE);
            return Response.ok(incidentResponse).status(Status.EXPECTATION_FAILED).build();
        }

        if(incident.getIncidentorgs() != null && !incident.getIncidentorgs().isEmpty()) {
            boolean containsSelf = false;
            for(IncidentOrg io : incident.getIncidentorgs()) {
                if(io.getOrgid() == orgId) {
                    containsSelf = true;
                }
            }

            //Add organization creating the incident
            if(!containsSelf) {
                IncidentOrg self = new IncidentOrg();
                self.setOrgid(orgId);
                self.setUserid(userId);
                incident.getIncidentorgs().add(self);
            }

            Set<IncidentOrg> parents =
                    incOrgManager.convertToSet(
                            incOrgManager.getParentOrgs(
                                    incOrgManager.convertToList(incident.getIncidentorgs()),
                                    -1, userId));

            incident.getIncidentorgs().addAll(parents);
        }

        Incident newIncident = null;
        try {
            incident.setCreated(new Date());
            newIncident = incidentDao.create(incident);

            if(newIncident != null) {
                incidentResponse.getIncidents().add(newIncident);
                incidentResponse.setMessage(Status.OK.getReasonPhrase());
                incidentResponse.setCount(incidentResponse.getIncidents().size());
                response = Response.ok(incidentResponse).status(Status.OK).build();
            } else {
                incidentResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
                incidentResponse.setCount(0);
                response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                return response;
            }
        } catch(Exception e) {
            incidentResponse.setMessage("Post Incident failed.");
            response = Response.ok(incidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        //Create incident folder for datalayers
        String parentFolderId = folderDao.getRootFolder("Data", workspaceId).getFolderid();
        Folder folder = new Folder();
        folder.setFoldername(incident.getIncidentname());
        folder.setIndex(folderDao.getNextFolderIndex(parentFolderId));
        folder.setParentfolderid(parentFolderId);
        folder.setWorkspaceid(workspaceId);
        Folder newFolder = folderDao.createFolder(folder);
        if(newFolder != null) {
            incidentDao.createIncidentFolder(newIncident.getIncidentid(), newFolder.getFolderid());
            incidentResponse.setIncidentFolder(newFolder);
        }else{
            System.out.println("Could not create folder!!");
        }

        //Publish new folder -I'm not sure we have to do this
        //String topic = String.format("iweb.NICS.%s.folder.new", workspaceId);

        // Create default rooms
        CollabRoom incidentMap = createDefaultCollabRoom(newIncident.getUsersessionid(),
                APIConfig.getInstance().getConfiguration().getString(APIConfig.INCIDENT_MAP,
                        SADisplayConstants.INCIDENT_MAP));
        incidentMap.setIncident(newIncident);
        List<Integer> admins = orgDao.getOrgAdmins(orgId, workspaceId);
        if(!admins.contains(userId)) {
            incidentMap.getAdminUsers().add(userId);
        }
        incidentMap.getAdminUsers().addAll(admins);

        CollabService collabRoomEndpoint = new CollabServiceImpl();
        collabRoomEndpoint.createCollabRoomWithPermissions(newIncident.getIncidentid(), orgId, workspaceId,
                incidentMap);


        CollabRoom workingMap = createDefaultCollabRoom(newIncident.getUsersessionid(), WORKING_MAP);
        workingMap.setIncident(newIncident);
        collabRoomEndpoint.createUnsecureCollabRoom(newIncident.getIncidentid(), workingMap);

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                String topic = String.format("iweb.NICS.ws.%s.newIncident", workspaceId);
                Set<IncidentOrg> incidentOrgs = incident.getIncidentorgs();

                // If the incident has been locked down to one or more orgs, only send to those orgs
                if(incidentOrgs != null && !incidentOrgs.isEmpty()) {
                    // Notify orgs
                    notifyIncidentOrgsToOrgs(workspaceId, newIncident, incidentOrgs);

                    // Notify superusers
                    notifyIncident(newIncident, String.format("iweb.NICS.ws.%d.superuser.incident.add", workspaceId));
                } else {
                    // If it was not restricted, send to everyone as usual
                    notifyIncident(newIncident, topic);
                }

                try {

                    String date = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy").format(new Date());
                    String alertTopic = String.format("iweb.nics.email.alert");
                    String newIncidentUsers =
                            APIConfig.getInstance().getConfiguration().getString(APIConfig.NEW_INCIDENT_USERS_EMAIL);
                    String hostname = InetAddress.getLocalHost().getHostName();
                    User creator = userDao.getUserBySessionId(newIncident.getUsersessionid());
                    Org org = orgDao.getLoggedInOrg(creator.getUserId());
                    List<String> disList = orgDao.getOrgAdmins(org.getOrgId());
                    String toEmails =
                            disList.toString().substring(1, disList.toString().length() - 1) + ", " + newIncidentUsers;
                    String siteName = workspaceDao.getWorkspaceName(workspaceId);

                    if(disList.size() > 0) {
                        email = new JsonEmail(creator.getUsername(), toEmails, "Alert from NewIncident@" + hostname);
                        email.setBody(
                                date + "\n\n" + "A new incident has been created: " + newIncident.getIncidentname() +
                                        "\n" +
                                        "Creator: " + creator.getUsername() + "\n" +
                                        "Location: " + newIncident.getLat() + "," + newIncident.getLon() + "\n" +
                                        "Site: " + siteName);

                        notifyNewIncidentEmail(email.toJsonObject().toString(), alertTopic);
                    }

                } catch(Exception e) {
                    log.error("Failed to send new Incident email alerts", e);
                }


            } catch(Exception e) {
                log.error("Failed to publish a new Incident message event", e);
            }
        }

        return response;
    }

    private CollabRoom createDefaultCollabRoom(int userSessionId, String name) {
        CollabRoom collabRoom = new CollabRoom();
        collabRoom.setName(name);
        collabRoom.setUsersessionid(userSessionId);
        return collabRoom;
    }

    /**
     * Restricted version of {@link IncidentServiceImpl#notifyIncident(Incident, String)}. Based on the known topics
     * ending in newIncident, updateIncident, and removeIncident, notifications are sent to those orgs (and super
     * users)
     * who have access to the Incident. If the incident is not restricted to any orgs, then the usual {@link
     * IncidentServiceImpl#notifyIncident(Incident, String)} method is called.
     *
     * @param newIncident the Incident to include in the notification
     * @param topic       the topic everyone in a workspace usually gets incident notifications on, expected to contain
     *                    newIncident, updateIncident, or removeIncident
     * @throws Exception when there's a failure building and/or sending notification, or an issue with the queries for
     *                   incident_org
     */
    private void notifyIncidentRestricted(Incident newIncident, String topic) throws Exception {
        if(newIncident == null) {
            return;
        }

        List<Integer> orgIds = incidentDao.getIncidentOrgIds(newIncident.getIncidentid());
        int workspaceId = newIncident.getWorkspaceid();

        try {
            // Tell orgs that have access
            if(orgIds != null && !orgIds.isEmpty()) {

                if(topic.contains("newIncident")) {
                    // called by postIncident and activateIncident

                    // Notify Supers
                    notifyIncident(newIncident, String.format("iweb.NICS.ws.%d.superuser.incident.add", workspaceId));

                    // Notify orgs
                    notifyIncidentToOrgs(newIncident.getWorkspaceid(), newIncident, orgIds);

                } else {
                    log.warn("Unhandled notifyIncident topic ({}), not sending notification for Incident ID {}",
                            topic, newIncident.getIncidentid());
                }

            } else {
                // If not restricted, do the usual
                notifyIncident(newIncident, topic);
            }
        } catch(Exception e) {
            log.error("Failed to notify incident restricted.", e);
        }
    }

    private void notifyIncident(Incident newIncident, String topic)
            throws IOException, TimeoutException, AlreadyClosedException {

        if(newIncident != null) {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(newIncident);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyIncident(int incidentId, String topic)
            throws IOException, TimeoutException, AlreadyClosedException {
        getRabbitProducer().produce(topic, (new Integer(incidentId).toString()));
    }

    private void notifyNewIncidentEmail(String email, String topic)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(email != null) {
            getRabbitProducer().produce(topic, email);
        }
    }

    public Response getActiveIncidentOrg(Integer workspaceId, String requestingUser) {
        FieldMapResponse dataResponse = new FieldMapResponse();

        List<Map<String, Object>> results = incidentDao.getIncidentOrg(workspaceId);
        dataResponse.setData(results);

        dataResponse.setMessage(Status.OK.getReasonPhrase());
        return Response.ok(dataResponse).status(Status.OK).build();
    }


    /**
     * Return the number of Incidents in the specified workspace
     *
     * @param workspaceId ID of workspace to get incident count from
     * @return Response An IncidentResponse with the count field set to the count of incidents
     *
     * @see IncidentServiceResponse
     */
    @Deprecated
    public Response getIncidentCount(Integer workspaceId) {
        Response response;
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        incidentResponse.setMessage(Status.OK.getReasonPhrase());
        int count = incidentDao.getIncidentCount(workspaceId);
        incidentResponse.setCount(count);
        if(count == -1) {
            incidentResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
            response = Response.ok(incidentResponse)
                    .status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        response = Response.ok(incidentResponse).status(Status.OK).build();
        return response;
    }

    /**
     * Return the folder for this incident
     *
     * @param incidentId
     * @return Folder
     *
     * @see IncidentServiceResponse
     */
    @Override
    public Response getIncidentFolder(Integer incidentId) {
        Response response;
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        Folder folder = incidentDao.getIncidentFolder(incidentId);
        if(folder == null) {
            incidentResponse.setMessage(NO_INCIDENT_FOLDER_FOUND);
        }else{
            incidentResponse.setMessage(Status.OK.getReasonPhrase());
            incidentResponse.setIncidentFolder(folder);
        }

        response = Response.ok(incidentResponse).status(Status.OK).build();
        return response;
    }

    @Override
    public Response getIncidentOrgs(Integer incidentId) {
        Response response = null;
        IncidentServiceResponse orgIncidentResponse = new IncidentServiceResponse();
        List<IncidentOrg> incidentOrgs = null;
        try {
            if(incidentId == null) {
                throw new IllegalArgumentException("incidentId must be specified");
            }

            log.debug("Getting incidentOrgs for incidentId: {}", incidentId);

            incidentOrgs = incidentDao.getIncidentOrgs(incidentId);
            log.trace("Got incidentOrgs: {}", Arrays.toString(incidentOrgs.toArray()));

            orgIncidentResponse.setIncidentOrgs(incidentOrgs);
            orgIncidentResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(orgIncidentResponse).status(Status.OK).build();
        } catch(Exception e) {
            orgIncidentResponse.setMessage("Unhandled exception, " + e.getMessage());
            log.error("Unhandled exception while querying getOrgIncident with incidentId {}", incidentId, e);
        }

        return response;
    }

    @Override
    public Response postIncidentOrgs(List<IncidentOrg> incidentOrgs, Integer incidentId,
                                     String requestingUser, Integer workspaceId) {
        Response response;
        IncidentServiceResponse orgIncidentResponse = new IncidentServiceResponse();

        Map<String, Object> result = null;
        List<Integer> currentIncidentOrgs = null;

        try {
            if(requestingUser == null || incidentId == null || incidentOrgs == null || incidentOrgs.isEmpty()) {
                throw new IllegalArgumentException("workspaceId, incidentId, incidentOrgs, " +
                        "and username must be specified");
            }

            User user = getUserByName(requestingUser);
            if(user == null) {
                orgIncidentResponse.setMessage("Failed. User with username " + requestingUser + " not found.");
                return Response.ok(orgIncidentResponse).status(Status.PRECONDITION_FAILED).build();
            }

            //NOTE: I think should be || not && ...
            if(!incidentDao.isAdmin(workspaceId, incidentId, requestingUser) &&
                    !userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
                orgIncidentResponse.setMessage("Failed. User must be admin of org owning the incident to make changes");
                return Response.ok(orgIncidentResponse).status(Status.UNAUTHORIZED).build();
            }

            log.debug("User {} is adding incidentorg mappings for incidentId {}",
                    user.getUserId(), incidentId);

            try {
                currentIncidentOrgs = incidentDao.getIncidentOrgIds(incidentId);

                //Add parent organizations
                //If the owning orgid is not in the list - it will be added here
                incidentOrgs.addAll(incOrgManager.getParentOrgs(incidentOrgs, incidentId, user.getUserId()));

                //Add new incident orgs
                result = incidentDao.addIncidentOrgs(workspaceId, incidentId, requestingUser,
                        user.getUserId(), incidentOrgs);

            } catch(Exception e) {
                log.error("Exception adding incidentOrgs", e);

                if(e.getMessage().contains("authorized")) {
                    orgIncidentResponse.setMessage("Failed. User not authorized.");
                } else {
                    orgIncidentResponse.setMessage("Failed. Unhandled exception: " + e.getMessage());
                }

                return Response.ok(orgIncidentResponse).status(Status.UNAUTHORIZED).build();
            }

            List<Integer> success = (List<Integer>) result.get("success");
            List<Integer> fail = (List<Integer>) result.get("fail");
            List<String> messages = (List<String>) result.get("messages");

            log.trace("Succeeded on orgIds {}, failed on orgIds {}, reasons:\n{}",
                    Arrays.toString(success.toArray()),
                    Arrays.toString(fail.toArray()),
                    Arrays.toString(messages.toArray()));

            orgIncidentResponse.setMessage("Successfully added " + success.size() + " IncidentOrg mappings.");
            orgIncidentResponse.setCount(success.size());
            response = Response.ok(orgIncidentResponse).status(Status.OK).build();

            notifyIncidentOrgAdded(workspaceId, incidentId, success, currentIncidentOrgs);
        } catch(Exception e) {
            response = Response.ok(orgIncidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            orgIncidentResponse.setMessage("Failure. Unhandled exception: " + e.getMessage());
            log.error("Unhandled exception while adding orgIds to incident with incidentId {}", incidentId, e);
        }

        return response;
    }

    @Override
    public Response readIncidentTypes(int workspaceId, String requestingUser) {
        IncidentServiceResponse incidentServiceResponse = new IncidentServiceResponse();
        Status status = null;
        try {
            List<IncidentType> incidentTypes = incidentDao.getIncidentTypes();
            incidentServiceResponse.setMessage("OK");
            incidentServiceResponse.setIncidentTypes(incidentTypes);
            incidentServiceResponse.setCount(incidentTypes.size());
            status = Status.OK;
        } catch(Exception e) {
            log.error("Unhandled exception reading IncidentTypes", e);
            incidentServiceResponse.setMessage(e.getMessage()); // Not ideal sticking stacktrace message in response
            status = Status.INTERNAL_SERVER_ERROR;
        }

        return Response.ok(incidentServiceResponse).status(status).build();
    }

    @Override
    public Response createIncidentType(int workspaceId, String requestingUser, IncidentType incidentType) {

        IncidentServiceResponse incidentServiceResponse = new IncidentServiceResponse();
        Status status = null;

        // Validate
        if (incidentType == null || incidentType.getIncidentTypeName() == null
                || incidentType.getIncidentTypeName().isEmpty()) {

            incidentServiceResponse.setMessage("Invalid IncidentType");
            status = Status.PRECONDITION_FAILED;
            return Response.ok(incidentServiceResponse).status(status).build();
        }

        User user = getUserByName(requestingUser);
        if(user == null ||
                !(userOrgDao.isUserRole(requestingUser, SADisplayConstants.ADMIN_ROLE_ID) ||
                userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID))) {

            incidentServiceResponse.setMessage("User not authorized.");
            status = Status.UNAUTHORIZED;
            return Response.ok(incidentServiceResponse).status(status).build();
        }

        try {
            IncidentType newIncidentType = incidentDao.createIncidentType(incidentType);
            incidentServiceResponse.setMessage("OK");
            incidentServiceResponse.setIncidentTypes(Arrays.asList(newIncidentType));
            incidentServiceResponse.setCount(1);
            status = Status.CREATED;
        } catch (Exception e) {
            log.error("Unhandled exception creating IncidentType {}", incidentType.getIncidentTypeName(), e);
            incidentServiceResponse.setMessage(e.getMessage());
            status = Status.INTERNAL_SERVER_ERROR;
        }

        return Response.ok(incidentServiceResponse).status(status).build();
    }

    @Override
    public Response updateIncidentType(int workspaceId, String requestingUser, int incidentTypeId,
                                       IncidentType incidentType) {
        IncidentServiceResponse incidentServiceResponse = new IncidentServiceResponse();
        Status status = null;

        // Validate
        if (incidentType == null || incidentType.getIncidentTypeId() <= 0
                || incidentType.getIncidentTypeName() == null
                || incidentType.getIncidentTypeName().isEmpty()
                || incidentTypeId != incidentType.getIncidentTypeId()) {

            incidentServiceResponse.setMessage("Invalid IncidentType");
            status = Status.PRECONDITION_FAILED;
            return Response.ok(incidentServiceResponse).status(status).build();
        }

        User user = getUserByName(requestingUser);
        if(user == null || !userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
            incidentServiceResponse.setMessage("User not authorized.");
            status = Status.UNAUTHORIZED;
            return Response.ok(incidentServiceResponse).status(status).build();
        }

        try {
            IncidentType updatedIncidentType = incidentDao.updateIncidentType(incidentType);

            if(updatedIncidentType != null) {
                incidentServiceResponse.setMessage("OK");
                incidentServiceResponse.setIncidentTypes(Arrays.asList(updatedIncidentType));
                incidentServiceResponse.setCount(1);
                status = Status.OK;
            } else {
                incidentServiceResponse.setMessage("Problem returning updated IncidentType");
                status = Status.EXPECTATION_FAILED;
            }
        } catch (Exception e) {
            log.error("Unhandled exception creating IncidentType {}", incidentType.getIncidentTypeName(), e);
            incidentServiceResponse.setMessage(e.getMessage());
            status = Status.INTERNAL_SERVER_ERROR;
        }

        return Response.ok(incidentServiceResponse).status(status).build();
    }

    @Override
    public Response deleteIncidentType(int workspaceId, String requestingUser, int incidentTypeId) {
        IncidentServiceResponse incidentServiceResponse = new IncidentServiceResponse();
        Status status = null;

        try {
            if(!userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
                incidentServiceResponse.setMessage("User not authorized");
                status = Status.UNAUTHORIZED;
                return Response.ok(incidentServiceResponse).status(status).build();
            }

            boolean deleted = incidentDao.deleteIncidentType(incidentTypeId);
            if(deleted) {
                incidentServiceResponse.setMessage("IncidentType successfully deleted");
                status = Status.OK;
            } else {
                incidentServiceResponse.setMessage("Failed to delete IncidentType");
                status = Status.EXPECTATION_FAILED;
            }

        } catch(Exception e){
            log.error("Exception deleting IncidentType {}", incidentTypeId, e);
            incidentServiceResponse.setMessage(e.getMessage());
            status = Status.INTERNAL_SERVER_ERROR;
        }

        return Response.ok(incidentServiceResponse).status(status).build();
    }

    @Override
    public Response deleteIncidentOrgs(List<IncidentOrg> incidentOrgs, Integer incidentId,
                                       String requestingUser, Integer workspaceId) {

        Response response;
        IncidentServiceResponse orgIncidentResponse = new IncidentServiceResponse();

        try {
            if(incidentId == null || incidentOrgs == null) {
                throw new IllegalArgumentException("Both incidentId and incidentOrgs must be specified");
            }

            User user = getUserByName(requestingUser);
            if(user == null) {
                orgIncidentResponse.setMessage("Failed. User " + requestingUser + " not found.");
                return Response.ok(orgIncidentResponse).status(Status.PRECONDITION_FAILED).build();
            }

            if(!incidentDao.isAdmin(workspaceId, incidentId, requestingUser) &&
                    !userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
                orgIncidentResponse.setMessage("Failed. User must be admin of org owning the incident to make changes");
                return Response.ok(orgIncidentResponse).status(Status.UNAUTHORIZED).build();
            }

            List<Integer> currentIncidentOrgs = incidentDao.getIncidentOrgIds(incidentId);

            log.debug("User {} deleting orgIds for incidentId {} org_incident", user.getUserId(), incidentId);

            Map<String, Object> result = null;
            try {
                if(currentIncidentOrgs.size() == incidentOrgs.size()) {
                    //remove them all
                    result = incidentDao.removeIncidentOrgs(workspaceId, incidentId, requestingUser,
                            user.getUserId(), incidentOrgs);
                } else {
                    if(incOrgManager.isOwningOrgLockedOut(currentIncidentOrgs, incidentOrgs, incidentId)) {
                        orgIncidentResponse.setMessage("Failed. This action would result in the owning organization " +
                                "being locked out.");
                        return Response.ok(orgIncidentResponse).status(Status.PRECONDITION_FAILED).build();
                    }

                    List<Integer> orgsToBeRemoved = incOrgManager.getOrgIds(incidentOrgs);

                    //Cannot remove a parent organization from a locked incident
                    List<IncidentOrg> validatedOrgs = new ArrayList();
                    for(IncidentOrg incidentOrg : incidentOrgs) {
                        if(incOrgManager.validateRemoval(incidentOrg,
                                currentIncidentOrgs, orgsToBeRemoved)) {
                            validatedOrgs.add(incidentOrg);
                        }
                    }

                    if(validatedOrgs.size() > 0) {

                        result = incidentDao.removeIncidentOrgs(workspaceId, incidentId, requestingUser,
                                user.getUserId(), validatedOrgs);

                    }
                }
            } catch(Exception e) {
                e.printStackTrace();

                log.error("Exception adding incidentOrgs", e);

                if(e.getMessage().contains("authorized")) {
                    orgIncidentResponse.setMessage("Failed. User not authorized.");
                } else {
                    orgIncidentResponse.setMessage("Failed. Unhandled exception: " + e.getMessage());
                }

                return Response.ok(orgIncidentResponse).status(Status.UNAUTHORIZED).build();
            }

            if(result != null) {
                List<Integer> success = (List<Integer>) result.get("success");
                // TODO: as of 1.8, can use getOrDefault, but to be safe:
                if(success == null) {
                    success = new ArrayList<>();
                }
                List<Integer> fail = (List<Integer>) result.get("fail");
                List<String> messages = (List<String>) result.get("messages");

                if(success != null) {

                    if(success.size() != incidentOrgs.size()) {
                        log.warn("The number of Incidents requested for deletion does not match the number that " +
                                "were deleted!\n{}", (messages == null) ? "" : Arrays.asList(messages.toArray()));
                    }

                }

                orgIncidentResponse.setMessage("Successfully deleted " + success.size() + " OrgIncident mappings");
                orgIncidentResponse.setCount(success.size());

                notifyIncidentOrgRemoved(workspaceId, incidentId, success, currentIncidentOrgs);
            } else {
                orgIncidentResponse.setMessage(
                        "Parent organizations cannot be removed when the child organization are still secured to the " +
								"incident.");
            }

            response = Response.ok(orgIncidentResponse).status(Status.OK).build();
        } catch(Exception e) {
            e.printStackTrace();
            orgIncidentResponse.setMessage("Unhandled exception, " + e.getMessage());
            response = Response.ok(orgIncidentResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            log.error("Unhandled exception while deleting orgIds from incident with incidentId {}", incidentId, e);
        }

        return response;
    }

    /**
     * Send an incident to the specified orgs
     *
     * @param workspaceId the workspace in use
     * @param incident    the incident to be sent to clients
     * @param orgIds      the orgs to send the message to
     * @throws Exception when there's an issue serializing the payload object
     */
    private void notifyIncidentToOrgs(Integer workspaceId, Incident incident, List<Integer> orgIds) throws Exception {
        String topicTemplate = "iweb.NICS.ws.%d.incidentorg.%d.add";
        String topic;
        ObjectMapper mapper = new ObjectMapper();

        String message = mapper.writeValueAsString(incident);

        for(Integer orgId : orgIds) {
            topic = String.format(topicTemplate, workspaceId, orgId);
            try {
                getRabbitProducer().produce(topic, message);
                log.debug("Sent to topic: {}\n{}", topic, message);
            } catch(IOException e) {
                log.warn("Failed to send Incident to topic: {}", topic);
            } catch(AlreadyClosedException e) {
                log.error("Exception RabbitMQ attempting to use a closed channel ", e);
            } catch(Exception e) {
                log.error("Exception RabbitMQ exception occurred.", e);
            }
        }
    }

    /**
     * Send an incident to all who have this incident loaded
     *
     * @param incident the incident to be sent to clients
     * @throws Exception when there's an issue serializing the payload object
     */
    private void notifyIncidentUpdate(Incident incident) throws Exception {

        String topic = String.format("iweb.NICS.incident.%d.update", incident.getIncidentid());

        ObjectMapper mapper = new ObjectMapper();

        String message = mapper.writeValueAsString(incident);
        try {
            getRabbitProducer().produce(topic, message);
            log.debug("Sent to topic: {}\n{}", topic, message);
        } catch(IOException e) {
            log.warn("Failed to send Incident to topic: {}", topic);
        } catch(AlreadyClosedException e) {
            log.error("Exception RabbitMQ attempting to use a closed channel ", e);
        } catch(Exception e) {
            log.error("Exception RabbitMQ exception occurred.", e);
        }
    }

    /**
     * Helper method to extract list of orgIds from a set of IncidentOrg objects
     *
     * @see IncidentServiceImpl#notifyIncidentToOrgs(Integer, Incident, List)
     */
    private void notifyIncidentOrgsToOrgs(Integer workspaceId, Incident incident,
                                          Set<IncidentOrg> incidentOrgs) throws Exception {
        List<Integer> orgIds = new ArrayList<>();
        for(IncidentOrg incidentOrg : incidentOrgs) {
            orgIds.add(incidentOrg.getOrgid());
        }

        notifyIncidentToOrgs(workspaceId, incident, orgIds);
    }

    /**
     * Sends removal message containing incidentId and a list of orgIds that are allowed to see the incident
     *
     * @param workspaceId the workspace in use
     * @param incidentId  the incident the orgIds are attached to
     * @param orgIds      the orgs allowed to see the incident
     * @throws Exception when there's a failure serializing the message for putting on the bus
     */
    private void notifyIncidentRemovalToAll(Integer workspaceId, Integer incidentId, List<Integer> orgIds)
            throws Exception {

        String topicTemplate = "iweb.NICS.ws.%d.incidentorg.remove";
        String topic;
        Map<String, Object> incidentOrgs = new HashMap<>();
        incidentOrgs.put("incidentId", incidentId);
        incidentOrgs.put("orgIds", orgIds);
        ObjectMapper mapper = new ObjectMapper();
        String message;

        topic = String.format(topicTemplate, workspaceId);
        message = mapper.writeValueAsString(incidentOrgs);
        getRabbitProducer().produce(topic, message);

        log.debug("Sent to topic: {}\n{}", topic, message);
    }

    /**
     * Sends appropriate event over appropriate topic when an incident_org mapping has been added.
     * <ul>
     *     <li>
     *         If there was already an existing mapping for this Incident, then the new orgIds that
     *         were just added are sent the Incident, since they now have permission
     *     </li>
     *     <li>
     *         If this is the first mapping for this Incident, then a remove message is sent to
     *         all orgs, which contains a list of the orgIds that are allowed to see the Incident. If
     *         the client isn't in the org/orgs, then they have to remove the Incident from their client.
     *     </li>
     * </ul>
     *
     * @param workspaceId      the workspaceId these Incidents and Orgs fall under
     * @param incidentId       the id of the Incident the mappings belong to
     * @param restrictedOrgIds the orgIds of the incident_org mapping(s) added
     * @param before           the orgIds that existed before the addition that triggered this method
     * @throws Exception when there's a failure serializing the message to put on the rabbit bus
     */
    private void notifyIncidentOrgAdded(Integer workspaceId, Integer incidentId,
                                        List<Integer> restrictedOrgIds, List<Integer> before) throws Exception {
        Incident incident = incidentDao.getIncident(incidentId);

        // If no orgs were previously restricted, we have to notify all users not in these newly
        // restricted orgs to remove the incident from their UI
        if(before.isEmpty()) {
            notifyIncidentRemovalToAll(workspaceId, incidentId, restrictedOrgIds);
        }

        // Notify newly restricted orgs they can add the incident, if they don't already have it
        notifyIncidentToOrgs(workspaceId, incident, restrictedOrgIds);

        // Notify the incident was updated
        // TODO: Need to decide if both a notifyIncidentToOrgs AND this update is necessary. The
        //  notifyIncidentToOrgs is preferred, since it includes the orgids added. I don't think the
        //  incident entity being pushed contains the incidentorg mappings. Just also need to ensure
        //  the incident doesn't get added twice if both of these are called?
        notifyIncidentUpdate(incident);
    }

    /**
     * Sends appropriate event over appropriate topic when an incident_org mapping has been removed.
     * <ul>
     *     <li>
     *         If after removal there are no incident_org mappings for this Incident, everyone gets sent
     *         the Incident
     *     </li>
     *     <li>
     *         If after removal there is still at least one entry for this Incident, each org
     *         that was removed is no longer allowed to see the Incident, so they receive
     *         a message telling them to remove that Incident from their client
     *     </li>
     * </ul>
     *
     * @param workspaceId   the workspaceId these Incidents and Orgs fall under
     * @param incidentId    the id of the Incident the mappings belong to
     * @param orgIdsRemoved the orgIds of the incident_org mapping(s) removed
     * @param before        the orgIds that existed before the removal that triggered this method
     * @throws Exception when there's a failure serializing the message to put on the rabbit bus
     */
    private void notifyIncidentOrgRemoved(Integer workspaceId, Integer incidentId, List<Integer> orgIdsRemoved,
                                          List<Integer> before) throws Exception {

        // Grab the latest mappings for this incident for comparison
        List<Integer> latest = incidentDao.getIncidentOrgIds(incidentId);

        if(latest.isEmpty() && !before.isEmpty()) {
            // Incident was locked down to an org/orgs before, but now isn't, so
            // everyone should see it. (No need to send to orgs in "before", but
            // is it worth the work?)

            Incident incident = incidentDao.getIncident(incidentId);
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(incident);

            String topic = String.format("iweb.NICS.ws.%d.newIncident", workspaceId);
            getRabbitProducer().produce(topic, message);
            log.debug("Sent to topic: {}\n{}", topic, message);

        } else if(!latest.isEmpty()) {
            // The incident is still locked to an org/orgs, so send removal to those
            // orgs whose permission was just revoked

            String topic;

            Map<String, Object> incidentIdToRemove = new HashMap<>();
            incidentIdToRemove.put("incidentId", incidentId);
            // Include list of orgs with access, so client can check if they have access via another org
            incidentIdToRemove.put("orgIds", latest);
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(incidentIdToRemove);

            for(Integer orgId : orgIdsRemoved) {
                try {
                    topic = String.format("iweb.NICS.ws.%d.incidentorg.%d.remove", workspaceId, orgId);
                    getRabbitProducer().produce(topic, message);
                    log.debug("Sent to topic: {}\n{}", topic, message);
                } catch(IOException e) {
                    log.error("Error sending remove incident message", e);
                }
            }
        } else {
            log.debug("NOT sending a notification:\n\tLatest: {}\n\tBefore: {}",
                    Arrays.toString(latest.toArray()),
                    Arrays.toString(before.toArray()));
        }
        this.notifyIncidentUpdate(incidentDao.getIncident(incidentId));
    }

    boolean isNotAdminOrSuper(String username) {
        return !userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID) &&
                !userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID);
    }

    @Override
    public Response getIncidentTypeFormTypes(int workspaceId, int incidentId, String requestingUser) {

        Response response = null;
        IncidentServiceResponse serviceResponse = new IncidentServiceResponse();

        try {
            if(incidentId <= 0) {
                String message = String.format(" Fail. Bad incidentId (%d)", incidentId);
                serviceResponse.setMessage(message);
                return Response.ok(serviceResponse).status(Status.PRECONDITION_FAILED).build();
            }

            List<FormType> reportTypes = incidentDao.getIncidentFormTypes(workspaceId, incidentId);
            serviceResponse.setFormTypes(reportTypes);
            serviceResponse.setMessage("Success");

            response = Response.ok(serviceResponse).status(Status.OK).build();

        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }


    @Override
    public Response createIncidentTypeFormType(int workspaceId, int incidentTypeId, List<Integer> formTypeIds,
                                               String requestingUser) {
        Response response = null;
        IncidentServiceResponse serviceResponse = new IncidentServiceResponse();

        try {
            if(isNotAdminOrSuper(requestingUser)) {
                serviceResponse
                        .setMessage("Fail. User is not authorized to create Incident Type FormType mappings");
                return Response.ok(serviceResponse).status(Status.UNAUTHORIZED).build();
            }

            if(incidentTypeId <= 0 || formTypeIds == null || formTypeIds.isEmpty()) {
                String message = String.format(" Fail. Bad incidentTypeId (%d) or formTypeId (%d)",
                        incidentTypeId, formTypeIds);
                serviceResponse.setMessage(message);
                return Response.ok(serviceResponse).status(Status.PRECONDITION_FAILED).build();
            }

            Status createStatus = Status.CREATED;
            int result = incidentDao.createIncidentTypeReportTypes(workspaceId, incidentTypeId, formTypeIds);

            serviceResponse.setCount(result);
            serviceResponse.setMessage(String.format("Created %d of %d mappings",
                    result, formTypeIds.size()));

            // TODO: see if we want to return the actual mapping id or not?
            response = Response.ok(serviceResponse).status(createStatus).build();

            if(result >= 1) {
                // TODO: send actual formtypes, or just a notification, letting client poll
                notifyIncidentTypeFormTypeAdded(incidentTypeId);
            }

        } catch(DuplicateKeyException e) {
            response = Response.ok("That mapping already exists").status(Status.CONFLICT).build();
        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    public void notifyIncidentTypeFormTypeAdded(int incidentTypeId) {
        String topic = "iweb.NICS.incident.incidenttype.formType.added";
        ObjectMapper mapper = new ObjectMapper();

        try {
            String message = mapper.writeValueAsString(incidentTypeId);
            getRabbitProducer().produce(topic, message);
            log.debug("Sent to topic: {}\n{}", topic, message);
        } catch(IOException e) {
            log.warn("Failed to send Incident to topic: {}", topic);
        } catch(AlreadyClosedException e) {
            log.error("Exception RabbitMQ attempting to use a closed channel ", e);
        } catch(Exception e) {
            log.error("Exception RabbitMQ exception occurred.", e);
        }
    }

    @Override
    public Response deleteIncidentTypeFormType(int workspaceId, int incidentTypeReportTypeId,
                                               String requestingUser) {
        Response response = null;
        IncidentServiceResponse serviceResponse = new IncidentServiceResponse();

        try {
            if(isNotAdminOrSuper(requestingUser)) {
                serviceResponse
                        .setMessage("Fail. User is not authorized to delete IncidentType FormType mappings");
                return Response.ok(serviceResponse).status(Status.UNAUTHORIZED).build();
            }

            if(incidentTypeReportTypeId <= 0) {
                String message = String.format(" Fail. Bad incidentTypeReportTypeId (%d)",
                        incidentTypeReportTypeId);
                serviceResponse.setMessage(message);
                return Response.ok(serviceResponse).status(Status.PRECONDITION_FAILED).build();
            }

            Status createStatus = Status.CREATED;
            boolean result = incidentDao.deleteIncidentTypeReportTypeById(workspaceId, incidentTypeReportTypeId);
            if(result) {
                serviceResponse.setMessage("Success");
            } else {
                serviceResponse.setMessage("Fail");
                createStatus = Status.INTERNAL_SERVER_ERROR;
            }

            response = Response.ok(serviceResponse).status(createStatus).build();
        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    @Override
    public Response deleteIncidentTypeFormTypes(int workspaceId, int incidentTypeId, List<Integer> formTypeIds,
                                                String requestingUser) {
        Response response = null;
        IncidentServiceResponse serviceResponse = new IncidentServiceResponse();

        try {
            if(isNotAdminOrSuper(requestingUser)) {
                serviceResponse
                        .setMessage("Fail. User is not authorized to delete IncidentType FormType mappings");
                return Response.ok(serviceResponse).status(Status.UNAUTHORIZED).build();
            }

            if(incidentTypeId <= 0) {
                String message = String.format(" Fail. Bad incidentTypeId (%d)",
                        incidentTypeId);
                serviceResponse.setMessage(message);
                return Response.ok(serviceResponse).status(Status.PRECONDITION_FAILED).build();
            }

            Status createStatus = Status.OK;
            int result = incidentDao.deleteIncidentTypeReportTypes(workspaceId, incidentTypeId, formTypeIds);
            serviceResponse.setCount(result);
            serviceResponse.setMessage(String.format("Deleted %d/%d mappings",
                    result, formTypeIds.size()));

            response = Response.ok(serviceResponse).status(createStatus).build();
        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    // Utility Methods

    private Response makeIllegalOpRequestResponse() {
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        incidentResponse.setMessage("Request ignored.");
        Response response = Response.notModified("Illegal operation requested").
                status(Status.FORBIDDEN).build();
        return response;
    }

    private Response makeUnsupportedOpRequestResponse() {
        IncidentServiceResponse incidentResponse = new IncidentServiceResponse();
        incidentResponse.setMessage("Request ignored.");
        Response response = Response.notModified("Unsupported operation requested").
                status(Status.NOT_IMPLEMENTED).build();
        return response;
    }

    /**
     * Get Rabbit producer to send message
     *
     * @return
     *
     * @throws IOException
     */
    private RabbitPubSubProducer getRabbitProducer()
            throws IOException, TimeoutException, AlreadyClosedException {
        if(rabbitProducer == null) {
            rabbitProducer = RabbitFactory.makeRabbitPubSubProducer(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
        }
        return rabbitProducer;
    }

    /**
     * Utility method for getting the user from the username/CUSTOM-uid header param
     *
     * @param username the username of the user to fetch
     * @return the user if it exists, null otherwise
     */
    private User getUserByName(String username) {
        User user = null;
        try {
            user = userDao.getUser(username);
        } catch(Exception e) {
            log.error("Exception getting {}", username, e);
        }

        return user;
    }
}

