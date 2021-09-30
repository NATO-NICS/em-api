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
package edu.mit.ll.em.api.rs.impl;

import edu.mit.ll.em.api.rs.AnnouncementService;
import edu.mit.ll.em.api.rs.LogServiceResponse;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.Log;
import edu.mit.ll.nics.nicsdao.impl.LogDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Service implementation for posting, getting, and deleting announcements.
 *
 * @AUTHOR st23429
 */
public class AnnouncementServiceImpl implements AnnouncementService {

    private static String POST_ERROR_MESSAGE = "An error occurred while attempting to post a new announcement.";
    private static String GET_ERROR_MESSAGE = "An error occurred while attempting to retrieve announcements.";
    private static String DELETE_ERROR_MESSAGE = "An error occurred while attempting to delete an announcement.";

    /**
     * Logger instance.
     */
    private static final Logger LOG = LoggerFactory.getLogger(AnnouncementServiceImpl.class);

    /**
     * UserOrgDAO instance.
     */
    private static final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();

    /**
     * LogDAO instance.
     */
    private static final LogDAOImpl logDao = new LogDAOImpl();


    /**
     * {@inheritDoc}
     * <p/>
     *
     * @param workspaceId the workspace ID the announcement belongs to
     * @param log         the Log entity containing the message text of the announcement
     * @param username    the username of the user posting the announcement
     * @return a Response with a Status of BAD_REQUEST if the requesting username does not have an Admin or Super role,
     * a Status of OK if the Announcement was successfully persisted, or a Status of INTERNAL_SERVER_ERROR if there was
     * an error persisting the Announcement.
     */
    @Override
    public Response postAnnouncement(int workspaceId, Log log, String username) {
        AnnouncementServiceImpl.LOG.debug("User {} posting announcement to workspaceId {}:\n{}",
                username, workspaceId, log.getMessage());

        if(userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID)
                || userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID)) {

            if(logDao.postLog(workspaceId, log)) {
                return Response.status(Status.OK).entity(Status.OK.getReasonPhrase()).build();
            } else {
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(POST_ERROR_MESSAGE).build();
            }
        }

        return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
    }

    /**
     * {@inheritDoc}
     * <p/>
     *
     * @param workspaceId the workspace ID to retrieve announcements from
     * @param username    the username of the user making the request
     * @return a Response object containing a Status, and set of Log results if any were found, or a Status of
     * INTERNAL_SERVER_ERROR if there was a problem querying Announcements
     */
    @Override
    public Response getAnnouncements(int workspaceId, String username) {
        LOG.debug("getAnnouncements requested by user {}", username);

        LogServiceResponse logResponse = new LogServiceResponse();
        logResponse.setResults(logDao.getLogs(workspaceId, SADisplayConstants.ANNOUNCEMENTS_LOG_TYPE));
        if(logResponse.getResults() == null) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(GET_ERROR_MESSAGE).build();
        }
        return Response.status(Status.OK).entity(logResponse).build();
    }

    /**
     * {@inheritDoc}
     *
     * @param workspaceId the workspace ID the announcement to delete is in
     * @param logId       the ID of the Log containing the announcement to delete
     * @param username    the username of the user making the delete request
     * @return Response with Status BAD_REQUEST if user doesn't have ADMIN or SUPER role, OK if the delete was
     * successful, or INTERNAL_SERVER_ERROR if there was a problem deleting the Announcement
     */
    @Override
    public Response deleteAnnouncement(int workspaceId, int logId, String username) {
        LOG.debug("User {} requesting deletion of announcement with logId {} in workspace {}",
                username, logId, workspaceId);

        if(userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID)
                || userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID)) {
            if(logDao.deleteLog(logId)) {
                return Response.status(Status.OK).entity(Status.OK.getReasonPhrase()).build();
            } else {
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(DELETE_ERROR_MESSAGE).build();
            }
        }

        return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
    }
}