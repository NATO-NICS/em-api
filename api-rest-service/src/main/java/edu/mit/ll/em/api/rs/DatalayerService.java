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

import java.util.ArrayList;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import edu.mit.ll.nics.common.entity.CollabroomDatalayer;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;

import edu.mit.ll.nics.common.entity.datalayer.Datalayer;
import edu.mit.ll.nics.common.entity.datalayer.Datasource;

@Path("/datalayer/{workspaceId}")
public interface DatalayerService {

    @GET
    @Path("/{folderId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getDatalayers(@PathParam("folderId") String folderId);

    @GET
    @Path("/collabroom/{collabRoomId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getCollabRoomDatalayers(@PathParam("collabRoomId") int collabRoomId,
                                     @QueryParam("enablemobile") String enablemobile);

    @GET
    @Path("/tracking")
    @Produces(MediaType.APPLICATION_JSON)
    Response getTrackingLayers(@PathParam("workspaceId") int workspaceId);

    @GET
    @Path("/token/{datasourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getToken(@PathParam("datasourceId") String datasourceId);

    @GET
    @Path("/token")
    @Produces(MediaType.APPLICATION_JSON)
    Response getToken(
            @QueryParam("internalurl") String internalUrl,
            @QueryParam("username") String username,
            @QueryParam("password") String password);

    @POST
    @Path("/sources/{dataSourceId}/layer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postDataLayer(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("dataSourceId") String dataSourceId,
            Datalayer datalayer,
            @QueryParam("folderId") String folderId);

    @POST
    @Path("/sources/{dataSourceId}/tracking/layer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postTrackingLayer(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("dataSourceId") String dataSourceId,
            Datalayer datalayer);

    @DELETE
    @Path("/sources/{dataSourceId}/layer")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteDataLayer(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("dataSourceId") String dataSourceId);

    @POST
    @Path("/sources/layer/update")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateDataLayer(
            @PathParam("workspaceId") int workspaceId,
            Datalayer datalayer);

    @POST
    @Path("/sources/{dataSourceId}/document/{userOrgId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)

    public Response postDataLayerDocument(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("dataSourceId") String dataSourceId,
            @PathParam("userOrgId") int userOrgId,
            @Multipart(value = "refreshrate", required = false) int refreshRate,
            MultipartBody body,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Path("/icon")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postTrackingIcon(MultipartBody body, @HeaderParam("X-Remote-User") String username);

    @POST
    @Path("/sources/{dataSourceId}/georss/{userOrgId}/{usersessionId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postGeorssDataLayerDocument(
            @PathParam("workspaceId") int workspaceId,
            @PathParam("dataSourceId") String dataSourceId,
            @PathParam("userOrgId") int userOrgId,
            @PathParam("usersessionId") int usersessionId,
            @FormParam("refreshrate") int refreshRate,
            @FormParam("feedUrl") String feedUrl,
            @FormParam("displayname") String displayName,
            @HeaderParam("X-Remote-User") String username,
            @FormParam("folderid") String folderId);

    @POST
    @Path("/shapefile")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Response postShapeDataLayer(
            @PathParam("workspaceId") int workspaceId,
            @Multipart("displayName") String displayName,
            MultipartBody body,
            @HeaderParam("X-Remote-User") String username,
            @Multipart("folderid") String folderId);

    @POST
    @Path("/geotiff")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Response postGeotiffDataLayer(
            @PathParam("workspaceId") int workspaceId,
            @Multipart("displayName") String displayName,
            @Multipart("filename") String fileName,
            @Multipart("tifFile") Attachment uploadedFile,
            @QueryParam("isMosaic") boolean isMosaic,
            MultipartBody body,
            @HeaderParam("X-Remote-User") String username,
            @Multipart("folderid") String folderId);

    @POST
    @Path("/georss")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postGeorssDataLayer(
            @PathParam("workspaceId") int workspaceId,
            @Multipart("displayName") String displayName,
            @Multipart("url") String feedUrl,
            MultipartBody body,
            @HeaderParam("X-Remote-User") String username,
            @QueryParam("folderId") String folderId);


    @POST
    @Path("/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Response postImageDataLayer(
            @PathParam("workspaceId") int workspaceId,
            @Multipart("id") String id,
            MultipartBody body,
            @HeaderParam("X-Remote-User") String username);

    @POST
    @Path("/image/finish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response finishImageLayer(
            @QueryParam("cancel") boolean cancel,
            @PathParam("workspaceId") int workspaceId,
            @QueryParam("id") String id,
            @QueryParam("title") String title,
            @QueryParam("usersessionid") int usersessionId,
            @QueryParam("folderId") String folderId);


    @GET
    @Path("/sources/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getDatasources(@PathParam("type") String type);

    @POST
    @Path("/sources/{type}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postDatasource(
            @PathParam("type") String type,
            Datasource dataSource);

    @POST
    @Path("/collabroom/{collabroomId}/{datalayerId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response addCollabroomDatalayer(
            @PathParam("collabroomId") int collabroomId,
            @PathParam("datalayerId") String datalayerId);

    @POST
    @Path("/collabroom/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateCollabroomDatalayer(
            CollabroomDatalayer collabroomDatalayer);

    @POST
    @Path("/collabroom")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response deleteCollabroomDataLayer(ArrayList<CollabroomDatalayer> collabRoomDataLayerId);

    @DELETE
    @Path("/datasource/{dataSourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteDatasource(
            @PathParam("dataSourceId") String dataSourceId,
            @HeaderParam("X-Remote-User") String username);
}

