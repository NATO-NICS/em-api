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

import java.util.List;
import java.util.Locale;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.lang.NullArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.mit.ll.em.api.rs.RemoteLoggerResponse;
import edu.mit.ll.em.api.dataaccess.EntityCacheMgr;
import edu.mit.ll.em.api.dataaccess.ICSDatastoreException;
import edu.mit.ll.em.api.rs.LoggerService;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.RemoteLog;
import edu.mit.ll.nics.common.entity.RemoteLogType;
import edu.mit.ll.nics.nicsdao.impl.RemoteLogDAOImpl;


/**
 * <p>
 * LoggerServiceImpl receives log messages from clients for printing server side, or for sending to a database.
 * </p>
 * <p>
 * Meant to work in conjunction with a logback config that will sift messages for this class to a separate client log
 * file.
 * </p>
 */
public class LoggerServiceImpl implements LoggerService {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory
            .getLogger(LoggerServiceImpl.class);

    /**
     * Pattern used for logging incoming messages
     */
    private static final String PATTERN = "\nUsername: {}\nUserSessionID: {}\nMessage:\n{}\nError:\n{}";

    /**
     * Whether or not to log to database. Default is false.
     */
    private boolean logToDb;

    /**
     * Whether or not to override logToDb if it's an exception. Default is false.
     */
    private boolean persistExceptions;

    /**
     * The Log level to log the message
     */
    private String logLevel;

    /**
     * RemoteLogDAO instance
     */
    private static final RemoteLogDAOImpl remoteLogDao = new RemoteLogDAOImpl();

    /**
     * Denotes whether or not the config was read
     */
    private boolean isInited;


    /**
     * Initialize the logToDb property
     */
    private void init() {
        log.debug("Initializing LoggerService properties...");
        logToDb = APIConfig.getInstance().getConfiguration()
                .getBoolean(APIConfig.LOGGER_LOGTODB, false);
        persistExceptions = APIConfig.getInstance().getConfiguration()
                .getBoolean(APIConfig.LOGGER_PERSIST_EXCEPTIONS, false);
        logLevel = APIConfig.getInstance().getConfiguration().getString(
                APIConfig.LOGGER_LEVEL, APIConfig.LOGGER_LEVEL_DEFAULT);
        isInited = true;
    }


    /**
     * Accepts a RemoteLog entity from a client, and logs it to a server side log, and calls {@link
     * LoggerServiceImpl#persistLog(RemoteLog)}
     *
     * @param username  the username specified by the CUSTOM-UID header
     * @param logEntity the RemoteLog entity to log
     */
    @Override
    public Response postLog(String username, RemoteLog logEntity) {
        log.debug("Received remote log request from user: {}", username);
        if(!isInited) {
            init();
        }

        RemoteLoggerResponse response = new RemoteLoggerResponse();

        if(logEntity == null) {
            response.setMessage("RemoteLog entity is null, not logging");
            return Response.ok(response).status(Status.BAD_REQUEST).build();
        }


        StringBuffer responseMessage = new StringBuffer(65);

        // Log with logging level specified by logLevel
        doLog(username, logEntity.getUsersessionid(), logEntity);
        responseMessage.append("Logged to server successfully.");

        // Override logToDb and persist if it's of type exception, and
        // persistExceptions is enabled
        boolean persisted = false;
        if(logToDb || isException(logEntity)) {
            persisted = persistLog(logEntity);
        }

        if((logToDb || isException(logEntity)) && !persisted) {
            // error persisting
            responseMessage.append(
                    " Also specified to log to database, but failed.");
        }

        if(persisted) {
            responseMessage.append(" Also successfully logged to database "
                    + ((persistExceptions)
                    ? "(due to persist exceptions override)."
                    : "."));
        }

        response.setMessage(responseMessage.toString());
        return Response.ok(response).status(Status.OK)
                .build();
    }


    /**
     * Uses {@link LoggerServiceImpl#log} to log the message with the log level specified by {@link
     * LoggerServiceImpl#logLevel}
     *
     * @param username      of client
     * @param userSessionId client's usersessionid
     * @param logEntity     the RemoteLog entity containing the message to log
     */
    private void doLog(String username, int userSessionId,
                       RemoteLog logEntity) {

        // If the username isn't set here from the header, then
        // try to set it from the logEntity
        if(username == null || username.isEmpty()) {
            username = logEntity.getUsername();
        }

        switch(logLevel.toLowerCase(Locale.US)) { // TODO: May need to load
            // locale dynamically for
            // non-US locales?
            case "info":
                log.info(PATTERN, username, userSessionId,
                        logEntity.getMessage(), logEntity.getError());
                break;
            case "debug":
                log.debug(PATTERN, username, userSessionId,
                        logEntity.getMessage(), logEntity.getError());
                break;
            case "warn":
                log.warn(PATTERN, username, userSessionId,
                        logEntity.getMessage(), logEntity.getError());
                break;
            case "trace":
                log.trace(PATTERN, username, userSessionId,
                        logEntity.getMessage(), logEntity.getError());
                break;
            default:
                log.warn("Invalid log level specified ({}), so using 'debug'",
                        logLevel);
                log.debug(PATTERN, username, userSessionId,
                        logEntity.getMessage(), logEntity.getError());
                break;
        }
    }


    /**
     * Uses {@link RemoteLogDAOImpl} to persist RemoteLog entity
     *
     * @param log the RemoteLog entity to persist
     * @return true if the RemoteLog entity was successfully persisted, false otherwise
     */
    private boolean persistLog(RemoteLog log) {
        return remoteLogDao.persistLog(log.getWorkspaceid(), log);
    }


    /**
     * Retrieves the configured RemoteLogTypes in the database
     *
     * @return Response object with RemoteLogType entities from database
     */
    @Override
    public Response getLogTypes() {
        List<RemoteLogType> logTypes = remoteLogDao.getLogTypes();

        RemoteLoggerResponse response = new RemoteLoggerResponse();
        response.setTypes(logTypes);
        response.setMessage("Retrieved LogTypes");

        return Response.ok(response).status(Status.OK).build();
    }


    /**
     * If {@link LoggerServiceImpl.persistExceptions} is set, this checks the status member on the specified RemoteLog
     * entity to see if it matches the Exception type.
     *
     * @param logEntity the entity to check for a type of EXCEPTION
     * @return true if persistExceptions is true and the log type is EXCEPTION, false otherwise
     */
    private boolean isException(RemoteLog logEntity)
            throws NullArgumentException {
        if(logEntity == null) {
            throw new NullArgumentException("logEntity");
        }

        if(!this.persistExceptions) {
            return false;
        }

        return getRemoteLogTypeNameById(logEntity.getType())
                .equalsIgnoreCase(SADisplayConstants.EXCEPTION);

    }


    /**
     * Gets the RemoteLogType name associated with the specified id
     *
     * @param id to look up
     * @return the matching name if found, empty String otherwise
     */
    private String getRemoteLogTypeNameById(int id) {
        try {
            return EntityCacheMgr.getInstance().getRemoteLogTypeById(id)
                    .getName();
        } catch(NullPointerException | ICSDatastoreException e) {
            log.error("Exception retrieving RemoteLogType with ID: {}", id, e);
        }

        return "";
    }


    /**
     * Gets the RemoteLogType id associated with the specified name
     *
     * @param name to look up
     * @return the matching id if found, -1 otherwise
     */
    private int getRemoteLogTypeIdByName(String name) {

        try {
            return EntityCacheMgr.getInstance().getRemoteLogTypeByName(name)
                    .getId();

        } catch(NullPointerException | ICSDatastoreException e) {
            log.error("Exception retrieving RemoteLogType.id", e);
        }

        return -1;
    }
}