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

import edu.mit.ll.em.api.dataaccess.EntityCacheMgr;
import edu.mit.ll.em.api.dataaccess.ICSDatastoreException;
import edu.mit.ll.em.api.rs.MediaStreamResponse;
import edu.mit.ll.em.api.rs.MediaStreamService;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.MediaStream;
import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.nicsdao.impl.MediaStreamDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of the MediaStreamService interface. Implements CRUD and querying operations on streams.
 */
public class MediaStreamServiceImpl implements MediaStreamService {
    /**
     * Failure to retrieve message
     */
    private static final String FAILED_READ = "Failed to read media streams.";

    /**
     * Failure to insert message
     */
    private static final String FAILED_CREATE = "Failed to create new media stream.";

    /**
     * Failure to update message
     */
    private static final String FAILED_UPDATE = "Failed to update media stream.";

    /**
     * Failure to delete message
     */
    private static final String FAILED_DELETE = "Failed to delete media stream.";

    /**
     * OK response string
     */
    private static final String OK = "OK";

    private static final String SUCCESS = "Success";

    private static final String FAILURE = "Failure: ";


    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(MediaStreamServiceImpl.class);

    /**
     * MediaStream DAO
     */
    private static MediaStreamDAOImpl msDao = new MediaStreamDAOImpl();

    /**
     * User DAO
     */
    private static UserServiceImpl userDao = new UserServiceImpl();

    /**
     * Org DAO
     */
    private static OrgDAOImpl orgDao = new OrgDAOImpl();


    @Override
    public Response readMediaStreams(int workspaceId, String title, String url, String username) {
        MediaStreamResponse streamResponse = new MediaStreamResponse();
        Response.Status status = Response.Status.BAD_REQUEST;

        LOG.info("workspaceid, title, url, username: {}, {}, {}, {}", workspaceId, title, url, username);

        // TODO: Need to ensure user exists, and is enabled in the workspace, later we'll
        //  also be checking permissions
        if(userNotAuthorized(workspaceId, username)) {
            status = Response.Status.UNAUTHORIZED;
            streamResponse.setMessage("Unauthorized");
            return Response.ok(streamResponse).status(status).build();
        }

        if(workspaceId > 0) {
            try {
                LOG.debug("Getting streams for workspace {}", workspaceId);

                if(title.isEmpty() && url.isEmpty()) {
                    streamResponse.setStreams(msDao.readMediaStreamsByWorkspaceId(workspaceId));
                } else {
                    streamResponse.setStreams(msDao.findMediaStreamsByWorkspaceId(workspaceId, title, url));
                }

                status = Response.Status.OK;
                streamResponse.setMessage(OK);
            } catch(Exception e) {
                LOG.error("Error retrieving streams for workspaceId {}", workspaceId, e);
                status = Response.Status.INTERNAL_SERVER_ERROR;
                streamResponse.setMessage(FAILED_READ);
            }
        } else {
            streamResponse.setMessage("Invalid workspaceId");
            LOG.debug("Invalid workspaceId specified: {}", workspaceId);
        }

        return Response.ok(streamResponse).status(status).build();
    }


    public Response findMediaStreams(int workspaceId, String name, String url, String username) {
        MediaStreamResponse streamResponse = new MediaStreamResponse();
        Response.Status status = Response.Status.BAD_REQUEST;

        // TODO: Need to ensure user exists, and is enabled in the workspace, later we'll
        //  also be checking permissions
        if(userNotAuthorized(workspaceId, username)) {
            status = Response.Status.UNAUTHORIZED;
            streamResponse.setMessage("Unauthorized");
            return Response.ok(streamResponse).status(status).build();
        }

        if(workspaceId > 0) {

            if(name == null && url == null) {
                status = Response.Status.BAD_REQUEST;
                streamResponse.setMessage("Empty search parameters");
                return Response.ok(streamResponse).status(status).build();
            }

            try {
                LOG.debug("Finding streams for workspace {} with query params: name: {}, and url: {}",
                        workspaceId, name, url);
                streamResponse.setStreams(msDao.findMediaStreamsByWorkspaceId(workspaceId, name, url));
                status = Response.Status.OK;
                streamResponse.setMessage(OK);
            } catch(Exception e) {
                LOG.error("Error retrieving streams for workspaceId {}", workspaceId, e);
                status = Response.Status.INTERNAL_SERVER_ERROR;
                streamResponse.setMessage(FAILED_READ);
            }
        } else {
            streamResponse.setMessage("Invalid workspaceId");
            LOG.debug("Invalid workspaceId specified: {}", workspaceId);
        }

        return Response.ok(streamResponse).status(status).build();
    }

    @Override
    public Response createMediaStream(int workspaceId, MediaStream stream, String username) {
        MediaStreamResponse res = new MediaStreamResponse();
        Response.Status status = Response.Status.BAD_REQUEST;

        // TODO: Need to ensure user exists, and is enabled in the workspace, later we'll
        //  also be checking permissions
        if(userNotAuthorized(workspaceId, username)) {
            status = Response.Status.UNAUTHORIZED;
            res.setMessage("Unauthorized");
            return Response.ok(res).status(status).build();
        }

        if(workspaceId > 0 && stream != null) {
            try {
                LOG.info("Creating new media stream");
                res.setStreams(Arrays.asList(msDao.createMediaStream(
                        stream, workspaceId)));
                res.setMessage(SUCCESS);
                status = Response.Status.OK;
            } catch(Exception e) {
                LOG.error("Exception adding stream: {}", e.getMessage(), e);
                if(e.getMessage().contains(SADisplayConstants.MEDIASTREAM_NOT_FOUND)) {
                    res.setMessage(e.getMessage());
                    status = Response.Status.NOT_FOUND;
                } else {
                    res.setMessage(String.format("%s %s", FAILED_CREATE, e.getMessage()));
                    status = Response.Status.INTERNAL_SERVER_ERROR;
                }
            }
        } else {
            LOG.debug("Invalid workspaceId ({}) or null stream entity", workspaceId);
            res.setMessage(String.format("%s Invalid workspaceId or null stream entity",
                    FAILED_CREATE));
        }

        return Response.ok(res).status(status).build();
    }

    @Override
    public Response updateMediaStream(int workspaceId, long streamId, MediaStream stream, String username) {
        MediaStreamResponse res = new MediaStreamResponse();
        Response.Status status = Response.Status.BAD_REQUEST;

        // TODO: Need to ensure user exists, and is enabled in the workspace, later we'll
        //  also be checking permissions
        if(userNotAuthorized(workspaceId, username)) {
            status = Response.Status.UNAUTHORIZED;
            res.setMessage("Unauthorized");
            return Response.ok(res).status(status).build();
        }

        if(stream != null && stream.getMsid() > 0 && streamId == stream.getMsid()) {
            try {
                final MediaStream updatedStream = msDao.updateMediaStream(stream);
                if(updatedStream != null) {
                    res.setStreams(Arrays.asList(updatedStream));
                    res.setMessage(OK);
                    status = Response.Status.OK;
                } else {
                    LOG.debug("No MediaStream affected by update query on stream: {}",
                            stream.getTitle());
                    res.setMessage("Stream not found");
                    status = Response.Status.NOT_FOUND;
                }
            } catch(Exception e) {
                LOG.error("Exception updating stream: {}", e.getMessage(), e);
                if(e.getMessage().contains(SADisplayConstants.MEDIASTREAM_NOT_FOUND)) {
                    res.setMessage(SADisplayConstants.MEDIASTREAM_NOT_FOUND);
                    status = Response.Status.NOT_FOUND;
                } else {
                    res.setMessage(FAILED_UPDATE);
                }
            }
        } else {
            LOG.debug("Invalid workspaceId, streamId, or given streamId does not match id in entity");
            res.setMessage("Invalid workspaceId or streamId");
        }

        return Response.ok(res).status(status).build();
    }

    @Override
    public Response deleteMediaStream(int workspaceId, long streamId, String username) {
        MediaStreamResponse res = new MediaStreamResponse();
        Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;

        // TODO: Need to ensure user exists, and is enabled in the workspace, later we'll
        //  also be checking permissions
        if(userNotAuthorized(workspaceId, username)) {
            status = Response.Status.UNAUTHORIZED;
            res.setMessage("Unauthorized");
            return Response.ok(res).status(status).build();
        }

        if(streamId > 0) {
            try {
                LOG.debug("Deleting media stream");
                boolean success = msDao.deleteMediaStream(streamId);
                if(success) {
                    LOG.debug("Successfully deleted stream with ID {}", streamId);
                    status = Response.Status.OK;
                } else {
                    LOG.debug("Failed to delete stream with ID {}", streamId);
                    res.setMessage(FAILED_DELETE);
                }
            } catch(Exception e) {
                LOG.error("Exception deleting media stream: {}", e.getMessage(), e);
                if(e.getMessage().contains(SADisplayConstants.MEDIASTREAM_NOT_FOUND)) {
                    res.setMessage(SADisplayConstants.MEDIASTREAM_NOT_FOUND);
                    status = Response.Status.NOT_FOUND;
                } else {
                    res.setMessage(FAILED_DELETE);
                }
            }
        } else {
            LOG.debug("Invalid streamId: {}", streamId);
            status = Response.Status.BAD_REQUEST;
            res.setMessage("Invalid stream ID");
        }

        return Response.ok(res).status(status).build();
    }

    /**
     * Checks if a user is NOT authorized to perform the action.
     *
     * @param workspaceId the workspace ID the stream and user belong to
     * @param username    the username of the user making the request
     * @return true if the user is NOT authorized, false otherwise
     */
    private boolean userNotAuthorized(int workspaceId, String username) {

        User user = getUserByUsername(username);
        if(user == null) {
            return true;
        }


        // want to see if they're enabled in this workspace
        // TODO: must be better way
        List<Org> userOrgs = orgDao.getUserOrgs(user.getUserId(), workspaceId);
        if(userOrgs == null || userOrgs.isEmpty()) {
            return true;
        }

        // TODO: Other auth checks


        return false;


    }

    /**
     * Helper method to get User by username from the EntityCacheMgr.
     *
     * @param username the username of the user to fetch
     * @return the User if found, null otherwise
     */
    private User getUserByUsername(String username) {
        User user = null;
        try {
            user = EntityCacheMgr.getInstance().getUserEntityByUsername(username);
        } catch(ICSDatastoreException e1) {
            LOG.error("Datastore exception getting User entity with username {}", username, e1);
        }
        return user;
    }
}
