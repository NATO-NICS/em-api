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

import edu.mit.ll.nics.common.entity.Alert;
import edu.mit.ll.nics.common.entity.AlertUser;

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
 * Alert Service Interface.
 */
@Path("/alert")
public interface AlertService {

    /**
     * Responsible for persisting a UserAlert.
     *
     * @param alert    the alert to persist
     * @param username the username of the user sending the request
     * @return a Response object with an HTTP status indicating success or failure
     */
    @POST
    @Path("/user")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postUserAlert(
            AlertUser alert,
            @HeaderParam("X-Remote-User") String username);

    /**
     * Responsible for persisting an Alert.
     *
     * @param alert    the alert to persist
     * @param username the username of the user posting the alert
     * @return a Response object with a status of OK if successful, and PRECONDITION_FAILED otherwise
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response postAlert(
            Alert alert,
            @HeaderParam("X-Remote-User") String username);

    /**
     * Retrieves alerts associated with the specified Incident and User IDs.
     *
     * @param incidentId the ID of the incident this alert is associated with
     * @param userId     the ID of the user associated with the alert
     * @param username   the username of the user making this request
     * @return Response object
     */
    @GET
    @Path("/{incidentId}/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getAlerts(
            @PathParam("incidentId") int incidentId,
            @PathParam("userId") int userId,
            @HeaderParam("X-Remote-User") String username);

    /**
     * Deletes the alert with the specified ID.
     *
     * @param datasourceId the ID to delete
     * @param username     the username of the user making the delete request
     * @return a Response object with appropriate Status set
     */
    @DELETE
    @Path("/{datasourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteAlert(
            @PathParam("datasourceId") int datasourceId,
            @HeaderParam("X-Remote-User") String username);

}