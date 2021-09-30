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
package edu.mit.ll.em.api.rs;

import edu.mit.ll.nics.common.entity.IncidentType;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.dao.DataAccessException;

import edu.mit.ll.em.api.exception.DuplicateCollabRoomException;
import edu.mit.ll.nics.common.entity.Incident;
import edu.mit.ll.nics.common.entity.IncidentOrg;

@Path("/incidents/{workspaceId}")
public interface IncidentService {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getIncidents(
            @PathParam("workspaceId") Integer workspaceId,
            @QueryParam("accessibleByUserId") Integer userId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/getincidenttree")
    Response getIncidentsTree(
            @PathParam("workspaceId") Integer workspaceId,
            @QueryParam("accessibleByUserId") Integer userId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/incidentorgs")
    Response getActiveIncidentOrg(
            @PathParam("workspaceId") Integer workspaceId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/archived/{orgId}/{folderId}")
    public Response getArchivedIncidents(
            @PathParam("workspaceId") Integer workspaceId,
            @PathParam("orgId") Integer orgId,
            @PathParam("folderId") String folderId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/active/{orgId}")
    Response getActiveIncidents(
            @PathParam("workspaceId") Integer workspaceId,
            @PathParam("orgId") Integer orgId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/find")
    public Response findIncidents(
            @PathParam("workspaceId") Integer workspaceId,
            @QueryParam("orgPrefix") String orgPrefix,
            @QueryParam("name") String name,
            @DefaultValue("-1") @QueryParam("incidentTypeId") int incidentTypeId,
            @QueryParam("archived") boolean archived,
            @QueryParam("") QueryConstraintParms optionalParams,
            @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/owner/{incidentId}")
    public Response getIncidentOwner(
            @PathParam("workspaceId") Integer workspaceId,
            @PathParam("incidentId") Integer incidentId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/update")
    Response updateIncident(
            @PathParam("workspaceId") Integer workspaceId,
            Incident incident,
            @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/archive/{incidentId}/{folderId}")
    public Response archiveIncident(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("incidentId") int incidentId,
            @HeaderParam("X-Remote-User") String user,
            @PathParam("folderId") String folderId);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/activate/{incidentId}")
    Response activateIncident(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("incidentId") int incidentId,
            @HeaderParam("X-Remote-User") String user);
	
	/*@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteIncidents(@PathParam("workspaceId") Integer workspaceId);*/

	/*@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response putIncidents(
			@PathParam("workspaceId") Integer workspaceId,
			Collection<Incident> incidents);*/

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postIncident(
            @PathParam("workspaceId") Integer workspaceId,
            @QueryParam("orgId") Integer orgId,
            @QueryParam("userId") Integer userId,
            Incident incident)
            throws DataAccessException, DuplicateCollabRoomException, Exception;

    @GET
    @Path(value = "/incidenttype")
    @Produces(MediaType.APPLICATION_JSON)
    Response getIncidentTypes();


    /**
     * Gets any IncidentOrgs for the specified incidentId. Only admin and higher can see entries. TODO: verify
     *
     * @param incidentId the ID of the incident to get incident_orgs for
     * @return a response containing the list of IncidentOrgs, if any are found.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/orgs/{incidentId}")
    Response getIncidentOrgs(@PathParam("incidentId") Integer incidentId);

	/*@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/orgs/notin/{orgId}")
	public Response getOrgIncidentIdsNotInOrg(@PathParam("orgId") Integer orgId);*/


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/orgs/{incidentId}")
    Response postIncidentOrgs(List<IncidentOrg> incidentOrgs,
                              @PathParam("incidentId") Integer incidentId,
                              @HeaderParam("X-Remote-User") String requestingUser,
                              @PathParam("workspaceId") Integer workspaceId);

	/* TODO: Disallowing for now, but could add back with improvements, and
		the dao call would need refactored like the add/remove calls.
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/orgs/update/{incidentId}")
	public Response putOrgIncidents(List<IncidentOrg> orgIncident,
									@PathParam("incidentId") Integer incidentId,
									@HeaderParam("X-Remote-User") String requestingUser,
									@PathParam("workspaceId") Integer workspaceId);*/

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/orgs/remove/{incidentId}")
    Response deleteIncidentOrgs(List<IncidentOrg> incidentOrgs,
                                @PathParam("incidentId") Integer incidentId,
                                @HeaderParam("X-Remote-User") String requestingUser,
                                @PathParam("workspaceId") Integer workspaceId);

    /**
     * Get the incident folder
     *
     * @param incidentId the ID of the incident to get incident_orgs for
     * @return a response containing the list of IncidentOrgs, if any are found.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/folder/{incidentId}")
    Response getIncidentFolder(@PathParam("incidentId") Integer incidentId);

    /**
     * Read all IncidentTypes. Currently the workspaceId is NOT used.
     *
     * @param workspaceId the id of the workspace to get IncidentTypes for
     * @param requestingUser the identity username of the user making the request
     *
     * @return an {@link IncidentServiceResponse} containing a list of IncidentTypes if successful,
     *          otherwise, an error response (Unauthorized, Internal Server Error, Precondition failed,
     *          expectation failed).
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttypes")
    Response readIncidentTypes(@PathParam("workspaceId") int workspaceId,
                               @HeaderParam("X-Remote-User") String requestingUser);

    /**
     * Create a new IncidentType. workspaceId is NOT used. Returns a 201 CREATED if successful.
     *
     * @param workspaceId the id of the workspace the user is in
     * @param requestingUser the identity username of the user making the request
     * @param incidentType the new IncidentType to create
     *
     * @return an {@link IncidentServiceResponse} containing the new IncidentType if successful, otherwise
     *          an appropriate error response (Unauthorized, Expectation Failed, Precondition Failed,
     *          Internal Server Error)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttypes")
    Response createIncidentType(@PathParam("workspaceId") int workspaceId,
                                @HeaderParam("X-Remote-User") String requestingUser,
                                IncidentType incidentType);

    /**
     * Updates the specified IncidentType. workspaceId is NOT used. Returns a 200 OK if successful. The path
     * parameter incidentTypeId and the incidentTypeId set on the IncidentType entity must match.
     *
     * @param workspaceId the id of the workspace the user is in
     * @param requestingUser the identity username of the user making the request
     * @param incidentTypeId the incidentTypeId of the IncidentType to update
     * @param incidentType the IncidentType to update
     *
     * @return an {@link IncidentServiceResponse} containing the updated IncidentType if successful, otherwise,
     *          an appropriate error response (Unauthorized, Expectation Failed, Precondition Failed, Internal
     *          Server Error)
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttypes/{incidentTypeId}")
    Response updateIncidentType(@PathParam("workspaceId") int workspaceId,
                                @HeaderParam("X-Remote-User") String requestingUser,
                                @PathParam("incidentTypeId") int incidentTypeId,
                                IncidentType incidentType);

    /**
     * Delete the specified IncidentType.
     *
     * @param workspaceId the id of the workspace the user is in
     * @param requestingUser the identity username of the user making the request
     * @param incidentTypeId the id of the IncidentType to delete
     *
     * @return a 200 OK with an empty IncidentServiceResponse if successful, otherwise, an appropriate error
     *          response
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttypes/{incidentTypeId}")
    Response deleteIncidentType(@PathParam("workspaceId") int workspaceId,
                                @HeaderParam("X-Remote-User") String requestingUser,
                                @PathParam("incidentTypeId") int incidentTypeId);


    /* NOTES:
        - What will the client have on hand to call these... should orgid be specified?
        - currently gets one reporttype for an incidenttypeid... but really, incidents can have more than one type
            so need to make that work, even if MKD "plans" to only use 1 incidenttype?
     */

    /**
     * Intent is to get a list of ReportType (really FormType) associated with the IncidentTypes
     * this organization is registered to.
     *
     * @param workspaceId the ID of the workspace
     * @param incidentId the ID of the Incident to get the associated FormTypes for
     * @param requestingUser the username of the user making the request
     *
     * @return A OrgIncidentTypeServiceResponse containing the list of FormTypes if successful, an appropriate
     *          error response otherwise
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/formtype/incident/{incidentId}")
    Response getIncidentTypeFormTypes(@PathParam("workspaceId") int workspaceId,
                                        @PathParam("incidentId") int incidentId,
                                        @HeaderParam("X-Remote-User") String requestingUser);

    // TODO: should allow for associating multiple formtypes with incidenttype in one call
    // TODO: should probably allow posting of FORMTYPENAMEs, since formtypeids can differ on different systems
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/formtype/incidenttype/{incidentTypeId}")
    Response createIncidentTypeFormType(@PathParam("workspaceId") int workspaceId,
                                        @PathParam("incidentTypeId") int incidentTypeId,
                                        List<Integer> reportTypeIds,
                                        @HeaderParam("X-Remote-User") String requestingUser);

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/formtype/incidenttypereporttype/{incidentTypeFormTypeId}")
    Response deleteIncidentTypeFormType(@PathParam("workspaceId") int workspaceId,
                                        @PathParam("incidentTypeFormTypeId") int incidentTypeFormTypeId,
                                        @HeaderParam("X-Remote-User") String requestingUser);

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/formtype/incidenttype/{incidentTypeId}")
    Response deleteIncidentTypeFormTypes(@PathParam("workspaceId") int workspaceId,
                                         @PathParam("incidentTypeId") int incidentTypeId,
                                         List<Integer> formTypeIds,
                                         @HeaderParam("X-Remote-User") String requestingUser);










	/*@GET
	@Path(value = "/count")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getIncidentCount(@PathParam("workspaceId") Integer workspaceId);

	@GET
	@Path(value = "/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchIncidentResources();	
	
	@GET
	@Path(value = "/{incidentId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getIncident(
			@PathParam("workspaceId") Integer workspaceId,
			@PathParam("incidentId") int incidentId);
	
	@GET
	@Path(value = "/{incidentId}/notification")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getIncidentNotificationForm(
			@PathParam("workspaceId") Integer workspaceId,
			@PathParam("incidentId") int incidentId);

	@DELETE
	@Path(value = "/{incidentId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteIncident(
			@PathParam("workspaceId") Integer workspaceId,
			@PathParam("incidentId") int incidentId);

	@PUT
	@Path(value = "/{incidentId}")
    @Consumes(MediaType.APPLICATION_JSON)	
	@Produces(MediaType.APPLICATION_JSON)
	public Response putIncident(
			@PathParam("workspaceId") Integer workspaceId,
			@PathParam("incidentId") int incidentId, Incident incident);*/
}

