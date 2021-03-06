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

import edu.mit.ll.nics.common.entity.Log;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


/**
 * Announcement Service Interface.
 */
@Path("/announcement/{workspaceId}")
public interface AnnouncementService {

    /**
     * Persists Announcements.
     *
     * @param workspaceId the workspace ID the announcement belongs to
     * @param log         the Log entity containing the message text of the announcement
     * @param username    the username of the user posting the announcement
     * @return a Response with a Status indicating success or failure
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postAnnouncement(
            @PathParam("workspaceId") int workspaceId,
            Log log,
            @HeaderParam("X-Remote-User") String username);

    /**
     * Retrieves Announcements.
     *
     * @param workspaceId the workspace ID to retrieve announcements from
     * @param username    the username of the user making the request
     * @return a Response with a Status indicating success or failure
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getAnnouncements(
            @PathParam("workspaceId") int workspaceId,
            @HeaderParam("X-Remote-User") String username);

    /**
     * Deletes Announcements.
     *
     * @param workspaceId the workspace ID the announcement to delete is in
     * @param logId       the ID of the Log containing the announcement to delete
     * @param username    the username of the user making the delete request
     * @return a Response with a Status indicating sucess or failure
     */
    @DELETE
    @Path("/{logId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteAnnouncement(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("logId") int logId,
            @HeaderParam("X-Remote-User") String username);

}