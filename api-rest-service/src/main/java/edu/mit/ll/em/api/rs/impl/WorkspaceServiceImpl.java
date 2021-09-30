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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import edu.mit.ll.em.api.rs.WorkspaceResponse;
import edu.mit.ll.em.api.rs.WorkspaceService;

import edu.mit.ll.nics.common.entity.Workspace;
import edu.mit.ll.nics.nicsdao.WorkspaceDAO;
import edu.mit.ll.nics.nicsdao.impl.WorkspaceDAOImpl;

import edu.mit.ll.nics.nicsdao.UserDAO;

/**
 * @author sa23148
 */
public class WorkspaceServiceImpl implements WorkspaceService {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(WorkspaceServiceImpl.class);

    private final WorkspaceDAO workspaceDao = new WorkspaceDAOImpl();
    private final UserDAO userDao = new UserDAOImpl();

    @Override
    public Response getWorkspaces() {
        Response response = null;
        WorkspaceResponse workspaceResponse = new WorkspaceResponse();

        List<Workspace> workspaces = null;
        try {
            workspaces = workspaceDao.getWorkspaces();
            workspaceResponse.setWorkspaces(workspaces);
            workspaceResponse.setCount(workspaces.size());
        } catch(DataAccessException e) {
            log.error("DataAccessException getting workspaces", e);
            workspaceResponse.setMessage("error. " + e.getMessage());
            response = Response.ok(workspaceResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch(Exception e) {
            log.error("Unhandled Exception getting system workspaces ", e);
            workspaceResponse.setMessage("error. Unhandled exception: " + e.getMessage());
            response = Response.ok(workspaceResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        workspaceResponse.setMessage("OK");
        response = Response.ok(workspaceResponse).status(Status.OK).build();

        return response;
    }

    @Override
    public Response getUserWorkspaces(String username) {
        Response response;
        WorkspaceResponse workspaceResponse = new WorkspaceResponse();

        List<Workspace> userWorkspaces = new ArrayList<>();

        Long userId = userDao.getMyUserID(username);

        if(userId == null) {
            workspaceResponse.setMessage("Could not find user in database");
            response = Response.ok(workspaceResponse).status(Status.NOT_FOUND).build();
            return response;
        }

        //TODO get enabled workspaces with this userid.

        try {
            userWorkspaces = workspaceDao.getUserWorkspaces(userId);

            workspaceResponse.setWorkspaces(userWorkspaces);
            workspaceResponse.setCount(userWorkspaces.size());
        } catch(DataAccessException e) {
            log.error("DataAccessException getting workspaces", e);
            workspaceResponse.setMessage("error. " + e.getMessage());
            response = Response.ok(workspaceResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch(Exception e) {
            log.error("Unhandled Exception getting system workspaces ", e);
            workspaceResponse.setMessage("error. Unhandled exception: " + e.getMessage());
            response = Response.ok(workspaceResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        workspaceResponse.setMessage("OK");
        response = Response.ok(workspaceResponse).status(Status.OK).build();

        return response;
    }
}
