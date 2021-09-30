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

import edu.mit.ll.em.api.rs.OrgIncidentTypeServiceResponse;
import edu.mit.ll.em.api.rs.OrgOrgTypeServiceResponse;
import edu.mit.ll.em.api.rs.OrganizationService;
import edu.mit.ll.em.api.rs.OrganizationServiceResponse;
import edu.mit.ll.em.api.rs.UserService;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.Cap;
import edu.mit.ll.nics.common.entity.IncidentOrg;
import edu.mit.ll.nics.common.entity.IncidentType;
import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.OrgCap;
import edu.mit.ll.nics.common.entity.OrgOrgType;
import edu.mit.ll.nics.common.entity.OrgType;
import edu.mit.ll.nics.common.entity.OrgIncidentType;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;


/**
 * Service endpoint for SSO management calls like creating and modifying users, and other SSO related management
 */
public class OrganizationServiceImpl implements OrganizationService {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(OrganizationServiceImpl.class);

    /**
     * Organization DAO
     */
    private static final OrgDAOImpl orgDao = new OrgDAOImpl();
    private static final UserDAOImpl userDao = new UserDAOImpl();
    private static final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();
    private static final IncidentDAOImpl incidentDao = new IncidentDAOImpl();

    private static final String DATA_ACCESS_ERROR = "Data Access Error";
    private static final String UNHANDLED_EXCEPTION = "Unhandled Exception";
    private static final String PERSISTING_ORG_EXCEPTION = "Unhandled exception while persisting organization.";

    private static final String ADD = "add";
    private static final String REMOVE = "remove";

    private RabbitPubSubProducer rabbitProducer;

    /**
     * Returns a OrganizationServiceResponse with the organizations list set to all organizations in the database
     *
     * @return Response
     * @see OrganizationServiceResponse
     */
    public Response getAllOrganizations() {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();

        List<Org> allOrgs = null;
        try {
            allOrgs = orgDao.getOrganizations();
            organizationResponse.setOrganizations(allOrgs);
            organizationResponse.setMessage(Status.OK.getReasonPhrase());
            if(allOrgs != null) {
                organizationResponse.setCount(allOrgs.size());
            }
            response = Response.ok(organizationResponse).status(Status.OK).build();
        } catch(Exception e) {
            organizationResponse.setMessage("Unable to read all organizations.");
            log.error("Unhandled exception while querying getAllOrganizations()", e);
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }


    /**
     * Read and return orgs for specified user and workspace ids
     *
     * @return Response
     *
     * @see OrganizationServiceResponse
     */
    public Response getOrganizations(Integer workspaceId, Integer userId) {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        List<Org> organizations = null;
        try {
            organizations = orgDao.getUserOrgs(userId, workspaceId);
            organizationResponse.getOrganizations().addAll(organizations);
            organizationResponse.setMessage("ok");
            if(organizations != null) {
                organizationResponse.setCount(organizationResponse.getOrganizations().size());
            }
            response = Response.ok(organizationResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            organizationResponse.setMessage(DATA_ACCESS_ERROR);
            organizationResponse.setCount(organizationResponse.getOrganizations().size());
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            organizationResponse.setMessage(UNHANDLED_EXCEPTION);
            organizationResponse.setCount(organizationResponse.getOrganizations().size());
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    /**
     * Read and return orgs for specified user and workspace ids
     *
     * @return Response
     *
     * @see OrganizationServiceResponse
     */
    public Response getAdminOrgs(Integer workspaceId, Integer userId) {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        List<Org> organizations = null;
        try {
            organizations = orgDao.getAdminOrgs(userId, workspaceId);
            organizationResponse.getOrganizations().addAll(organizations);

            //Get children organizations
            List<Integer> orgIds = new ArrayList<>();
            for(Org org : organizations) {
                orgIds.add(org.getOrgId());
            }

            //Add child organizations to the response
            organizationResponse.getOrganizations().addAll(
                    orgDao.getAdminOrgs(orgDao.getAllChildren(orgIds)));

            organizationResponse.setMessage(Status.OK.getReasonPhrase());
            if(organizations != null) {
                organizationResponse.setCount(organizationResponse.getOrganizations().size());
            }
            response = Response.ok(organizationResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            organizationResponse.setMessage(DATA_ACCESS_ERROR);
            organizationResponse.setCount(organizationResponse.getOrganizations().size());
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            organizationResponse.setMessage(UNHANDLED_EXCEPTION);
            organizationResponse.setCount(organizationResponse.getOrganizations().size());
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }


    /**
     * Returns an OrganizationServiceResponse with orgTypes set to all orgTypes in the database
     *
     * @return Response
     *
     * @See {@link OrganizationServiceResponse}
     */
    public Response getOrganizationTypes() {
        Response response = null;

        OrganizationServiceResponse orgTypeResponse = new OrganizationServiceResponse();
        List<OrgType> orgTypes = null;

        try {
            orgTypes = orgDao.getOrgTypes();
            if(orgTypes != null && !orgTypes.isEmpty()) {
                orgTypeResponse.setOrgTypes(orgTypes);
                orgTypeResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(orgTypeResponse).status(Status.OK).build();
            } else {
                log.debug("OrgDao returned no OrgTypes");
            }
        } catch(Exception e) {
            orgTypeResponse.setMessage(UNHANDLED_EXCEPTION);
            log.error("Unhandled exception while querying getOrganizationTypes()", e);
            orgTypeResponse.setCount(orgTypeResponse.getOrgTypes().size());
            response = Response.ok(orgTypeResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }


    @Override
    public Response getOrganizationTypeMap() {

        Response response = null;

        OrganizationServiceResponse orgTypeMapResponse = new OrganizationServiceResponse();
        List<OrgOrgType> orgTypesMap = null;

        try {
            orgTypesMap = orgDao.getOrgOrgTypes();
            if(orgTypesMap != null && !orgTypesMap.isEmpty()) {
                orgTypeMapResponse.setOrgOrgTypes(orgTypesMap);
                orgTypeMapResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(orgTypeMapResponse).status(Status.OK).build();
            } else {
                log.debug("OrgDao returned no OrgOrgTypes");
            }
        } catch(Exception e) {
            orgTypeMapResponse.setMessage(UNHANDLED_EXCEPTION);
            log.error("Unhandled exception while querying getOrgOrgTypes()", e);
            orgTypeMapResponse.setCount(orgTypeMapResponse.getOrgOrgTypes().size());
            response = Response.ok(orgTypeMapResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Returns a admin list set to an  organization's distribution list in the database based on incidentId
     *
     * @return Response
     * @see OrganizationServiceResponse
     */
    public Response getOrgAdminList(Integer orgId) {
        Response response = null;
        OrganizationServiceResponse orgAdminListResponse = new OrganizationServiceResponse();
        List<String> orgAdminList = null;
        try {
            log.debug("My orgId is {}", orgId);
            orgAdminList = orgDao.getOrgAdmins(orgId);
            orgAdminListResponse.setOrgAdminList(orgAdminList);
            orgAdminListResponse.setMessage(Status.OK.getReasonPhrase());

            response = Response.ok(orgAdminListResponse).status(Status.OK).build();
        } catch(Exception e) {
            orgAdminListResponse.setMessage(UNHANDLED_EXCEPTION);
            log.error("Unhandled exception while querying getOrgAdminList()", e);
            response = Response.ok(orgAdminListResponse).status(Status.INTERNAL_SERVER_ERROR).build();

        }
        return response;
    }

    public Response postOrganization(Org org, String username, Integer workspaceId) {
        Response response = null;
        OrganizationServiceResponse orgResponse = new OrganizationServiceResponse();
        Org newOrg = null;

        try {
            int orgId = orgDao.addOrg(org);
            newOrg = orgDao.getOrganization(orgId);
            newOrg.setOrgTypes(new HashSet<OrgOrgType>(orgDao.getOrgTypes(orgId)));

            orgResponse.setMessage(Status.OK.getReasonPhrase());
            orgResponse.setOrganizations(Arrays.asList(newOrg));
            orgResponse.setCount(1);
            response = Response.ok(orgResponse).status(Status.OK).build();

            UserService userService = new UserServiceImpl();
            int userId = (new Long(userDao.getUserId(username))).intValue();
            userService.addUserToOrg(Arrays.asList(userId), newOrg.getOrgId(), workspaceId);

            int userOrgId = userOrgDao.getUserOrgId(newOrg.getOrgId(), userId);
            int userOrgWorkspaceId = userOrgDao.getUserOrgWorkspaceId(userOrgId, workspaceId);
            userOrgDao.setUserOrgEnabled(userOrgWorkspaceId, true);
            try {
                notifyUserAddedToOrg(userId, workspaceId, userOrgWorkspaceId);
            } catch(Exception e) {
                e.printStackTrace();
            }
            userOrgDao.setSystemRoleId(userOrgId, SADisplayConstants.ADMIN_ROLE_ID);

        } catch(Exception e) {
            orgResponse.setMessage(PERSISTING_ORG_EXCEPTION);
            response = Response.ok(orgResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
		
		/* Notify Super Users?  
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyChange(newChat);
			} catch (IOException e) {
				log.error("Failed to publish ChatMsgService message event", e);
			}
		}
		*/
    }

    private void notifyUserAddedToOrg(int userId, int workspaceId, int userorgWorkspaceId)
            throws IOException, TimeoutException, AlreadyClosedException {

        final String topic = String.format("iweb.NICS.%d.user.%d.userorgupdate",
                workspaceId, userId);
        //ObjectMapper mapper = new ObjectMapper();
        //String message = mapper.writeValueAsString("Update to userorgworkspace " + userorgWorkspaceId);
        String message = "Update to userorgworkspace " + userorgWorkspaceId;
        getRabbitProducer().produce(topic, message);
    }

    public Response postOrgOrgType(int orgId, int orgTypeId) {
        Response response = null;
        OrgOrgTypeServiceResponse serviceResponse = new OrgOrgTypeServiceResponse();
        try {
            int ret = orgDao.addOrgOrgType(orgId, orgTypeId);
            if(ret == 1) {
                serviceResponse.setOrgId(orgId);
                serviceResponse.setOrgTypeId(orgTypeId);
                response = Response.ok(serviceResponse).status(Status.OK).build();
            } else {
                response = Response.ok("There was an error adding the organziation to the orgtype.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();

        }

        return response;
    }

    /**
     * Returns a OrganizationServiceResponse with an organization based on organization id
     *
     * @return Response
     * @see OrganizationServiceResponse
     */
    public Response getOrganizationById(int orgId) {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        Org oneOrg = null;

        try {
            oneOrg = orgDao.getOrganization(orgId);
            organizationResponse.getOrganizations().add(oneOrg);
            organizationResponse.setMessage(Status.OK.getReasonPhrase());
            if(oneOrg != null) {
                organizationResponse.setCount(1);
            }
            response = Response.ok(organizationResponse).status(Status.OK).build();
        } catch(Exception e) {
            organizationResponse.setMessage("Unable to read organization.");
            log.error("Unhandled exception while querying getOrganization()", e);
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Returns a OrganizationServiceResponse with an organization based on organization name
     *
     * @return Response
     * @see OrganizationServiceResponse
     */
    public Response getOrganization(String orgName) {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        Org oneOrg = null;

        try {
            oneOrg = orgDao.getOrganization(orgName);
            organizationResponse.getOrganizations().add(oneOrg);
            organizationResponse.setMessage(Status.OK.getReasonPhrase());
            if(oneOrg != null) {
                organizationResponse.setCount(1);
            }
            response = Response.ok(organizationResponse).status(Status.OK).build();
        } catch(Exception e) {
            organizationResponse.setMessage("Unable to read organization.");
            log.error("Unhandled exception while querying getOrganization()", e);
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Returns a OrganizationServiceResponse with an organization based on organization name
     *
     * @return Response
     * @see OrganizationServiceResponse
     */
    public Response getOrganizationName(int orgId) {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();

        try {
            String name = orgDao.getOrganization(orgId).getName();
            organizationResponse.setMessage(name);
            if(name != null) {
                organizationResponse.setCount(1);
            }
            response = Response.ok(organizationResponse).status(Status.OK).build();
        } catch(Exception e) {
            organizationResponse.setMessage("Unable to read organization.");
            log.error("Unhandled exception while querying getOrganization()", e);
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    public Response removeOrgOrgType(int orgId, int orgTypeId) {
        Response response = null;
        OrgOrgTypeServiceResponse serviceResponse = new OrgOrgTypeServiceResponse();

        try {
            int ret = orgDao.removeOrgOrgType(orgId, orgTypeId);
            if(ret == 1) {
                serviceResponse.setOrgId(orgId);
                serviceResponse.setOrgTypeId(orgTypeId);
                response = Response.ok(serviceResponse).status(Status.OK).build();
            } else {
                response = Response.ok("There was an error removing the organization from the orgtype.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    public Response getCaps() {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        List<Cap> caps = null;

        try {
            caps = orgDao.getCaps();


            if(caps != null) {
                organizationResponse.setCaps(caps);
                organizationResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(organizationResponse).status(Status.OK).build();
            } else {
                response = Response.ok("There was an error retrieving the orgcaps.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }
            return response;
        } catch(Exception e) {
            log.error("Unhandled exception while retrieving caps", e);
            organizationResponse.setMessage("failure. Unable to read caps.");
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return null;
    }

    public Response getOrgCaps(int orgId) {
        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        List<OrgCap> orgCaps = null;

        try {
            orgCaps = orgDao.getOrgCaps(orgId);
            if(orgCaps != null) {
                organizationResponse.setOrgCaps(orgCaps);
                organizationResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(organizationResponse).status(Status.OK).build();
            } else {
                response = Response.ok("There was an error retrieving the orgcaps.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }

        } catch(Exception e) {
            log.error("Unhandled exception while querying OrgCaps", e);
            organizationResponse.setMessage("failure. Unable to read OrgCaps.");
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }


        return response;
    }


    @Override
    public Response postOrgCaps(int orgCapId, String activeWeb, String activeMobile) {

        Response response = null;
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        OrgCap orgCap = null;

        try {
            orgCap = orgDao.updateOrgCaps(orgCapId, activeWeb, activeMobile);

            if(orgCap != null) {
                organizationResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(organizationResponse).status(Status.OK).build();


                notifyOrgCap(orgCap);

            } else {
                response = Response.ok("There was an error updating the orgcaps.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }

        } catch(Exception e) {
            log.error("Unhandled exception while updating OrgCaps", e);
            organizationResponse.setMessage("failure. Unable to update postOrgCaps.");
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    @Override
    public Response getOrgIncidentTypes(int orgId) {
        Response response = null;
        OrgIncidentTypeServiceResponse organizationResponse = new OrgIncidentTypeServiceResponse();
        List<IncidentType> incidentTypes = null;
        List<OrgIncidentType> orgIncidentTypes = null;

        try {
            orgIncidentTypes = orgDao.getIncidentTypes(orgId);
            if(orgIncidentTypes != null && orgIncidentTypes.size() > 0) {
                organizationResponse.setActiveIncidentTypes(orgIncidentTypes);
                organizationResponse.setInactiveIncidentTypes(orgDao.getInactiveIncidentTypes(orgId));
                organizationResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(organizationResponse).status(Status.OK).build();
            } else {
                incidentTypes = incidentDao.getIncidentTypes(true);
                organizationResponse.setDefaultIncidentTypes(incidentTypes);
                organizationResponse.setInactiveIncidentTypes(incidentDao.getIncidentTypes(false));
                organizationResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(organizationResponse).status(Status.OK).build();
            }

        } catch(Exception e) {
            log.error("Unhandled exception while querying OrgIncidentTypes", e);
            organizationResponse.setMessage("failure. Unable to read OrgIncidentTypes.");
            response = Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

	public Response postOrgIncidentDefaultType(int orgId, OrgIncidentType orgIncidentType, String requestingUser){
        Response response = null;
        OrgIncidentTypeServiceResponse serviceResponse = new OrgIncidentTypeServiceResponse();

        try{
            if(isNotAdminOrSuper(requestingUser)) {
                serviceResponse.setMessage("Fail. User is not authorized to add Incident Types to this Organization");
                return Response.ok(serviceResponse).status(Status.UNAUTHORIZED).build();
            }

            if(orgIncidentType == null || orgIncidentType == null) {

                serviceResponse.setMessage("OrgIncidentTypeId was null or empty");
                return Response.ok(serviceResponse).status(Status.PRECONDITION_FAILED).build();
            }

            int ret = orgDao.updateOrgIncidentType(orgIncidentType);
            if(ret != -1){
                serviceResponse.setMessage("OK");
                serviceResponse.setOrgId(orgId);
                response = Response.ok(serviceResponse).status(Status.OK).build();

                Response latest = this.getOrgIncidentTypes(orgId);
                notifyOrgIncidentType(orgId, latest);
            } else {
                response = Response.ok("There was an error adding the incidenttype to the org.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();

        }

        return response;
    }

	@Override
	public Response postOrgIncidentType(int orgId, List<Integer> orgIncidentTypeList, String requestingUser) {

        Response response = null;
        OrgIncidentTypeServiceResponse serviceResponse = new OrgIncidentTypeServiceResponse();

        try {
            if(isNotAdminOrSuper(requestingUser)) {
                serviceResponse.setMessage("Fail. User is not authorized to add Incident Types to this Organization");
                return Response.ok(serviceResponse).status(Status.UNAUTHORIZED).build();
            }

            if(orgId <= 0 || orgIncidentTypeList == null || orgIncidentTypeList == null ||
                    orgIncidentTypeList.isEmpty()) {

                serviceResponse.setMessage("Fail. Bad orgId(" + orgId + ") or IncidentTypeIds list was null or empty");
                return Response.ok(serviceResponse).status(Status.PRECONDITION_FAILED).build();
            }


            int ret = orgDao.addOrgIncidentTypes(orgId, orgIncidentTypeList);
            if(ret != -1) {
                serviceResponse.setMessage("OK");
                serviceResponse.setOrgId(orgId);
                response = Response.ok(serviceResponse).status(Status.OK).build();

                Response latest = this.getOrgIncidentTypes(orgId);
                notifyOrgIncidentType(orgId, latest);
            } else {
                response = Response.ok("There was an error adding the incidenttype to the org.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();

        }

        return response;
    }

    @Override
    public Response removeOrgIncidentType(int orgId, List<Integer> incidentTypeList, String requestingUser) {
        Response response = null;
        OrgIncidentTypeServiceResponse serviceResponse = new OrgIncidentTypeServiceResponse();

        try {

            if(isNotAdminOrSuper(requestingUser)) {
                serviceResponse
                        .setMessage("Fail. User is not authorized to remove Incident Types from this Organization");
                return Response.ok(serviceResponse).status(Status.UNAUTHORIZED).build();
            }

            if(orgId <= 0 || incidentTypeList == null || incidentTypeList == null ||
                    incidentTypeList.isEmpty()) {

                serviceResponse.setMessage("Fail. Bad orgId(" + orgId + ") or IncidentTypeIds list was null or empty");
                return Response.ok(serviceResponse).status(Status.PRECONDITION_FAILED).build();
            }

            int ret = orgDao.removeOrgIncidentTypes(orgId, incidentTypeList);
            if(ret != -1) {
                serviceResponse.setMessage("OK");
                serviceResponse.setOrgId(orgId);
                response = Response.ok(serviceResponse).status(Status.OK).build();

                Response latest = this.getOrgIncidentTypes(orgId);
                notifyOrgIncidentType(orgId, latest);
            } else {
                response = Response.ok("There was an error removing the incidenttype from the org.")
                        .status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            response = Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

	public Response getOrgsWithIncidentType(int incidentId){
		try{
			OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
			organizationResponse.setOrganizations(orgDao.getOrgsWithIncidentType(incidentId));
			return Response.ok(organizationResponse).status(Status.OK).build();
		}catch(Exception e){
			e.printStackTrace();
			return Response.ok(e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

    @Override
    public Response getOrgsWithDefaultIncidentType(int incidentTypeId) {
        OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        try {
            organizationResponse.setOrgIdNameMap(orgDao.getOrgsWithDefaultIncidentType(incidentTypeId));
            organizationResponse.setMessage("Successfully retrieved Organizations");
            return Response.ok(organizationResponse).status(Status.OK).build();
        } catch(Exception e) {
            log.error("Error retrieving Organizations with default Incident Type of {}", incidentTypeId, e);
            organizationResponse.setMessage("Error retrieving Organizations with default Incident Type");
            return Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public Response getOrgsWithActiveIncidentType(int incidentTypeId, boolean active) {
        final OrganizationServiceResponse organizationResponse = new OrganizationServiceResponse();
        try {
            organizationResponse.setOrgIdNameMap(orgDao.getOrgsWithActiveIncidentType(incidentTypeId,
                    active));
            organizationResponse.setMessage("Successfully retrieved Organizations");
            return Response.ok(organizationResponse).status(Status.OK).build();
        } catch(Exception e) {
            log.error("Error retrieving Organizations with default Incident Type of {}", incidentTypeId, e);
            organizationResponse.setMessage("Error retrieving Organizations with default Incident Type");
            return Response.ok(organizationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    boolean isNotAdminOrSuper(String username) {
	    return !userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID) &&
				!userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID);
    }

    private void notifyOrgCap(OrgCap orgCap)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(orgCap != null) {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(orgCap);
            getRabbitProducer().produce("iweb.nics.orgcaps." + orgCap.getOrgId() + "." +
                    orgCap.getCap().getName(), message);
        }
    }

    private void notifyOrgIncidentType(int orgId, Response response)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(response != null) {
            String topic = String.format("iweb.NICS.orgincidenttypes.%d.update", orgId);

            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(response);
            getRabbitProducer().produce(topic, message);
        }
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

}
