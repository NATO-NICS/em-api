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

import edu.mit.ll.nics.common.entity.MobileDeviceTrack;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Interface for the MobileDeviceTrackService.
 */
@Path("/mdtracks")
public interface MobileDeviceTrackService {

    /**
     * Endpoint for inserting/updating a {@link MobileDeviceTrack}.
     *
     * @param mobileDeviceTrack the track to persist
     * @param workspaceId       the ID of the workspace this track is to be associated with
     * @param username          the authenticated username
     * @return a Response specifying success or failure
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{workspaceId}")
    Response postMDTrack(MobileDeviceTrack mobileDeviceTrack,
                         @PathParam("workspaceId") Integer workspaceId,
                         @HeaderParam("X-Remote-User") String username);

    /**
     * Deletes the specified user's current track entry. Does not affect any track history.
     *
     * @param userId   the username of the user who's track is to be deleted
     * @param username the username the call is authenticated as
     * @return a Response specifying success or failure of the deletion. 404 if a track for that user was not found, 200
     * if successful, 401 if authenticated username and specified username do not match, and an error status if there
     * was a problem deleting the track
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{workspaceId}/user/{userId}/device/{deviceId}")
    Response deleteMDTrack(@PathParam("userId") String userId,
                           @PathParam("deviceId") String deviceId,
                           @PathParam("workspaceId") Integer workspaceId,
                           @HeaderParam("X-Remote-User") String username);
}

