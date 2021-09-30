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

import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.OrgIncidentType;

@Path("/orgs/{workspaceId}")
public interface OrganizationService {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getOrganizations(@PathParam("workspaceId") Integer workspaceId, @QueryParam("userId") Integer userId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/admin")
    Response getAdminOrgs(@PathParam("workspaceId") Integer workspaceId, @QueryParam("userId") Integer userId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/all")
    Response getAllOrganizations();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/types")
    Response getOrganizationTypes();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/typemap")
    Response getOrganizationTypeMap();


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/adminlist/{orgId}")
    Response getOrgAdminList(@PathParam("orgId") Integer orgId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/org/{orgName}")
    Response getOrganization(@PathParam("orgName") String orgName);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/org/id/{orgId}")
    Response getOrganizationById(@PathParam("orgId") int orgId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/org/name/{orgId}")
    Response getOrganizationName(@PathParam("orgId") int orgId);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    Response postOrganization(Org org,
                              @HeaderParam("X-Remote-User") String requestingUser,
                              @PathParam("workspaceId") Integer workspaceId);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/orgtype/add/{orgId}/{orgTypeId}")
    Response postOrgOrgType(
            @PathParam("orgId") int orgId,
            @PathParam("orgTypeId") int orgTypeId);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/orgtype/remove/{orgId}/{orgTypeId}")
    Response removeOrgOrgType(
            @PathParam("orgId") int orgId,
            @PathParam("orgTypeId") int orgTypeId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttype/{orgId}")
    Response getOrgIncidentTypes(
            @PathParam("orgId") int orgId);

    /**
     * Responsible for retrieving all Organizations that have the specified Incident Type identifier set as
     * a default type. The orgid/name mappings are placed in the {@link OrganizationServiceResponse#getOrgIdNameMap()}
     * property of the response.
     *
     * @param incidentTypeId the Incident Type identifier to query for being registered as defaults for Organizations
     *
     * @return a Response containing a {@link OrganizationServiceResponse} containing a list of orgid/name pairs
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttype/{incidentTypeId}/default")
    Response getOrgsWithDefaultIncidentType(@PathParam("incidentTypeId") int incidentTypeId);

    /**
     * Responsible for retrieving all Organizations that have the specified Incident Type identifier set
     * as either Active or Inactive. The orgid/name mappings are placed in the
     * {@link OrganizationServiceResponse#getOrgIdNameMap()} property of the response.
     *
     * @param incidentTypeId the Incident Type identifier to query
     * @param active whether to filter on the Incident Type being active or inactive
     *
     * @return a Response containing a {@link OrganizationServiceResponse} containing a list of orgid/name pairs
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttype/{incidentTypeId}/active/{active}")
    Response getOrgsWithActiveIncidentType(@PathParam("incidentTypeId") int incidentTypeId,
                                           @PathParam("active") boolean active);

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/incidenttype/{incidentId}/org")
	Response getOrgsWithIncidentType(
			@PathParam("incidentId") int incidentId);

	@POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/incidenttype/add/{orgId}")
    Response postOrgIncidentType(
            @PathParam("orgId") int orgId,
            List<Integer> incidentTypeList,
            @HeaderParam("X-Remote-User") String requestingUser);

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/incidenttype/default/{orgId}")
	Response postOrgIncidentDefaultType(
			@PathParam("orgId") int orgId,
			OrgIncidentType orgIncidentType,
			@HeaderParam("X-Remote-User") String requestingUser);

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/incidenttype/remove/{orgId}")
    Response removeOrgIncidentType(
            @PathParam("orgId") int orgId,
            List<Integer> incidentTypeList,
            @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/caps")
    Response getCaps();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/orgcaps/{orgId}")
    Response getOrgCaps(@PathParam("orgId") int orgId);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/orgcaps/{orgCapId}")
    Response postOrgCaps(@PathParam("orgCapId") int orgCapId,
                         @QueryParam("activeWeb") String activeWeb,
                         @QueryParam("activeMobile") String activeMobile);
}
