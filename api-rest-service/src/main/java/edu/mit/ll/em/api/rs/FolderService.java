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

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import edu.mit.ll.nics.common.entity.datalayer.Folder;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;

import java.util.List;

@Path("/folder/{workspaceId}")
public interface FolderService {
    @GET
    @Path(value = "/name/{folderName}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getFolderData(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("folderName") String folderName);

    @GET
    @Path(value = "/incident/archived/{orgId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getArchivedFolderData(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("orgId") int orgId);

    @GET
    @Path(value = "/incident/{folderId}/{orgId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIncidentFolderData(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("folderId") String folderId,
            @PathParam("orgId") int orgId);

    @GET
    @Path(value = "/id/{folderId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getChildFolders(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("folderId") String folderId);

    @POST
    @Path(value = "/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    public Response postFolder(
            @PathParam("workspaceId") int workspaceId,
            Folder folder);

    @DELETE
    @Path("/document/incident/{incidentId}/folder/{folderId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteIncidentDocument(
            @PathParam("folderId") String folderId,
            @PathParam("incidentId") String incidentId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @DELETE
    @Path("/document/org/{orgId}/folder/{folderId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteOrgDocument(
            @PathParam("folderId") String folderId,
            @PathParam("orgId") String orgId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @DELETE
    @Path("/document/collabroom/{collabroomId}/folder/{folderId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteCollabroomDocument(
            @PathParam("folderId") String folderId,
            @PathParam("collabroomId") String collabroomId,
            @HeaderParam("X-Remote-User") String requestingUser);

    @POST
    @Path(value = "/document/usersession/{usersessionId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Response postDocumentFolder(
            @PathParam("workspaceId") int workspaceId,
            @QueryParam("orgId") int orgId,
            @QueryParam("incidentId") int incidentId,
            @QueryParam("collabroomId") int collabroomId,
            @PathParam("usersessionId") int usersessionId,
            @Multipart("displayName") String displayName,
            @Multipart("description") String description,
            MultipartBody body,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Path(value = "/document/folder/{folderId}/usersession/{usersessionId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateDocumentFolder(
            @PathParam("folderId") String folderId,
            @QueryParam("orgId") int orgId,
            @QueryParam("incidentId") int incidentId,
            @QueryParam("collabroomId") int collabroomId,
            @PathParam("usersessionId") int usersessionId,
            @Multipart("displayName") String displayName,
            @Multipart("description") String description,
            MultipartBody body,
            @HeaderParam("X-Remote-User") String username);

    @GET
    @Path(value = "/document")
    @Produces(MediaType.APPLICATION_JSON)
    Response getDocumentFolderData(
            @PathParam("workspaceId") int workspaceId,
            @QueryParam("orgId") int orgId,
            @QueryParam("incidentId") int incidentId,
            @QueryParam("collabroomId") int collabroomId);

    @POST
    @Path(value = "/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateFolder(
            @PathParam("workspaceId") int workspaceId,
            Folder folder);

    @DELETE
    @Path(value = "/{folderId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteFolder(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("folderId") String folderId);

    @POST
    @Path(value = "/move/{parentFolderId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response moveFolder(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("parentFolderId") String parentFolderId,
            @QueryParam("folderId") String folderId,
            @QueryParam("datalayerfolderId") Integer datalayerfolderId,
            @QueryParam("index") int index);
}

