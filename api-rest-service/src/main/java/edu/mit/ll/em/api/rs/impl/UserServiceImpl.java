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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AlreadyClosedException;

import edu.mit.ll.em.api.rs.FieldMapResponse;
import edu.mit.ll.em.api.rs.GlobalUser;
import edu.mit.ll.em.api.rs.Login;
import edu.mit.ll.em.api.rs.LoginResponse;
import edu.mit.ll.em.api.rs.NewUserOrgResponse;
import edu.mit.ll.em.api.rs.RegisterUser;
import edu.mit.ll.em.api.rs.UserOrgResponse;
import edu.mit.ll.em.api.rs.UserProfileResponse;
import edu.mit.ll.em.api.rs.GlobalAdminUserResponse;
import edu.mit.ll.em.api.rs.UserResponse;
import edu.mit.ll.em.api.rs.UserService;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.UserInfoValidator;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.email.JsonEmail;
import edu.mit.ll.nics.common.entity.Contact;
import edu.mit.ll.nics.common.entity.ContactType;
import edu.mit.ll.nics.common.entity.CurrentUserSession;
import edu.mit.ll.nics.common.entity.EntityEncoder;
import edu.mit.ll.nics.common.entity.IncidentType;
import edu.mit.ll.nics.common.entity.OrgIncidentType;
import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.entity.UserOrg;
import edu.mit.ll.nics.common.entity.UserOrgWorkspace;
import edu.mit.ll.nics.common.entity.Workspace;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserSessionDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.WorkspaceDAOImpl;

import java.util.Set;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * @author sa23148
 */
public class UserServiceImpl implements UserService {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static String FAILURE = "An error occurred while registering your account.";
    private static String FAILURE_NAMES = "A first and last name is required.";
    private static String FAILURE_USERNAME = "*** username already exists ***";
    private static String FAILURE_PHONE_NUMBERS = "Phone number is not a valid format.";
    private static String FAILURE_USERNAME_INVALID = "Your username must be in a valid email format.";
    private static String FAILURE_ORG_INVALID = "Invalid organization.";
    private static String FAILURE_ORG_BLANK = "You must choose an organization.";
    private static String FAILURE_EMAIL = "An email address is required.";
    private static String FAILURE_OTHER_EMAIL = "The Other Email is invalid.";
    private static String FAILURE_KEYCLOAK = "There was an error updating the user's status.";
    private static String SUCCESS = "success";
    private static String SAFECHARS = "Valid input includes letters, numbers, spaces, and these special "
            + "characters: , _ # ! . -";

    private static final String INVALID_ID = "Invalid user id value.";
    private static final String NO_USER_FOUND = "No user found.";

    private static final String CLIENT_ID = "client_id";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String GRANT_TYPE = "grant_type";
    private static final String BEARER = "bearer";
    private static final String ACCESS_TOKEN = "access_token";

    private static String DATA_ACCESS_ERROR = "Data Access Error";
    private static String UNHANDLED_EXCEPTION = "Unhandled Exception";

    private static final Log logger = LogFactory.getLog(UserServiceImpl.class);

    private final UserDAOImpl userDao = new UserDAOImpl();
    private final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();
    private final UserSessionDAOImpl userSessDao = new UserSessionDAOImpl();
    private final OrgDAOImpl orgDao = new OrgDAOImpl();

    private RabbitPubSubProducer rabbitProducer;

	/**
	 * Return the User specified by the username in the identity header.
	 *
	 * @param username the username taken from the identity header
	 *
	 * @return A UserResponse with the user in the list of users if found, otherwise
	 * 		   A UserResponse with the message detailing the problem
	 */
	public Response whoAmI(String username) {
		UserResponse userResponse = new UserResponse();
		Status status;

		if(username == null || username == "") {
			return Response.ok(userResponse).status(Status.EXPECTATION_FAILED).build();
		}

		User user = null;
		try {
			user = userDao.getUser(username);
			if(user != null) {
				userResponse.setMessage("Success");
				userResponse.setUsers(Arrays.asList(user));
				userResponse.setCount(1);
				status = Status.OK;
			} else {
				userResponse.setMessage("User not found");
				status = Status.NOT_FOUND;
			}

		} catch(DataAccessException e) {
			log.error("Exception looking up User by username", e);
			userResponse.setMessage("Error looking up user by username");
			status = Status.INTERNAL_SERVER_ERROR;
		}

		return Response.ok(userResponse).status(status).build();
	}

    /**
     * Read and return all User items in workspace
     *
     * @return Response
     *
     * @see UserResponse
     */
    public Response getUsers(int workspaceId) {
        //Get all users in the workspace
        return this.getUsers(workspaceId, -1);
    }

    /**
     * Read and return all User items in workspace
     *
     * @return Response
     *
     * @see UserResponse
     */
    public Response getUsers(int workspaceId, int orgId) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        List<edu.mit.ll.nics.common.entity.User> users = null;
        try {
            users = userDao.getEnabledUsersInWorkspace(workspaceId, orgId);
            if(users != null && users.size() > 0) {
                userResponse.setUsers(users);
            } else {
                log.debug("No enabled users in workspace with id {}", workspaceId);
            }

        } catch(DataAccessException e) {
            logger.error(e.getMessage());
            userResponse.setMessage(DATA_ACCESS_ERROR);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch(Exception e) {
            logger.error(e.getMessage());
            userResponse.setMessage(UNHANDLED_EXCEPTION);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        userResponse.setMessage(Status.OK.toString());
        response = Response.ok(userResponse).status(Status.OK).build();

        return response;
    }

    /**
     * Read and return all Active User items in workspace
     *
     * @return Response
     *
     * @see UserResponse
     */
    public Response getActiveUsers(int workspaceId) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        List<edu.mit.ll.nics.common.entity.User> users = null;
        try {

            users = userDao.getActiveUsers(workspaceId);
            userResponse.setUsers(users);
            if(users != null && users.size() > 0) {
                log.debug("GOT active users: {}", users.size());
            } else {
                log.debug("No active users in workspace with id {}", workspaceId);
            }

        } catch(DataAccessException e) {
            logger.error(e.getMessage());
            userResponse.setMessage(DATA_ACCESS_ERROR);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch(Exception e) {
            logger.error(e.getMessage());
            userResponse.setMessage(UNHANDLED_EXCEPTION);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        userResponse.setMessage(Status.OK.getReasonPhrase());
        response = Response.ok(userResponse).status(Status.OK).build();

        return response;
    }

    public Response isAdmin(int userOrgId) {
        Response response = null;
        UserResponse userResponse = new UserResponse();
        int systemRoleId = userOrgDao.getSystemRoleId(userOrgId);
        if(systemRoleId == SADisplayConstants.ADMIN_ROLE_ID ||
                systemRoleId == SADisplayConstants.SUPER_ROLE_ID) {
            userResponse.setCount(1);
            userResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(userResponse).status(Status.OK).build();
        } else {
            return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
        }
        return response;
    }

    public Response getLoginStatus(int workspaceId, String username) {
        Response response = null;
        UserResponse userResponse = new UserResponse();
        int userId = userDao.isActive(username);
        if(userId != -1 && userOrgDao.hasEnabledOrgs(userId, workspaceId) > 0) {
            userResponse.setCount(1);
            userResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(userResponse).status(Status.OK).build();
        } else {
            userResponse.setMessage(Status.FORBIDDEN.getReasonPhrase());
            response = Response.ok(userResponse).status(Status.OK).build();
        }
        return response;
    }

    /**
     * Delete all User items.
     *
     * <p>Unsupported Operation!</p>
     *
     * @return Response
     *
     * @see UserResponse
     */
    public Response deleteUsers() {
        return makeUnsupportedOpRequestResponse();
    }


    /**
     * Bulk creation of User items.
     *
     * <p>Unsupported Operation!</p>
     *
     * @param users collection of User items to be created.
     * @return Response
     *
     * @see UserResponse
     */
    public Response putUsers(Collection<User> users) {

        return makeUnsupportedOpRequestResponse();
    }

    public Response addUserToOrg(Collection<Integer> userIds, int orgId, int workspaceId) {
        NewUserOrgResponse userResponse = new NewUserOrgResponse();
        List<Integer> users = new ArrayList<>();
        List<Integer> failedUsers = new ArrayList<>();
        int curUserId = -1;
        try {
            for(Integer userId : userIds) {
                curUserId = userId;
                if(userOrgDao.getUserOrgById(orgId, userId, workspaceId) == null) {
                    UserOrg userorg = createUserOrg(orgId, curUserId, null);

					/*List<UserOrgWorkspace> userOrgWorkspaces = new ArrayList<>(
							createUserOrgWorkspaceEntities(userorg, true));*/

                    if(userDao.addUserToOrg(userId, Arrays.asList(userorg), getWorkspaceIds())) {
                        users.add(userId);

						/*try {
							notifyUserAddedToOrg(userorg, workspaceId);
						} catch (Exception e) {
							log.error("Exception publishing userorg change notification: ", e);
						}*/

                    } else {
                        failedUsers.add(userId);
                    }
                } else {
                    failedUsers.add(userId);
                }
            }
            userResponse.setFailedUsers(failedUsers);
            userResponse.setUsers(users);

        } catch(Exception e) {
            // TODO: bad practice, need to either treat as transaction, and roll all back, or
            // move try/catch inside on individual user attempts so one failure doesn't break
            // from successfully adding the rest - JP
            log.error("Unhandled exception adding user ({}) to org ({})", curUserId, e);
        }
        return Response.ok(userResponse).status(Status.OK).build();
    }

    private void notifyUserAddedToOrg(int userId, int workspaceId, int userorgWorkspaceId)
            throws IOException, TimeoutException, AlreadyClosedException {

        final String topic = String.format("iweb.NICS.%d.user.%d.userorgupdate",
                workspaceId, userId);
        //ObjectMapper mapper = new ObjectMapper();
        //String message = mapper.writeValueAsString("Update to userorgworkspace " + userorgWorkspaceId);
        String message = "Update to userorgworkspace " + userorgWorkspaceId;
        getRabbitProducer().produce(topic, message);
    }

    /**
     * Creation of a single User item.
     *
     * @param registerUser to be created.
     * @return Response
     *
     * @see UserResponse
     */
    public Response postUser(int workspaceId, RegisterUser registerUser) {
        String successMessage = "Successfully registered user ";

        try {

            String validate = validateRegisterUser(registerUser);

            if(!validate.equals(SUCCESS)) {
                return Response.ok(validate).status(Status.PRECONDITION_FAILED).build();
            }

            User user = createUser(registerUser);

            OrgDAOImpl orgDao = new OrgDAOImpl();

            int orgId = -1;
            if(validateStringAsInt(registerUser.getOrganization())) {
                orgId = Integer.parseInt(registerUser.getOrganization(), 10);
            } else {
                return Response.ok("Received invalid Organization ID, could not parse integer value from string: " +
                        registerUser.getOrganization()).status(Status.PRECONDITION_FAILED).build();
            }

            Org org = orgDao.getOrganization(orgId);
            if(org == null) {
                // need to fail, can't get org
                return Response.ok(FAILURE_ORG_INVALID + ": " + registerUser.getOrganization())
                        .status(Status.PRECONDITION_FAILED).build();
            }

            UserOrg userOrg = createUserOrg(org.getOrgId(), user.getUserId(), registerUser);

            if(userOrg == null) {
                log.warn("!!! FAILED to create userOrg for user: {}", registerUser.getEmail());
                return Response.ok("Failed to create UserOrg with orgId '" + org.getOrgId() +
                        "' and userId '" + user.getUserId() + "' for user: " + registerUser.getEmail()).
                        status(Status.PRECONDITION_FAILED).build();
            }

            List<Contact> contactSet = createContactsList(registerUser.getEmail(),
                    registerUser.getOtherEmail(),
                    registerUser.getOfficePhone(),
                    registerUser.getCellPhone(),
                    registerUser.getOtherPhone(),
                    registerUser.getRadioNumber(),
                    user.getUserId());

            try {

                boolean registerSuccess = userDao.registerUser(user, contactSet, Arrays.asList(userOrg),
                        //createUserOrgWorkspaceEntities(userOrg, false));
                        getWorkspaceIds());

                if(!registerSuccess) {
                    log.warn("!!! FAILED to create userOrg for user: {}", registerUser.getEmail());
                    return Response.ok("Failed to create UserOrg with orgId '" + org.getOrgId() +
                            "' and userId '" + user.getUserId() + "' for user: " + registerUser.getEmail()).
                            status(Status.PRECONDITION_FAILED).build();

                } else {
                    // User was successfully registered with NICS
                    // Get the new user, so we have the right id
                    User newUser = userDao.getUser(registerUser.getEmail());
                    if(newUser != null) {
                        Map<String, Object> disabledUserOrg = userOrgDao.getDisabledUserOrg(orgId,
                                newUser.getUserId(), workspaceId);
                        if(disabledUserOrg != null) {
                            notifyNewUserRegistered(workspaceId, disabledUserOrg);
                        }
                    } else {
                        log.warn("There was an issue querying for the user with username {}, so couldn't publish a " +
                                "notification event with the new user", registerUser.getEmail());
                    }

                    this.emailRegisteredUser(user, org, registerUser.getOtherInfo(),
                            orgDao.getOrgAdmins(org.getOrgId()));
                }
            } catch(DataAccessException e) {
                log.error("Exception creating user: {}", registerUser.getEmail(), e);
                return Response.ok("Failed to register user: " + e.getMessage()).status(Status.INTERNAL_SERVER_ERROR)
                        .build();
            } catch(Exception e) {
                log.error("Exception creating user: {}", registerUser.getEmail(), e);
                return Response.ok("Failed to register user: " + e.getMessage()).status(Status.INTERNAL_SERVER_ERROR)
                        .build();
            }

        } catch(Exception e) {
            log.error("Unhandled exception creating user: {}", registerUser.getEmail(), e);
            return Response.ok(FAILURE).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(successMessage + registerUser.getEmail()).status(Status.OK).build();
    }

    /**
     * Performs logout actions for specified user
     *
     * @param username Username of user to log out
     * @return Response
     *
     * @see LoginResponse
     */
    public Response deleteLogin(String username) {
        Response response = null;
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setMessage(Status.OK.getReasonPhrase());

        return response;
    }

    private void emailRegisteredUser(User user, Org org, String otherInfo, List<String> disList) {
        try {
            String fromEmail = APIConfig.getInstance().getConfiguration().getString(APIConfig.NEW_USER_ALERT_EMAIL);
            String date = new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy").format(new Date());
            String alertTopic = APIConfig.getInstance().getConfiguration().getString(APIConfig.EMAIL_ALERT_TOPIC,
                    "iweb.nics.email.alert");
            String newRegisteredUsers =
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.NEW_REGISTERED_USER_EMAIL);
            String hostname = InetAddress.getLocalHost().getHostName();
            String toEmails =
                    disList.toString().substring(1, disList.toString().length() - 1) + ", " + newRegisteredUsers;

            if(disList.size() > 0 && !fromEmail.isEmpty()) {
                JsonEmail email = new JsonEmail(fromEmail, toEmails,
                        "Alert from RegisterAccount@" + hostname);
                email.setBody(date + "\n\n" + "A new user has registered: " + user.getUsername() + "\n" +
                        "Name: " + user.getFirstname() + " " + user.getLastname() + "\n" +
                        "Organization: " + org.getName() + "\n" +
                        "Email: " + user.getUsername() + "\n" +
                        "Other Information: " + otherInfo);

                notifyNewUserEmail(email.toJsonObject().toString(), alertTopic);
            }

        } catch(Exception e) {
            log.error("Failed to send new User email alerts", e);
        }
    }


    /**
     * Builds a JSON response object
     *
     * @param status  Content of the status field
     * @param message A message explaining the status
     * @return A JSONObject with a 'status' and 'message' field populated if successful, otherwise an empty JSONObject
     */
    public JSONObject buildJSONResponse(String status, String message) {
        JSONObject response = new JSONObject();

        try {
            response.put("status", status);
            response.put("message", message);
        } catch(JSONException e) {
            log.error("JSONException building response: {}",
                    e.getMessage(), e);
        }

        return response;
    }


    /**
     * Creates {@link UserOrgWorkspace} entities for every {@link Workspace} in the database
     *
     * @param userOrg The {@link UserOrg} to create workspace entries for
     * @param enabled Whether or no the {@link UserOrgWorkspace} entry is enabled or not
     * @return A collection of {@link UserOrgWorkspace} entities
     */
    private List<UserOrgWorkspace> createUserOrgWorkspaceEntities(UserOrg userOrg, boolean enabled) {

        List<UserOrgWorkspace> workspaces = new ArrayList<UserOrgWorkspace>();

        List<Integer> workspaceIds = getWorkspaceIds();
        if(workspaceIds == null || workspaceIds.isEmpty()) {
            return workspaces; // TODO: caller needs to know an empty one may mean an error occurred, not just
            // that there are no workspaces
        }

        for(Integer id : workspaceIds) {
            UserOrgWorkspace workspace = new UserOrgWorkspace();
            workspace.setUserorgid(userOrg.getUserorgid());
            workspace.setWorkspaceid(id);
            workspace.setEnabled(enabled);
            workspace.setDefaultorg(false);

            workspaces.add(workspace);
        }

        return workspaces;
    }

    /**
     * Creates a list of {@link Contact} objects for the specified values
     *
     * @param email
     * @param otherEmail
     * @param officePhone
     * @param cellPhone
     * @param otherPhone
     * @param radioNumber
     * @param userId      The user the contact info is associated with
     * @return A list of {@link Contact} objects
     */
    private List<Contact> createContactsList(String email,
                                             String otherEmail, String officePhone, String cellPhone,
                                             String otherPhone, String radioNumber,
                                             int userId) {

        List<Contact> contacts = new ArrayList<Contact>();

        if(email != null && email != "") {
            contacts.add(this.createContact(SADisplayConstants.EMAIL_TYPE, email, userId));
        }
        if(otherEmail != null && otherEmail != "") {
            contacts.add(this.createContact(SADisplayConstants.EMAIL_TYPE, otherEmail, userId));
        }
        if(cellPhone != null && cellPhone != "") {
            contacts.add(this.createContact(SADisplayConstants.PHONE_CELL_TYPE, cellPhone, userId));
        }
        if(officePhone != null && officePhone != "") {
            contacts.add(this.createContact(SADisplayConstants.PHONE_OFFICE_TYPE, officePhone, userId));
        }
        if(otherPhone != null && otherPhone != "") {
            contacts.add(this.createContact(SADisplayConstants.PHONE_OTHER_TYPE, otherPhone, userId));
        }
        if(radioNumber != null && radioNumber != "") {
            contacts.add(this.createContact(SADisplayConstants.RADIO_NUMBER_TYPE, radioNumber, userId));
        }
        return contacts;

    }

    /**
     * Utility method for creating a {@link Contact}
     *
     * @param type   The {@link ContactType}
     * @param value  The value for this Contact
     * @param userId The {@link User} ID this Contact is associated with
     * @return A {@link Contact} of the specified type and value
     */
    private Contact createContact(String type, String value, int userId) {
        return this.createContact(userDao.getContactTypeId(type), value, userId);
    }

    /**
     * Utility method for creating a {@link Contact}
     *
     * @param contactTypeId The {@link ContactType}
     * @param value         The value for this Contact
     * @param userId        The {@link User} ID this Contact is associated with
     * @return A {@link Contact} of the specified type and value
     */
    private Contact createContact(int contactTypeId, String value, int userId) {
        Contact contact = null;
        try {
            contact = new Contact();
            contact.setContacttypeid(contactTypeId);
            contact.setValue(value);
            contact.setCreated(Calendar.getInstance().getTime());
            contact.setUserId(userId);
            contact.setEnabled(true);
        } catch(Exception e) {
            log.error("Exception creating contact.", e);
        }
        return contact;
    }

    public Response getSystemRoles() {
        return Response.ok(userOrgDao.getSystemRoles()).status(Status.OK).build();
    }

    /**
     * Read a single User item.
     *
     * @param userId ID of User item to be read.
     * @return Response
     *
     * @see UserResponse
     */
    public Response getUser(int userId) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        if(userId < 1) {
            logger.error(String.format("%s:%s", INVALID_ID, userId));
            userResponse.setMessage(INVALID_ID);
            response = Response.ok(userResponse).status(Status.BAD_REQUEST).build();
            return response;
        }

        //User u = null;
        edu.mit.ll.nics.common.entity.User u = null;
        try {
            u = userDao.getUserById(userId);
        } catch(DataAccessException e) {
            logger.error(e.getMessage());
            userResponse.setMessage(DATA_ACCESS_ERROR);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch(Exception e) {
            logger.error(e.getMessage());
            userResponse.setMessage(UNHANDLED_EXCEPTION);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }
        if(u == null) {
            logger.error(String.format("%s:%s", NO_USER_FOUND, userId));
            userResponse.setMessage(NO_USER_FOUND);
            response = Response.ok(userResponse).status(Status.NOT_FOUND).build();
            return response;
        }
        userResponse.getUsers().add(u);
        userResponse.setCount(1);
        userResponse.setMessage(Status.OK.getReasonPhrase());
        response = Response.ok(userResponse).status(Status.OK).build();

        return response;
    }

    /**
     * Read a single User item.
     *
     * @param usersessionId of User item to be read. (currentSession only)
     * @return Response
     *
     * @see UserResponse
     */
    public Response getUserBySessionId(long usersessionId) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        if(usersessionId < 1) {
            userResponse.setMessage("Invalid usersessionId value: " + usersessionId);
            response = Response.ok(userResponse).status(Status.BAD_REQUEST).build();
            return response;
        }
        log.debug("mysession id: {}", usersessionId);
        //User u = null;

        edu.mit.ll.nics.common.entity.User u = null;
        try {
            u = userDao.getUserBySessionId(usersessionId);
        } catch(DataAccessException e) {
            userResponse.setMessage("error. DataAccessException: " + e.getMessage());
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch(Exception e) {
            userResponse.setMessage("error. Unhandled Exception: " + e.getMessage());
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        if(u == null) {
            userResponse.setMessage("No user found for usersessionId value: " + usersessionId);
            response = Response.ok(userResponse).status(Status.NOT_FOUND).build();
            return response;
        }

        userResponse.getUsers().add(u);
        userResponse.setCount(1);
        userResponse.setMessage("ok");
        response = Response.ok(userResponse).status(Status.OK).build();

        return response;
    }

    /**
     * Read a single User item.
     *
     * @param usersessionId usersessionid of User to be read
     * @return Response
     *
     * @see UserResponse
     */
    public Response getUserByPastSessionId(long usersessionId) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        if(usersessionId < 1) {
            userResponse.setMessage("Invalid usersessionId value: " + usersessionId);
            response = Response.ok(userResponse).status(Status.BAD_REQUEST).build();
            return response;
        }
        log.debug("usersession id: {}", usersessionId);
        //User u = null;
        edu.mit.ll.nics.common.entity.User u = null;
        try {
            //u = UserDAO.getInstance().getUserById(userId);
            u = userDao.getUserByPastSessionId(usersessionId);
        } catch(DataAccessException e) {
            userResponse.setMessage("error. DataAccessException: " + e.getMessage());
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        } catch(Exception e) {
            userResponse.setMessage("error. Unhandled Exception: " + e.getMessage());
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        if(u == null) {
            userResponse.setMessage("No user found for usersessionId value: " + usersessionId);
            response = Response.ok(userResponse).status(Status.NOT_FOUND).build();
            return response;
        }

        userResponse.getUsers().add(u);
        userResponse.setCount(1);
        userResponse.setMessage("ok");
        response = Response.ok(userResponse).status(Status.OK).build();

        return response;
    }

    public Response getUserWithSession(int userorgId, String requestingUser) {

        UserResponse userResponse = new UserResponse();

        try {
            User user = userDao.getUser(requestingUser);

            if(user == null) {
                userResponse.setMessage("User not found");
                return Response.ok(userResponse).status(Status.NOT_FOUND).build();
            }

            if(!userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
                userResponse.setMessage("User not authorized");
                return Response.ok(userResponse).status(Status.UNAUTHORIZED).build();
            }


            int usersessionId = userSessDao.getUserSessionIdViaUserOrgId(userorgId);
            if(usersessionId == -1) {
                userResponse.setMessage("UsersessionId not found");
                return Response.ok(userResponse).status(Status.NOT_FOUND).build();
            }

            if(user != null) {
                CurrentUserSession currentUserSession = new CurrentUserSession();
                currentUserSession.setUsersessionid(usersessionId);
                Set<CurrentUserSession> sessions = new HashSet<>();
                sessions.add(currentUserSession);
                user.setCurrentusersessions(sessions);
                userResponse.setMessage("OK");
                userResponse.setUsers(Arrays.asList(user));
                userResponse.setCount(1);
                return Response.ok(userResponse).status(Status.OK).build();
            } else {
                userResponse.setMessage(String.format("Problem retrieving User with session for username %s",
                        requestingUser));
                return Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }

        } catch(Exception e) {
            log.error("Exception retrieving User with session for username {}", requestingUser, e);
            userResponse.setMessage(String.format("Unhandled exception %s", e.getMessage()));
            return Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

    }

    public Response findUser(String firstName, String lastName, boolean exact) {
        Response response = null;
        UserResponse userResponse = new UserResponse();
        List<User> foundUsers = null;

        if(firstName != null) {
            if(lastName != null) {
                foundUsers = userDao.findUser(firstName, lastName, exact);
            } else {
                foundUsers = userDao.findUserByFirstName(firstName, exact);
            }
        } else if(lastName != null) {
            foundUsers = userDao.findUserByLastName(lastName, exact);
        }

        if(foundUsers != null) {
            userResponse.setUsers(foundUsers);
            userResponse.setCount(foundUsers.size());
            userResponse.setMessage(Status.OK.toString());
            response = Response.ok(userResponse).status(Status.OK).build();
        } else {
            userResponse.setMessage(NO_USER_FOUND);
            response = Response.ok(userResponse).status(Status.NOT_FOUND).build();
        }

        return response;
    }

    public Response setUserActive(int userOrgWorkspaceId, int userId, boolean active, String requestingUser) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        int systemRoleId = userOrgDao.getSystemRoleId(requestingUser, userOrgWorkspaceId);

        if((systemRoleId == SADisplayConstants.ADMIN_ROLE_ID || systemRoleId == SADisplayConstants.SUPER_ROLE_ID) ||
                userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {

            userDao.setUserActive(userId, active);

            User responseUser = userDao.getUserById(userId);
            userResponse.setUsers(Arrays.asList(responseUser));

            userResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(userResponse).status(Status.OK).build();
        } else {
            return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
        }

        return response;
    }


    /**
     * Delete a single User item.
     *
     * @param userId ID of User item to be read.
     * @return Response
     *
     * @see UserResponse
     */
    public Response deleteUser(int userId) {

        return makeUnsupportedOpRequestResponse();
    }


    /**
     * Update a single User item.
     *
     * @param userId ID of User item to be read.
     * @param user   User entity to update
     * @return Response
     *
     * @see UserResponse
     */
    public Response putUser(int userId, User user) {

        return makeUnsupportedOpRequestResponse();
    }

    public Response getEnabledUsers(int workspaceId, int orgId) {
        Response response = null;
        FieldMapResponse dataResponse = new FieldMapResponse();
        dataResponse.setData(userOrgDao.getEnabledUserOrgs(orgId, workspaceId));

        dataResponse.setMessage(Status.OK.getReasonPhrase());
        response = Response.ok(dataResponse).status(Status.OK).build();

        return response;
    }

    public Response getDisabledUsers(int workspaceId, int orgId) {
        Response response = null;
        FieldMapResponse dataResponse = new FieldMapResponse();
        dataResponse.setData(userOrgDao.getDisabledUserOrgs(orgId, workspaceId));

        dataResponse.setMessage(Status.OK.getReasonPhrase());
        response = Response.ok(dataResponse).status(Status.OK).build();

        return response;
    }

    public Response setUserEnabled(int userOrgWorkspaceId, int userId,
                                   int workspaceId, boolean enabled, String requestingUser) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        int systemRoleId = userOrgDao.getSystemRoleId(requestingUser, userOrgWorkspaceId);

        if((systemRoleId == SADisplayConstants.ADMIN_ROLE_ID ||
                systemRoleId == SADisplayConstants.SUPER_ROLE_ID) ||
                userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {

            int count = userOrgDao.setUserOrgEnabled(userOrgWorkspaceId, enabled);

            if(count == 1) {

                User newUser = userDao.getUserById(userId);
                userResponse.setOrgCount(userOrgDao.hasEnabledOrgs(userId, workspaceId));

                try {
                    this.setKeycloakEnabled(newUser.getUsername(), enabled, userResponse.getOrgCount());
                } catch(Exception e) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR).entity(FAILURE_KEYCLOAK).build();
                }

                try {
                    notifyUserAddedToOrg(userId, workspaceId, userOrgWorkspaceId);
                } catch(Exception e) {
                    log.error("Exception notifying user of being enabled in org", e);
                }

                if(enabled) {

                    try {
                        String fromEmail =
                                APIConfig.getInstance().getConfiguration().getString(APIConfig.NEW_USER_ENABLED_EMAIL);
                        String alertTopic = String.format("iweb.nics.email.alert");
                        String emailTemplate = APIConfig.getInstance().getConfiguration()
                                .getString(APIConfig.NEW_USER_BODY_TEMPLATE);
                        String emailBody;
                        String emailSubject = APIConfig.getInstance().getConfiguration()
                                .getString(APIConfig.NEW_USER_BODY_SUBJECT, "Welcome to Team NICS!");
                        if(emailTemplate != null) {
                            emailBody = new String(Files.readAllBytes(Paths.get(emailTemplate)));
                        } else {
                            emailBody = "<html><p>Please review the following documentation: " +
                                    "<a href=\"https://public.nics.ll.mit" +
									".edu/nicshelp/documents/2015-06-05%20-%20NICS%20Talking%20Points%20v3.pdf\">" +
                                    "NICS Talking Points</a> & <a href=\"https://public.nics.ll.mit" +
									".edu/nicshelp/documents/NICS_GL_4_14.pdf\">" +
                                    "NICS Guidelines</a></p><ul><li>Web browsers: NICS works well with IE 9 and later" +
									" FireFox Safari Chrome and others." +
                                    "  It does not work with IE 8 and earlier.</li><li>When you visit the <b><span " +
									"style='color:#0056D6'>INCIDENT LOGIN</span>" +
                                    "</b> side of NICS. Please remember that there could be working incidents there " +
									"so use care in navigating around.</li>" +
                                    "<li>Learning about NICS:</li><ul>"
                                    +
                                    "<li>The <b><span style='color:#0056D6'>TRAINING LOGIN</span></b> side of NICS is" +
									" much more forgiving." +
                                    " As you can't break anything there and it has the same tools and format as the " +
									"INCIDENT side so it is a great place to "
                                    +
                                    "experiment and practice.</li><li>The <b><span " +
									"style='color:#0056D6'>UNDO</span></b>"
                                    + " button is helpful as you learn to navigate around NICS - " +
                                    "you can change back up to your last 5 actions.</li>><li>A HELP site is available" +
									" that includes some videos and tutorials at: " +
                                    "<a href=\"http://public.nics.ll.mit.edu/nicshelp/\">http://public.nics.ll.mit" +
									".edu/nicshelp/</a>. You can also access this site " +
                                    "from within NICS by selecting the <span style='color:#0056D6'>HELP</span>"
                                    + " button in the upper right corner of the Web browser.</li>" +
                                    "<li>References: </li><ul><li>Wildfire Today Article:" +
                                    " <a href=\"http://wildfiretoday" +
									".com/2014/02/10/new-communication-tool-enhances-incident-management/\">" +
                                    "http://wildfiretoday.com/2014/02/10/new-communication-tool-enhances-incident" +
									"-management/</a></li><li>You Tube Video: " +
                                    "<a href=\"http://youtu.be/mADTLY0t_eM\">http://youtu" +
									".be/mADTLY0t_eM</a></li></ul><li>NICS Mobile:</li>" +
                                    "<ul><li>The NICS Mobile Application instructions are available: <a " +
									"href=\"https://public.nics.ll.mit.edu/nicshelp/\">" +
                                    "https://public.nics.ll.mit.edu/nicshelp/</a></li><li>Scroll down to the NICS " +
									"Mobile section for details.</li></ul>" +
                                    "</ul></ul></body></html>";
                        }

                        JsonEmail email = new JsonEmail(fromEmail, newUser.getUsername(), emailSubject);

                        email.setBody(emailBody);

                        notifyNewUserEmail(email.toJsonObject().toString(), alertTopic);

                    } catch(Exception e) {
                        log.error("Failed to send new User email alerts when enabling user with id: {}", userId, e);
                    }
                }

                User responseUser = userDao.getUserById(userId);
                userResponse.setUsers(Arrays.asList(responseUser));
            }
            userResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(userResponse).status(Status.OK).build();
        } else {
            return Response.status(Status.BAD_REQUEST).entity(
                    Status.FORBIDDEN.getReasonPhrase()).build();
        }

        return response;
    }

    public Response isSuperUser(String username) {
        if(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID)) {
            return Response.ok().status(Status.OK).build();
        }

        return Response.status(Status.BAD_REQUEST).entity(Status.FORBIDDEN.getReasonPhrase()).build();
    }

    public Response getAdminUsers(String username, int workspaceId){
        GlobalAdminUserResponse response = new GlobalAdminUserResponse();
        if(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID)) {
            List<User> users = userDao.getAllUsers(workspaceId);
            response.setAdminUsers(response.transform(users));
        }else{
            try {
                int userId = (new Long(userDao.getUserId(username))).intValue();
                response.setAdminUsers(response.transform(userDao.getAdminUsers(userId, workspaceId)));
            }catch(Exception e){
                response.setMessage("There was an error retrieving the user id.");
            }
        }

        return Response.ok(response).status(Status.OK).build();
    }

    public Response getUserProfile(String username, int userOrgId,
                                   int workspaceId, int orgId, int rUserOrgId, String requestingUser) {
        Response response = null;
        UserProfileResponse profileResponse = new UserProfileResponse();

        if(!username.equalsIgnoreCase(requestingUser)) {

            //User is not requesting their own profile
            int requestingUserRole = userOrgDao.getSystemRoleIdForUserOrg(requestingUser, rUserOrgId);

            //Verify the request user is an admin for the organization or a super user for any other organization
            if(requestingUserRole != SADisplayConstants.ADMIN_ROLE_ID &&
                    !userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {

                return Response.status(Status.BAD_REQUEST).entity(
                        Status.FORBIDDEN.getReasonPhrase()).build();
            }
        }

        try {

            OrgDAOImpl orgDao = new OrgDAOImpl();
            Org org = orgDao.getOrganization(orgId);
            edu.mit.ll.nics.common.entity.User user = userDao.getUser(username);

            UserOrg userOrg = userOrgDao.getUserOrgById(org.getOrgId(), user.getUserId(), workspaceId);

            IncidentDAOImpl incidentDao = new IncidentDAOImpl();

            List<OrgIncidentType> orgIncidentTypes = orgDao.getIncidentTypes(orgId);
            if(orgIncidentTypes == null || orgIncidentTypes.size() == 0){
                profileResponse.setIncidentTypes(incidentDao.getIncidentTypes(true));
            }else{
                List<IncidentType> incidentTypes = new ArrayList<IncidentType>();
                for(OrgIncidentType orgIncidentType : orgIncidentTypes) {
                    incidentTypes.add(orgIncidentType.getIncidenttype());
                }
                profileResponse.setIncidentTypes(incidentTypes);
            }

            profileResponse.setChildOrgs(orgDao.getChildOrgs(orgId));

            profileResponse.setUserOrgId(userOrgId);
            profileResponse.setUsername(username);
            profileResponse.setOrgName(org.getName());
            profileResponse.setOrgId(org.getOrgId());
            profileResponse.setOrgPrefix(org.getPrefix());
            profileResponse.setWorkspaceId(workspaceId);
            profileResponse.setUserId(user.getUserId());
            profileResponse.setUserFirstname(user.getFirstname());
            profileResponse.setUserLastname(user.getLastname());
            profileResponse.setRank(userOrg.getRank());
            profileResponse.setDescription(userOrg.getDescription());
            profileResponse.setJobTitle(userOrg.getJobTitle());
            profileResponse.setSysRoleId(userOrg.getSystemroleid());
            profileResponse.setIsSuperUser(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID));
            profileResponse.setIsAdminUser(userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID));
            profileResponse.setDefaultLanguage(userOrg.getDefaultLanguage());
            profileResponse.setOrgDefaultLanguage(org.getDefaultlanguage());
            profileResponse.setRestrictIncidents(org.getRestrictincidents());
            profileResponse.setCreateIncidentRequiresAdmin(org.getCreateincidentrequiresadmin());
            profileResponse.setMessage(Status.OK.getReasonPhrase());

            response = Response.ok(profileResponse).status(Status.OK).build();
        } catch(DataAccessException ex) {
            log.error("DAO exception getting user profile for user {}", username, ex);
            return Response.ok("error. DAO exception getting user profile: " + ex.getMessage()).
                    status(Status.INTERNAL_SERVER_ERROR).build();

        } catch(Exception e) {
            log.error("Unhandled exception getting user profile for user {}", username, e);
            return Response.ok("error. Unhandled exception getting user profile: " + e.getMessage()).
                    status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    public Response postUserProfile(edu.mit.ll.em.api.rs.User user, String requestingUser, int rUserOrgId) {

        Response response = null;
        UserProfileResponse profileResponse = new UserProfileResponse();

        if(!user.getUserName().equalsIgnoreCase(requestingUser)) {

            //User is not requesting their own profile
            int requestingUserRole = userOrgDao.getSystemRoleIdForUserOrg(requestingUser, rUserOrgId);
            if((requestingUserRole != SADisplayConstants.SUPER_ROLE_ID &&
                    requestingUserRole != SADisplayConstants.ADMIN_ROLE_ID) &&
                    //Verify the request user is a super user for any other organization
                    !userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {

                return Response.status(Status.BAD_REQUEST).entity(
                        Status.FORBIDDEN.getReasonPhrase()).build();
            }
        }

        try {

            User dbUser = userDao.getAllUserInfoById(user.getUserId());
            UserOrg userOrg = userOrgDao.getUserOrg(user.getUserOrgId());

            userDao.updateNames(user.getUserId(), user.getFirstName(), user.getLastName());
            userOrgDao.updateUserOrg(user.getUserOrgId(), user.getJobTitle(), user.getRank(), user.getJobDesc(),
                    user.getSysRoleId(),
                    user.getDefaultLanguage());

            dbUser = userDao.getAllUserInfoById(user.getUserId());
            userOrg = userOrgDao.getUserOrg(user.getUserOrgId());

            profileResponse.setUserOrgId(userOrg.getUserorgid());
            profileResponse.setOrgId(userOrg.getOrgId());
            profileResponse.setUserId(dbUser.getUserId());
            profileResponse.setUserFirstname(dbUser.getFirstname());
            profileResponse.setUserLastname(dbUser.getLastname());
            profileResponse.setRank(userOrg.getRank());
            profileResponse.setDescription(userOrg.getDescription());
            profileResponse.setJobTitle(userOrg.getJobTitle());
            profileResponse.setMessage(Status.OK.getReasonPhrase());
            profileResponse.setDefaultLanguage(userOrg.getDefaultLanguage());

            response = Response.ok(profileResponse).status(Status.OK).build();

        } catch(DataAccessException ex) {
            log.error("DAO exception updating user profile for user {}", user.getUserName(), ex);
            return Response.ok("error. DAO exception updating user profile: " + ex.getMessage()).
                    status(Status.INTERNAL_SERVER_ERROR).build();

        } catch(Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
            return Response.ok(UNHANDLED_EXCEPTION).
                    status(Status.INTERNAL_SERVER_ERROR).build();

        }

        return response;
    }

    // TODO: breaks mobile
    public Response getUserOrgs(int workspaceId, String username, String requestingUser) {
        Response response = null;
        UserOrgResponse userOrgResponse = new UserOrgResponse();

        if(!username.equalsIgnoreCase(requestingUser)) {
            return Response.status(Status.BAD_REQUEST).entity(
                    Status.FORBIDDEN.getReasonPhrase()).build();
        }

        try {
            //int userId = UserDAO.getInstance().getNicsUserByUsername(username).getUserId();
            // TODO: fix int vs. long issue
            long userId = userDao.getMyUserID(username);
            //List<Object[]> userOrgs = OrgDAO.getInstance().getUserOrgs(workspaceId, userId);

            List<Map<String, Object>> userOrgs =
                    this.orgDao.getUserOrgsWithOrgName(Integer.parseInt("" + userId), workspaceId);
            // TODO: remove old hibernate
            userOrgResponse.setUserOrgs(userOrgs);

            //userOrgResponse.setUserOrgs(userOrgs);
            userOrgResponse.setUserId(userId);
            response = Response.ok(userOrgResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            log.error("DAO exception getting UserOrgs for user: {}", username, e);
            return Response.ok("error. DAO exception getting UserOrgs: " + e.getMessage())
                    .status(Status.INTERNAL_SERVER_ERROR).build();
        } catch(Exception e) {
            log.error("Unhandled exception getting UserOrgs for user: {}", username, e);
            return Response.ok("error. Unhandled exception getting UserOrgs: " + e.getMessage())
                    .status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Create User Org, taken from SADisplay
     *
     * @param orgid
     * @param userid
     * @param user
     * @return
     *
     * @throws Exception
     */
    public UserOrg createUserOrg(int orgid, int userid, RegisterUser user) throws Exception {
        UserOrg userorg = new UserOrg();

        UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();

        //ID user_org_workspace needs it, so can't let DAO get it
		/*int userorgid = userOrgDao.getNextUserOrgId();
		if(userorgid == -1) {
			throw new Exception("Could not create user org id");
		}
		userorg.setUserorgid(userorgid);*/
        userorg.setOrgId(orgid);
        userorg.setCreated(Calendar.getInstance().getTime());
        userorg.setSystemroleid(SADisplayConstants.USER_ROLE_ID);
        userorg.setUserId(userid); // userid is unset, depending on caller

        if(user != null) {
            userorg.setJobTitle(user.getJobTitle());
            userorg.setDescription(user.getDescription());
            userorg.setRank(user.getRank());
        }

        return userorg;
    }

    public Response createUserSession(int userId, String displayName, int userorgId,
                                      int systemRoleId, int workspaceId, String sessionId,
                                      String requestingUser) {

        Response response = null;
        UserResponse userResponse = new UserResponse();

        if(userDao.getUserId(requestingUser) != userId) {
            return Response.status(Status.BAD_REQUEST).entity(
                    Status.FORBIDDEN.getReasonPhrase()).build();
        }

        CurrentUserSession session = null;
        try {
            //Check if the user is logging in using the same browser and workspace
            List<CurrentUserSession> sessions = userSessDao.getCurrentUserSession(workspaceId, userId, sessionId);

            //If not,create a new session
            if(sessions == null){
                session = userSessDao.createUserSession(userId, displayName, userorgId, systemRoleId,
                        workspaceId, sessionId, false);
            }else{
                //Results are ordered by logged in - get the most recent
                session = sessions.get(0);
            }

            if(session != null) {
                userResponse.setCount(1);
                userResponse.setMessage(Status.OK.getReasonPhrase());
                userResponse.setUserSession(session);
                response = Response.ok(userResponse).status(Status.OK).build();

                try {
                    User user = userDao.getUserWithSession(userId);
                    notifyLogin(workspaceId, user);
                } catch(IOException e) {
                    log.error("Failed to publish Login notification message event for userId {}", userId, e);
                } catch(AlreadyClosedException e) {
                    log.error("Exception RabbitMQ attempting to use a closed channel ", e);
                } catch(Exception e) {
                    log.error("Exception RabbitMQ exception occurred.", e);
                }
            }

            if(session == null) {
                log.warn("The usersession was not created for {}", displayName);
                userResponse.setCount(1);
                userResponse.setMessage(Status.NOT_FOUND.getReasonPhrase());
                response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            log.error("Exception creating Current/UserSession for user: {} (userId: {})", displayName, userId, e);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    public Response removeUserSession(int userSessionId, String username, int workspaceId) {
        Response response = null;
        UserResponse userResponse = new UserResponse();

        try {
            String sessionId = userSessDao.getSessionId(userSessionId);

            int userId = (new Long(userDao.getUserId(username)).intValue());

            List<CurrentUserSession> sessions = userSessDao.getCurrentUserSession(
                    workspaceId, userId, sessionId);

            List<String> currentSessionIds = new ArrayList<String>();

            if(sessions!=null){
                for (CurrentUserSession session : sessions) {
                    currentSessionIds.add(Long.toString(
                            session.getCurrentusersessionid()));
                }
            }

            int count = userSessDao.removeUserSession(sessionId);
            if(count != 0) {
                userResponse.setCount(count);
                userResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(userResponse).status(Status.OK).build();
            }

            notifyLogout(sessionId);

            //Notify active user panels of user logout
            notifyLogout(currentSessionIds, workspaceId, sessionId);

        } catch(Exception e) {
            log.error("Exception removing UserSession for userSessionId {}", userSessionId, e);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }


        return response;

    }

    public Response getContactTypes() {
        UserResponse userResponse = new UserResponse();
        Status status;

        try {
            userResponse.setContactTypes(userDao.getContactTypes());
            userResponse.setMessage("Successfully retrieved ContactTypes");
            status = Status.OK;
        } catch(Exception e) {
            userResponse.setMessage("Error retrieving ContactTypes");
            status = Status.INTERNAL_SERVER_ERROR;
        }

        return Response.ok(userResponse).status(status).build();
    }

    public Response getUsersContactInfo(int workspaceId, String userName) {

        Response response = null;
        UserResponse userResponse = new UserResponse();
        List<Contact> contacts = null;
        User user = null;

        try {
            contacts = userDao.getAllUserContacts(userName);

            if(contacts != null) {
                user = new User();
                user.setContacts(new HashSet<Contact>(contacts));
                userResponse.getUsers().add(user);
                userResponse.setMessage(Status.OK.getReasonPhrase());
                userResponse.setCount(contacts.size());
                response = Response.ok(userResponse).status(Status.OK).build();
            } else {
                userResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
                userResponse.setCount(0);
                response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }


        } catch(Exception e) {
            log.error("Exception getting Contacts for user: {}", userName, e);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }


        return response;
    }

    public Response addContactInfo(int workspaceId, String userName, int contactTypeId, String value,
                                   String requestingUser) {

        Response response = null;
        UserResponse userResponse = new UserResponse();
        User user = null;
        Contact contact = null;
        HashSet<Contact> hashSet = null;

        if(!userName.equalsIgnoreCase(requestingUser)) {
            return Response.status(Status.BAD_REQUEST).entity(
                    Status.FORBIDDEN.getReasonPhrase()).build();
        }

        int userId = new Long(userDao.getUserId(userName)).intValue();

        try {

            Contact newContact = this.createContact(contactTypeId, value, userId);
            int dbContactId = userDao.createContact(newContact);

            if(dbContactId != -1) {
                newContact.setContactid(dbContactId);

                user = new User();
                hashSet = new HashSet<Contact>();

                hashSet.add(newContact);
                user.setContacts(hashSet);

                userResponse.getUsers().add(user);
                userResponse.setMessage(Status.OK.getReasonPhrase());
                userResponse.setCount(1);
                response = Response.ok(userResponse).status(Status.OK).build();
            } else {
                userResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
                userResponse.setCount(0);
                response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            log.error("Exception updating Contacts for user {}", userName, e);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }


        return response;
    }

    public Response deleteContactInfo(int workspaceId, String userName, int contactId, String requestingUser) {

        Response response = null;
        UserResponse userResponse = new UserResponse();

        if(!userName.equalsIgnoreCase(requestingUser)) {
            return Response.status(Status.BAD_REQUEST).entity(
                    Status.FORBIDDEN.getReasonPhrase()).build();
        }

        try {

            boolean deleted = userDao.deleteContact(contactId);

            if(deleted) {
                userResponse.setMessage(Status.OK.getReasonPhrase());
                userResponse.setCount(1);
                response = Response.ok(userResponse).status(Status.OK).build();
            } else {
                userResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
                userResponse.setCount(0);
                response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }

        } catch(Exception e) {
            log.error("Exception deleting Contacts for user {}", userName, e);
            response = Response.ok(userResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    public Response postLogin(Login login, String sessionId) {
        Response response = null;

        LoginResponse loginResponse = new LoginResponse();

        String username = login.getUsername().toLowerCase();

        int wsId = login.getWorkspaceId();

        Login newLogin = new Login();

        if(username != null && !username.isEmpty()) {
            try {
                newLogin.setUsername(username);

                User u = userDao.getUser(username);
                if(u == null) {
                    loginResponse.setMessage("Could not find username");
                    response = Response.ok(loginResponse).status(Status.OK).build();
                    return response;
                }

                log.debug("Login found user: {} ({})",
                        u.getUsername(), u.getUserId());

                int userId = u.getUserId();
                if(userId <= 0) {
                    loginResponse.setMessage("Invalid user id for username");
                    response = Response.ok(loginResponse).status(Status.OK).build();
                    return response;
                }

                /***********TEMPORARY***************************/
                int orgid = -1;
                List<Org> orgs = orgDao.getUserOrgs(userId, wsId);
                if(orgs != null && !orgs.isEmpty()) {
                    // TODO: Needs updated to use the default org, but the rest of the system
                    //		 needs to adopt the default field usage first
                    orgid = orgs.get(0).getOrgId();
                }

                UserOrg userOrg = null;
                if(orgid != -1) {
                    userOrg = userOrgDao.getUserOrgById(orgid, userId, wsId);
                }
                /***********************************************/

                //FUTURE
                //UserOrg userOrg = userOrgDao.getUserOrgById(login.getCurrentOrgId(), userId, wsId);

                if(userOrg != null) {
                    String displayname = u.getFirstname() + " " + u.getLastname();

                    int userSessionId;

                    //Check for an existing session
                    List<CurrentUserSession> sessions =
                            userSessDao.getCurrentUserSession(wsId, userId, sessionId);
                    if(sessions != null && sessions.size() > 0){
                        CurrentUserSession session = sessions.get(0);
                        userSessionId = session.getUsersessionid();
                    }else {
                        // create() creates both current and user sessions
                        userSessionId = userSessDao.create(sessionId, userOrg.getUserorgid(),
                                displayname, userId, userOrg.getSystemroleid(), wsId, true);
                    }

                    newLogin.setUserId(userId);
                    newLogin.setWorkspaceId(wsId);
                    newLogin.setUserSessionId(userSessionId);
                    newLogin.setSessionId(sessionId);

                    if(newLogin.getUserSessionId() <= 0) {
                        loginResponse.setMessage("Login failed for user");
                        response = Response.ok(loginResponse).status(Status.NO_CONTENT).build();
                        return response;
                    } else {
                        loginResponse.getLogins().add(newLogin);
                        loginResponse.setMessage(Status.OK.getReasonPhrase());
                        loginResponse.setCount(1);
                        response = Response.ok(loginResponse).status(Status.OK).build();

                        User user = userDao.getUserWithSession(userId);
                        notifyLogin(wsId, user);
                    }
                }else{
                    loginResponse.setMessage("Could not retrieve User Org");
                    response = Response.ok(loginResponse).status(Status.PRECONDITION_FAILED).build();
                }
            } catch(Exception e) {
                e.printStackTrace();
                loginResponse.setMessage("Unhandled exception logging in");
                response = Response.ok(loginResponse).status(Status.PRECONDITION_FAILED).build();
            }
        }

        return response;
    }

    private void setKeycloakEnabled(String username, boolean enabled, int orgCount) throws Exception {
        //If enabled and org count is one it is the first time the user is enabled
        //If disabled and org count is zero the user is no longer active in the system
        if((enabled && orgCount == 1) ||
                (!enabled && orgCount == 0)) {

            ResteasyClient client = new ResteasyClientBuilder().build();

            try {
                Configuration config = APIConfig.getInstance().getConfiguration();

                Form form = new Form()
                        .param(CLIENT_ID, config.getString(APIConfig.KEYCLOAK_CLIENT_ID))
                        .param(USERNAME, config.getString(APIConfig.KEYCLOAK_USERNAME))
                        .param(PASSWORD, config.getString(APIConfig.KEYCLOAK_PASSWORD))
                        .param(GRANT_TYPE, PASSWORD);

                Response token_response = client.target(config.getString(APIConfig.KEYCLOAK_TOKEN_ENDPOINT))
                        .request().post(Entity.form(form));

                String accessToken = new JSONObject(token_response.readEntity(String.class))
                        .getString(ACCESS_TOKEN);

                if(accessToken != null) {
                    String authorizationHeader = String.format("%s %s", BEARER, accessToken);

                    //Request the user representation from keycloak
                    Response user_response = client.target(config.getString(APIConfig.KEYCLOAK_USER_ADMIN_ENDPOINT))
                            .queryParam(SADisplayConstants.USER_NAME, username)
                            .request().header(HttpHeaders.AUTHORIZATION, authorizationHeader).get();

                    //Response is an array of users
                    JSONArray users = new JSONArray(user_response.readEntity(String.class));

                    if(users.length() == 1) {
                        JSONObject keycloakUser = users.getJSONObject(0);
                        String id = (String) keycloakUser.get(SADisplayConstants.ID);

                        if(id != null) {
                            //Update user enabled
                            keycloakUser.put(SADisplayConstants.ENABLED, enabled);

                            client.target(
                                    String.format("%s/%s", config.getString(APIConfig.KEYCLOAK_USER_ADMIN_ENDPOINT),
                                            id))
                                    .request().header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                                    .put(Entity.entity(keycloakUser.toString(), MediaType.APPLICATION_JSON));
                        }
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
                throw e;
            } finally {
                client.close();
            }
        }
    }

    private void notifyLogin(int workspaceId, User user)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(user != null) {
            String topic = String.format("iweb.NICS.%d.login", workspaceId);
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(user);
            getRabbitProducer().produce(topic, message);
        }
    }

    private Response makeIllegalOpRequestResponse() {
        UserResponse mdtrackResponse = new UserResponse();
        mdtrackResponse.setMessage("Request ignored.");
        Response response = Response.notModified("Illegal operation requested").
                status(Status.FORBIDDEN).build();
        return response;
    }

    private Response makeUnsupportedOpRequestResponse() {
        UserResponse mdtrackResponse = new UserResponse();
        mdtrackResponse.setMessage("Request ignored.");
        Response response = Response.notModified("Unsupported operation requested").
                status(Status.NOT_IMPLEMENTED).build();
        return response;
    }

    /**
     * Utility method for creating NICS User from an API RegisterUser
     *
     * @param rUser a valid RegisterUser object
     * @return a valid NICS User object if successful, null otherwise
     *
     * @throws Exception
     */
    private User createUser(RegisterUser rUser) throws Exception {
        User user = new User();

        //int userid = userDao.getNextUserId();
        user.setFirstname(rUser.getFirstName());
        user.setLastname(rUser.getLastName());
        user.setUsername(rUser.getEmail());
        //user.setUserId(userid);
        user.setActive(true);

        // TODO: go ahead and persist this user, getting the userid, set it, and
        // 	return the entity, except then it won't be part of the transaction....hmm, think about it

        return user;
    }

    /**
     * Validates a RegisterUser object
     *
     * @param user
     * @return Failure reason constant
     */
    private String validateRegisterUser(RegisterUser user) {

        if(StringUtils.isBlank(user.getOrganization())) {
            return FAILURE_ORG_BLANK;
        } else {

            if(!validateStringAsInt(user.getOrganization())) {
                log.warn("Couldn't parse integer from given orgid: {}", user.getOrganization());
                return FAILURE_ORG_INVALID + " - Received invalid Organization ID: " + user.getOrganization();
            }
        }

        if(StringUtils.isBlank(user.getFirstName()) || StringUtils.isBlank(user.getLastName())) {
            return FAILURE_NAMES;
        }

        if(StringUtils.isBlank(user.getEmail()) ||
                !EntityEncoder.validateEmailAddress(user.getEmail())) {
            return FAILURE_EMAIL;
        }

        if((StringUtils.isNotBlank(user.getOtherEmail()) &&
                !EntityEncoder.validateEmailAddress(user.getOtherEmail()))) {
            return FAILURE_OTHER_EMAIL;
        }

        if(!UserInfoValidator.validateUsername(user.getEmail()) ||
                !EntityEncoder.validateEmailAddress(user.getEmail())) {
            return FAILURE_USERNAME_INVALID;
        }

        try {
            if(userDao.getUser(user.getEmail()) != null) {
                return FAILURE_USERNAME;
            }
        } catch(DataAccessException e) {
            log.error("DAO exception attempting to retrieve user with email: {}", user.getEmail(), e);
            return FAILURE_USERNAME;
        }


        if(!UserInfoValidator.validatePhoneNumbers(
                user.getCellPhone(), user.getOtherPhone(), user.getOfficePhone())) {
            return FAILURE_PHONE_NUMBERS + String.format(": %s, %s, %s", user.getCellPhone(), user.getOtherPhone(),
                    user.getOfficePhone());
        }
		
		
		/* Also need to validate these
		user.getDescription();
		user.getJobTitle();
		user.getOrganization();
		user.getOtherInfo();
		user.getRadioNumber();
		user.getRank();
		user.getTeams();
		*/


        if(StringUtils.isNotBlank(user.getDescription())
                && !EntityEncoder.validateInputValue(user.getDescription())) {
            return "Invalid input in Job Description field. " + SAFECHARS;
        }

        if(StringUtils.isNotBlank(user.getJobTitle())
                && !EntityEncoder.validateInputValue(user.getJobTitle())) {
            return "Invalid input in Job Title field. " + SAFECHARS;
        }

        if(StringUtils.isNotBlank(user.getOtherInfo())
                && !EntityEncoder.validateInputValue(user.getOtherInfo())) {
            return "Invalid input in Other Info field. " + SAFECHARS;
        }

        if(StringUtils.isNotBlank(user.getRadioNumber())
                && !EntityEncoder.validateInputValue(user.getRadioNumber())) {
            return "Invalid input in Radio Number field. " + SAFECHARS;
        }

        if(StringUtils.isNotBlank(user.getRank())
                && !EntityEncoder.validateInputValue(user.getRank())) {
            return "Invalid input in Rank field. " + SAFECHARS;
        }

        return SUCCESS;
    }


    /**
     * Utility method for checking that a String successfully can be parsed as an integer
     *
     * @param input String expected to contain a value representing an integer
     * @return true if it can, false if it cannot
     */
    private boolean validateStringAsInt(String input) {
        boolean valid = false;

        if(StringUtils.isNotBlank(input)) {
            try {
                int value = Integer.parseInt(input, 10);
                valid = true;
            } catch(NumberFormatException e) {

            }
        }

        return valid;
    }

    private List<Integer> getWorkspaceIds() {
        WorkspaceDAOImpl workspaceDao = new WorkspaceDAOImpl();
        List<Integer> workspaceIds = workspaceDao.getWorkspaceIds();
        log.debug("got workspaceids: {}", Arrays.toString(workspaceIds.toArray()));
        return workspaceIds;
    }

    private void notifyLogout(String sessionId)
            throws IOException, TimeoutException, AlreadyClosedException {
        String topic = String.format("iweb.NICS.%s.logout", sessionId);
        getRabbitProducer().produce(topic, sessionId);
    }

    //Remove from Active Users table
    private void notifyLogout(List<String> currentSessions, int workspaceId, String sessionId)
            throws IOException, TimeoutException, AlreadyClosedException {

        String topic = String.format("iweb.NICS.%d.logout", workspaceId);

        if(currentSessions != null){
            for(String currentSessionId : currentSessions){
                getRabbitProducer().produce(topic,currentSessionId);
            }
        }
    }

    private void notifyNewUserEmail(String email, String topic)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(email != null) {
            getRabbitProducer().produce(topic, email);
        }
    }

    /**
     * Send an object containing userid, orgid, userorgid, userorg_workspaceid to the UI so that an admin viewing users
     * to enable/disable will see newly registered users show up that weren't there upon the first loading of users.
     *
     * @param workspaceId
     * @param user        map object retrieved from {@link UserOrgDAOImpl#getDisabledUserOrg(int, int, int)}
     * @throws IOException when there's an issue publishing message to rabbit bus
     */
    private void notifyNewUserRegistered(int workspaceId, Map<String, Object> user)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(user != null) {
            // TODO: topic should be configurable
            String topic = String.format("iweb.NICS.%d.admin.org.%d.users.new",
                    workspaceId, user.get("orgid"));

            log.debug("\n\nSENDING user to topic {}:\n{}", topic, user.get("username"));

            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(user);
            getRabbitProducer().produce(topic, message);
        }
    }

    private RabbitPubSubProducer getRabbitProducer()
            throws IOException, TimeoutException, AlreadyClosedException {
        if(rabbitProducer == null) {
            rabbitProducer = RabbitFactory.makeRabbitPubSubProducer(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
        }
        return rabbitProducer;
    }
}
