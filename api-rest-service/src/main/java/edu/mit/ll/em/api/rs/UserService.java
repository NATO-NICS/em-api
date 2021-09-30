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

import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/users/{workspaceId}")
public interface UserService {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/whoami")
	Response whoAmI(@HeaderParam("X-Remote-User") String username);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getUsers(@PathParam("workspaceId") int workspaceId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/org/{orgId}")
    Response getUsers(@PathParam("workspaceId") int workspaceId, @PathParam("orgId") int orgId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/admin")
    Response getAdminUsers(
            @HeaderParam("X-Remote-User") String username,
            @PathParam("workspaceId") int workspaceId
    );

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/systemroles")
    Response getSystemRoles();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/active")
    Response getActiveUsers(@PathParam("workspaceId") int workspaceId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/admin/{userOrgId}")
    Response isAdmin(@PathParam("userOrgId") int userOrgId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/super")
    Response isSuperUser(@HeaderParam("X-Remote-User") String username);

    @GET
    @Path(value = "/username/{username}/userOrgId/{userOrgId}/orgId/{orgId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getUserProfile(
            @PathParam("username") String username,
            @PathParam("userOrgId") int userOrgId,
            @PathParam("workspaceId") int workspaceId,
            @PathParam("orgId") int orgId,
            @QueryParam("requestingUserOrgId") int rUserOrgId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/updateprofile")
    Response postUserProfile(User user, @HeaderParam("X-Remote-User") String requestingUser,
                             @QueryParam("requestingUserOrgId") int rUserOrgId);

    /**
     * Gets User with usersessionid filled
     *
     * @param requestingUser the username of the user, read from the identity header, to get the User for
     *
     * @return a UserReponse with the user included, along with usersessionid set if successful, otherwise a
     *      Response with errors that occurred, or not being authorized
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/userWithSession/userorg/{userorgId}")
    Response getUserWithSession(@PathParam("userorgId") int userorgId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/find")
    Response findUser(
            @QueryParam("firstName") String firstName,
            @QueryParam("lastName") String lastName,
            @QueryParam("exact") boolean exact);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/enabled/{orgId}")
    Response getEnabledUsers(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("orgId") int orgId);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/disabled/{orgId}")
    Response getDisabledUsers(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("orgId") int orgId);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/enable/{userOrgWorkspaceId}/userid/{userId}")
    Response setUserEnabled(
            @PathParam("userOrgWorkspaceId") int userOrgWorkspaceId,
            @PathParam("userId") int userId,
            @PathParam("workspaceId") int workspaceId,
            @QueryParam("enabled") boolean enabled,
            @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postUser(@PathParam("workspaceId") int workspaceId, RegisterUser user);


    @POST
    @Path("/{userOrgWorkspaceId}/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response setUserActive(
            @PathParam("userOrgWorkspaceId") int userOrgWorkspaceId,
            @PathParam("userId") int userId,
            @QueryParam("active") boolean active,
            @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Path(value = "/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getUser(@PathParam("userId") int userId);

    @GET
    @Path(value = "/usersessionId/{usersessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getUserBySessionId(@PathParam("usersessionId") long usersessionId);

    @GET
    @Path(value = "/pastUsersessionId/{usersessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getUserByPastSessionId(@PathParam("usersessionId") long usersessionId);


    @GET
    @Path(value = "/userOrgs")
    @Produces(MediaType.APPLICATION_JSON)
    Response getUserOrgs(@PathParam("workspaceId") int workspaceId,
                         @QueryParam("userName") String username,
                         @HeaderParam("X-Remote-User") String requestingUser);

    @GET
    @Path(value = "/systemStatus")
    @Produces(MediaType.APPLICATION_JSON)
    Response getLoginStatus(@PathParam("workspaceId") int workspaceId, @QueryParam("userName") String username);

    @POST
    @Path(value = "/createsession")
    @Produces(MediaType.APPLICATION_JSON)
    Response createUserSession(@QueryParam("userId") int userId, @QueryParam("displayName") String displayName,
                               @QueryParam("userOrgId") int userOrgId, @QueryParam("systemRoleId") int systemRoleId,
                               @PathParam("workspaceId") int workspaceId, @QueryParam("sessionId") String sessionId,
                               @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Path(value = "/removesession")
    @Produces(MediaType.APPLICATION_JSON)
    Response removeUserSession(@QueryParam("userSessionId") int userSessionId,
                               @HeaderParam("X-Remote-User") String requestingUser,
                               @PathParam("workspaceId") int workspaceId);

    @POST
    @Path(value = "/userorg")
    @Produces(MediaType.APPLICATION_JSON)
    Response addUserToOrg(Collection<Integer> userIds, @QueryParam("orgId") int orgId,
                          @PathParam("workspaceId") int workspaceId);

    @GET
    @Path(value = "/contacttypes")
    @Produces(MediaType.APPLICATION_JSON)
    Response getContactTypes();

    @GET
    @Path(value = "/contactinfo")
    @Produces(MediaType.APPLICATION_JSON)
    Response getUsersContactInfo(@PathParam("workspaceId") int workspaceId,
                                 @QueryParam("userName") String userName);

    @POST
    @Path(value = "/updatecontactinfo")
    @Produces(MediaType.APPLICATION_JSON)
    Response addContactInfo(@PathParam("workspaceId") int workspaceId,
                            @QueryParam("userName") String userName,
                            @QueryParam("contactTypeId") int contactTypeId,
                            @QueryParam("value") String value,
                            @HeaderParam("X-Remote-User") String requestingUser);

    @DELETE
    @Path(value = "/deletecontactinfo")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteContactInfo(@PathParam("workspaceId") int workspaceId,
                               @QueryParam("userName") String userName,
                               @QueryParam("contactId") int contactId,
                               @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Path(value = "/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postLogin(Login login,
                              @HeaderParam("X-Session-Id") String sessionId);

    @DELETE
    @Path(value = "/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteLogin(@PathParam("username") String username);
}
