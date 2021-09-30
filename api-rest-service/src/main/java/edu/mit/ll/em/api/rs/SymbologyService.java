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

import edu.mit.ll.nics.common.entity.Symbology;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;


/**
 * Symbology Service Interface for managing user-created Symbology packs.
 */
@Api("Symbology")
@Path("/symbology")
public interface SymbologyService {

    /** The identity header key */
    String IDENTITY_HEADER = "X-Remote-User";

    // Endpoint Parameter Constants
    /** Parameter for the owner field: {@value} */
    String PARAM_OWNER = "owner";

    /** Parameter for the Symbology identifier: {@value} */
    String PARAM_SYMBOLOGYID = "symbologyId";

    /** Paramter for the Org identifier: {@value} */
    String PARAM_ORGID = "orgId";

    /**
     * Responsible for returning all Symbologies.
     *
     * @param requestingUser the username of the user making the request
     * @param owner Optional query parameter. Filters results on owner field
     *
     * @return a Response containing the appropriate success or failure
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved Symbology, if any exist"),
            @ApiResponse(code = 500, message = "Error retrieving Symbology")
    })
    Response getSymbology(@ApiParam(name= PARAM_OWNER, value="Filter by owner")
                                @QueryParam(PARAM_OWNER) String owner,
                            @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Responsible for retrieving the specified Symbology entity.
     *
     * @param symbologyId identifier of the Symbology to fetch
     * @param requestingUser username of the user making the request
     *
     * @return {@link SymbologyServiceResponse} containing the Symbology if found, or appropriate error otherwise
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved Symbology"),
            @ApiResponse(code = 404, message = "Symbology with specified ID not found"),
            @ApiResponse(code = 500, message = "Error retrieving Symbology")
    })
    @Path("/{symbologyId}")
    Response getSymbologyById(@PathParam(PARAM_SYMBOLOGYID) int symbologyId,
                              @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Responsible for retrieving Symbology associated with the specified Org identifier.
     *
     * @param orgId identifier for the Organzation to retrieve Symbologies for
     * @param requestingUser username of the user making the request
     *
     * @return {@link SymbologyServiceResponse} containing Symbologies if found, appropriate error status otherwise
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved Symbology, if any exist"),
            @ApiResponse(code = 500, message = "Error retrieving Symbology")
    })
    @Path("/org/{orgId}")
    Response getSymbologyByOrgId(@PathParam(PARAM_ORGID) int orgId,
                                 @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Creates a symbology entity, along with accepting a zip file containing the icons to
     * unpack to the symbology location specified by the em-api property
     * <i>em.api.service.symbology.path</i>
     * </p>
     * A directory will be created using the name of the file, minus the extension, and the
     * contents will be extracted to this directory if it doesn't already exist. If the directory
     * already exists, an error response will be returned stating as much.
     *
     * @param name a short name for the Symbology, which may be used in the UI to organize the icons
     * @param description a short description for the symbology
     * @param owner the username of the POC for this symbology set (must be an existing NICS user)
     * @param attachment the multipart body containing the zip file attachment
     * @param requestingUser the username of the user making the request
     *
     * @return Response containing the newly created Symbology if successful, or appropriate error response otherwise
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Successfully created Symbology"),
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 403, message = "Not authorized to create Symbology"),
            @ApiResponse(code = 500, message = "Error creating Symbology")
    })
    // TODO: this implicitparam is for the benefit of having Swagger UI able to upload a zip file for testing, but
    //  something with how it's formed from Swagger doesn't work, need to investigate.
    @ApiImplicitParams(
            @ApiImplicitParam(value = "Symbology zip archive", required = true, dataType = "file",
                    name = "file", paramType = "formData"))
    @Path("/")
    Response createSymbology(@Multipart(value="name")
                             @ApiParam(name="name", type="string", value="The name of the symbology",
                                     required = true) String name,
                             @Multipart(value="description")
                             @ApiParam(name="description", type="string", value="Short description of the symbology",
                                     required = true) String description,
                             @Multipart(value="owner")
                             @ApiParam(name="owner", type="string",
                                     value="The username/email of the symbology owner or POC", required = true)
                                     String owner,
                             @Multipart("file") @ApiParam(hidden = true) Attachment attachment,
                             @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Responsible for updating an existing Symbology entity. Only updates name, description, owner, and listing
     * fields. Does not accept an attachment.
     *
     * @param symbologyId the id of the existing Symbology entity to update
     * @param symbology the updated Symbology to persist
     * @param requestingUser the username of the user making the request
     *
     * @return Response with the updated Symbology entity if successful, appropriate error response otherwise
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully updated Symbology"),
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 404, message = "Symbology with that ID not found"),
            @ApiResponse(code = 500, message = "Error updating Symbology")
    })
    @Path("/{symbologyId}")
    Response updateSymbologyNoZip(@PathParam(PARAM_SYMBOLOGYID) int symbologyId,
                                  Symbology symbology,
                                  @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Updates a symbology, along with accepting a zip file containing the icons to
     * unpack to the symbology location specified by the em-api property
     * <i>em.api.service.symbology.path</i>
     * </p>
     * The contents will be extracted to the pre-existing directory. Files will be overwritten, but
     * nothing will be removed.
     *
     * @param name a short name for the Symbology, which may be used in the UI to organize the icons
     * @param description a short description for the symbology
     * @param owner the username of the owner/POC for this symbology set (must be an existing NICS user)
     * @param attachment the multipart body containing the zip file attachment
     * @param requestingUser the username of the user making the request
     *
     * @return Response containing the newly updated Symbology if successful, or an appropriate error
     *         response otherwise
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully updated Symbology"),
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 403, message = "Not authorized to update Symbology"),
            @ApiResponse(code = 404, message = "Symbology not found"),
            @ApiResponse(code = 500, message = "Error updating Symbology")
    })
    // TODO: WILL be fixed in refactor2021 with OpenAPI upgrade
    //  this implicitparam is for the benefit of having Swagger UI able to upload a zip file for testing, but
    //  something with how it's formed from Swagger doesn't work, need to investigate.
    @ApiImplicitParams(
            @ApiImplicitParam(value = "Symbology zip archive", required = true, dataType = "file",
                    name = "file", paramType = "formData"))
    @Path("/{symbologyId}")
    Response updateSymbology(@PathParam(PARAM_SYMBOLOGYID)
                             @ApiParam(name= PARAM_SYMBOLOGYID, type="int", value="The ID of the Symbology to update",
                                     required=true) int symbologyId,
                                  @Multipart(value="name")
                             @ApiParam(name="name", type="string", value="The name of the symbology",
                                     required = true) String name,
                                  @Multipart(value="description")
                             @ApiParam(name="description", type="string", value="Short description of the symbology",
                                     required = true) String description,
                                  @Multipart(value="owner")
                             @ApiParam(name="owner", type="string",
                                     value="The username/email of the symbology owner or POC", required = true)
                                     String owner,
                                  @Multipart("file") @ApiParam(hidden = true) Attachment attachment,
                                  @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Responsible for deleting a Symbology, but should retain icons on server
     * for historical display. Restricted to role 'super'.
     *
     * @param symbologyId the id of the Symbology to delete
     * @param requestingUser the username of the user making the request
     *
     * @return Response with success or failure
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully deleted Symbology"),
            @ApiResponse(code = 403, message = "Not authorized to delete Symbology"),
            @ApiResponse(code = 404, message = "Symbology with that ID not found"),
            @ApiResponse(code = 500, message = "Error deleting Symbology")
    })
    @Path("/{symbologyId}")
    Response deleteSymbology(@PathParam(PARAM_SYMBOLOGYID) int symbologyId,
                             @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Responsible for creating an Org->Symbology mapping.
     *
     * @param orgId ID of the Organization to map
     * @param symbologyId ID of the Symbology to map
     * @param requestingUser the username of the user making the request
     *
     * @return Response with appropriate success or failure status
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Successfully created Org Symbology mapping"),
            @ApiResponse(code = 400, message = "Invalid request"),
            @ApiResponse(code = 403, message = "Not authorized to create Org Symbology mapping"),
            @ApiResponse(code = 404, message = "Symbology or Organization not found"),
            @ApiResponse(code = 500, message = "Error creating Org Symbology mapping")
    })
    @Path("/{symbologyId}/org/{orgId}")
    Response createOrgSymbology(@PathParam("orgId") int orgId,
                                @PathParam(PARAM_SYMBOLOGYID) int symbologyId,
                                @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Get all Org Symbology mappings.
     *
     * @param requestingUser the username of the user making the request
     *
     * @return SymbologyServiceResponse with the OrgSymbologies set if any found, appropriate
     * error responses otherwise
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved Org Symbology Mappings, if any exist"),
            @ApiResponse(code = 500, message = "Error retrieving Org Symbology Mappings")
    })
    @Path("/org")
    Response getOrgSymbologyMappings(@HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Responsible for deleting an Org->Symbology mapping
     *
     * @param orgId the ID of the Organization to unmap
     * @param symbologyId the ID of the Symbology to unmap
     * @param requestingUser the username of the user making the request
     *
     * @return Response with appropriate success or failure status
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully deleted Org Symbology"),
            @ApiResponse(code = 403, message = "Not authorized to delete Org Symbology"),
            @ApiResponse(code = 404, message = "Symbology with that ID not found"),
            @ApiResponse(code = 500, message = "Error deleting Org Symbology")
    })
    @Path("/{symbologyId}/org/{orgId}")
    Response deleteOrgSymbology(@PathParam("orgId") int orgId,
                                @PathParam(PARAM_SYMBOLOGYID) int symbologyId,
                                @HeaderParam(IDENTITY_HEADER) String requestingUser);

}