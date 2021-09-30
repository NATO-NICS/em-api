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

import edu.mit.ll.nics.common.entity.MediaStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/ws/{workspaceId}/mediastreams")
public interface MediaStreamService {

    /**
     * Retrieves all streams for given workspace
     *
     * @param workspaceId the id of the workspace to retrieve the streams from
     * @param username    Identity of calling user
     * @return Response containing list of streams if found, or error response if there was a problem
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response readMediaStreams(@PathParam("workspaceId") int workspaceId,
                              @DefaultValue("") @QueryParam("title") String title,
                              @DefaultValue("") @QueryParam("streamurl") String url,
                              @HeaderParam("X-Remote-User") String username);

    /**
     * Finds matching streams
     *
     * @param workspaceId the ID of the workspace to retrieve the streams from
     * @param name the name of the stream to find
     * @param url the url of the stream to find
     * @param username Identity of calling user
     *
     * @return Response containing list of streams if found, or error response if there was a problem
     *
     @GET
     @Produces(MediaType.APPLICATION_JSON) Response findMediaStreams(@PathParam("workspaceId") int workspaceId,
     @QueryParam("name") String name, @QueryParam("url") String url,
     @HeaderParam("X-Remote-User") String username);*/

    /**
     * Creates a stream entry
     *
     * @param workspaceId the ID of the workspace the stream belongs to
     * @param stream      the stream entity
     * @param username    Identity of calling user
     * @return a Response containing the stream entity that was inserted if successful, an error response otherwise
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createMediaStream(@PathParam("workspaceId") int workspaceId, MediaStream stream,
                               @HeaderParam("X-Remote-User") String username);

    /**
     * Update the passed in MediaStream
     *
     * @param workspaceId the ID of the workspace the stream belongs to
     * @param streamId    the ID of the stream to update
     * @param stream      the modified stream entity
     * @param username    Identity of calling user
     * @return a Response containing the updated stream entity if successful, an error response otherwise
     */
    @PUT
    @Path("/{streamId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateMediaStream(@PathParam("workspaceId") int workspaceId,
                               @PathParam("streamId") long streamId, MediaStream stream,
                               @HeaderParam("X-Remote-User") String username);

    /**
     * Delete the stream specified by the stream id
     *
     * @param workspaceId the ID of the workspace the stream belongs to
     * @param streamId    the ID of the stream to delete
     * @param username    Identity of calling user
     * @return
     */
    @DELETE
    @Path("/{streamId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteMediaStream(@PathParam("workspaceId") int workspaceId,
                               @PathParam("streamId") long streamId,
                               @HeaderParam("X-Remote-User") String username);
}
