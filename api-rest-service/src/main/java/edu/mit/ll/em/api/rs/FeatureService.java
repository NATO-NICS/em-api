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
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;

import edu.mit.ll.nics.common.entity.FeatureComment;


@Path("/features")
public interface FeatureService {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/update/collabroom/{collabRoomId}")
    public Response updateFeature(
            @PathParam("collabRoomId") int collabRoomId,
            @DefaultValue("3857") @QueryParam("geoType") int geoType,
            String feature,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/collabroom/{collabRoomId}")
    Response postCollabRoomFeature(
            @PathParam("collabRoomId") int collabRoomId,
            @DefaultValue("3857") @QueryParam("geoType") int geoType,
            String feature,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/remove/collabroom/{featureId}")
    Response deleteCollabRoomFeature(
            @QueryParam("collabRoomId") int collabRoomId,
            @PathParam("featureId") long featureId,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/update/user")
    Response updateUserFeature(
            String feature);


    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/user/{userId}")
    Response postUserFeature(
            @PathParam("userId") long userId,
            String feature,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/remove/user/{featureId}")
    Response deleteUserFeature(
            @PathParam("featureId") long featureId);


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/collabroom/{collabRoomId}")
    Response getCollabroomFeatures(
            @PathParam("collabRoomId") int collabRoomId,
            @QueryParam("userId") long userId,
            @QueryParam("") QueryConstraintParms optionalParams,
            @DefaultValue("3857") @QueryParam("geoType") int geoType,
            @HeaderParam("X-Remote-User") String username);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/user/{userId}")
    Response getUserFeatures(
            @PathParam("userId") int userId,
            @HeaderParam("X-Remote-User") String username);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/comment/{featureId}")
    Response getFeatureComments(
            @PathParam("featureId") long featureId,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/comment")
    Response postFeatureComment(
            FeatureComment comment,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/comment/update")
    Response updateFeatureComment(
            FeatureComment comment,
            @HeaderParam("X-Remote-User") String username);

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/comment/{featureId}/{featureCommentId}")
    Response deleteFeatureComment(
            @PathParam("featureId") long featureId,
            @PathParam("featureCommentId") long featureCommentId,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/{featureId}/document")
    public Response postFeatureDocument(
            @PathParam("featureId") long featureId,
            @QueryParam("usersessionId") int usersessionId,
            @QueryParam("username") String username,
            List<Attachment> attachments,
            @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/{featureId}/document")
    public Response postFeatureDocument(
            @PathParam("featureId") long featureId,
            @QueryParam("documentId") String documentId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/{featureId}/document/{documentId}")
    Response deleteFeatureDocument(
            @PathParam("featureId") long featureId,
            @PathParam("documentId") long documentId,
            @HeaderParam("X-Remote-User") String username);


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/user/{userId}/share")
    Response shareWorkspace(
            @PathParam("userId") int userId,
            @QueryParam("collabRoomId") int collabRoomId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/user/{userId}/unshare")
    Response unshareWorkspace(
            @PathParam("userId") int userId,
            @QueryParam("collabRoomId") int collabRoomId,
            @HeaderParam("X-Remote-User") String requestingUser);


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path(value = "/user/{userId}/copy")
    Response copyWorkspace(
            @PathParam("userId") int userId,
            @QueryParam("collabRoomId") int collabRoomId,
            @HeaderParam("X-Remote-User") String requestingUser);


}

