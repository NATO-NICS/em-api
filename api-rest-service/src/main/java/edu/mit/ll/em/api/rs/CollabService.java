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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.dao.DataAccessException;

import edu.mit.ll.em.api.exception.DuplicateCollabRoomException;
import edu.mit.ll.nics.common.entity.CollabRoom;

@Path("/collabroom/{incidentId}")
public interface CollabService {

    @GET
    @Path("/subscribe/{collabroomId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response validateSubscription(
            @PathParam("collabroomId") int collabroomId,
            @HeaderParam("X-Remote-User") String username);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getCollabRoom(
            @PathParam("incidentId") int incidentId,
            @DefaultValue("-1") @QueryParam("userId") Integer userId,
            @HeaderParam("X-Remote-User") String username);

    @GET
    @Path("/users/{collabroomId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCollabRoomSecureUsers(
            @PathParam("collabroomId") int collabRoomId,
            @HeaderParam("X-Remote-User") String username);

    @GET
    @Path("/users/{workspaceId}/unsecure/{collabroomId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCollabRoomUnSecureUsers(
            @PathParam("collabroomId") int collabRoomId,
            @QueryParam("orgId") int orgId,
            @PathParam("workspaceId") int workspaceId,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postCollabRoom(
            @QueryParam("userOrgId") int userOrgId,
            @QueryParam("orgId") int orgId,
            @QueryParam("workspaceId") int workspaceId,
            @PathParam("incidentId") int incidentId,
            CollabRoom collabroom,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/batch")
    Response postCollabRooms(
            @QueryParam("userOrgId") int userOrgId,
            @QueryParam("workspaceId") int workspaceId,
            @PathParam("incidentId") int incidentId,
            List<CollabRoom> collabrooms,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Path("/secure/{collabroomId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateCollabRoomPermission(
            FieldMapResponse secureUsers,
            @PathParam("collabroomId") int collabRoomId,
            @QueryParam("userId") long userId,
            @QueryParam("orgId") int orgId,
            @QueryParam("workspaceId") int workspaceId,
            @HeaderParam("X-Remote-User") String username);

    @GET
    @Path("/{collabroomId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCollabRoomPresence(
            @PathParam("incidentId") int incidentId,
            @PathParam("collabroomId") int collabroomId);

    @POST
    @Path("/{collabroomId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postCollabRoomPresence(
            @PathParam("incidentId") int incidentId,
            @PathParam("collabroomId") int collabroomId,
            CollabPresenceStatus state);

    @POST
    @Path("/collabroomname")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateCollabRoomName(
            CollabRoom collabRoom,
            @HeaderParam("X-Remote-User") String username);

    @DELETE
    @Path("/unsecure/{collabroomId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response unsecureRoom(
            @PathParam("collabroomId") long collabroomId,
            @QueryParam("userId") long userId,
            @HeaderParam("X-Remote-User") String username);


    Response createCollabRoomWithPermissions(
            int incidentId,
            int orgId,
            int workspaceId,
            CollabRoom collabroom);

    CollabRoom createUnsecureCollabRoom(
            int incidentId,
            CollabRoom collabroom)
            throws DataAccessException, DuplicateCollabRoomException, Exception;
}