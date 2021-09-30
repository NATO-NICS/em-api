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

import edu.mit.ll.em.api.rs.LanguageTranslationService;
import edu.mit.ll.em.api.rs.LanguageTranslationResponse;
import edu.mit.ll.em.api.rs.LanguageTranslationRequest;
import edu.mit.ll.em.api.rs.LanguageTranslation;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.FileUtil;
import edu.mit.ll.em.api.exception.BadConfigException;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.UserOrg;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.lang.StringBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Language Translation Service Implementation.  Provides CRUD operations nn Language Translations.
 */
public class LanguageTranslationServiceImpl implements LanguageTranslationService {
    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LanguageTranslationServiceImpl.class);

    /**
     * UserOrg DAO Instance for verifying user roles.
     */
    private static final UserOrgDAOImpl userorgDao = new UserOrgDAOImpl();

    /**
     * The root language translation path from the em-api configuration.
     */
    private static String rootTranslationPath = null;

    /**
     * Language Translation map using the Language code as hashmap key.
     */
    private static ConcurrentHashMap<String, LanguageTranslation> translationMap = new ConcurrentHashMap<>();

    /**
     * Reference holder to the default Language code.
     */
    private static String default_code = null;

    /**
     * Flag for valid configuration in em-api.properties.
     */
    private static boolean invalidConfig = false;

    /**
     * Initialization method for this Service.
     */
    public void init() {
        loadTranslations();
    }

    /**
     * Method loads a specified translation file from disk and adds to
     * Language translation map.
     *
     * @param translationFile Language translation file
     */
    public void loadTranslation(File translationFile) {
        try {
            LOG.debug(MESSAGE_LOADING_TRANSLATION, translationFile.getAbsolutePath());
            if (translationFile.exists()) {
                JSONObject data = FileUtil.getInstance().parseJSONFromFile(translationFile.getAbsolutePath());
                final String code = data.getString(LanguageTranslation.KEY_CODE);
                final String language = data.getString(LanguageTranslation.KEY_LANGUAGE);
                final boolean isDefault = data.getBoolean(LanguageTranslation.KEY_DEFAULT);
                final String selectOrgText = data.getString(LanguageTranslation.KEY_SELECTORG);
                final JSONObject translations = data.getJSONObject(LanguageTranslation.KEY_TRANSLATIONS);

                final LanguageTranslation lt = new LanguageTranslation(code, language, isDefault,
                        selectOrgText, translations);
                addLanguageTranslationToMap(code, lt);

                if (default_code == null && isDefault) {
                    default_code = code;
                }
            }
        } catch (JSONException je) {
            LOG.error(MESSAGE_ERROR_TRANSLATION_JSON, translationFile.getAbsolutePath(), je);
        } catch (IOException ie) {
            LOG.error(MESSAGE_ERROR_TRANSLATION_IO, translationFile.getAbsolutePath(), ie);
        }
    }

    /**
     * Method that loads all Language translations. Loads all Language translation files
     * from configured directory in em-api.properties.  The configured directory
     * expects 1 JSON file per Language translation.  Filename pattern is "[code].json".
     */
    public void loadTranslations() {
        // load translations root path
        if (rootTranslationPath == null) {
            rootTranslationPath = APIConfig.getInstance().getConfiguration()
                    .getString(APIConfig.TRANSLATION_PATH, null);
        }

        if (rootTranslationPath == null) {
            invalidConfig = true;
        } else {
            File translationDir = new File(rootTranslationPath);
            for (final File translationEntry : translationDir.listFiles()) {
                if (translationEntry.getName().endsWith(".json")) {
                    loadTranslation(translationEntry);
                }
            }
        }
    }

    public Response getAllLanguageTranslations() {
        Response response;
        LanguageTranslationResponse translationResponse = new LanguageTranslationResponse();

        if (invalidConfig) {
            translationResponse.setMessage(MESSAGE_INVALID_CONFIG);
            return Response.ok(translationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        translationResponse.setMessage(MESSAGE_SUCCESS);
        translationResponse.setTranslations(translationMap.values());
        return Response.ok(translationResponse).status(Status.OK).build();
    }

    public Response getLanguageTranslationCodes() {
        Response response;
        LanguageTranslationResponse translationResponse = new LanguageTranslationResponse();
        ArrayList<LanguageTranslation> codes = new ArrayList<>();

        if (invalidConfig) {
            translationResponse.setMessage(MESSAGE_INVALID_CONFIG);
            return Response.ok(translationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        for (Map.Entry<String, LanguageTranslation> entry: translationMap.entrySet()) {
            final LanguageTranslation val = entry.getValue();
            final LanguageTranslation code = new LanguageTranslation(val.getCode(), val.getLanguage(), false,
                    val.getSelectOrgText(), new JSONObject());
            codes.add(code);
        }

        translationResponse.setMessage(MESSAGE_SUCCESS);
        translationResponse.setTranslations(codes);
        return Response.ok(translationResponse).status(Status.OK).build();
    }

    public Response getLanguageTranslationByCode(String code) {
        Response response;
        LanguageTranslationResponse translationResponse = new LanguageTranslationResponse();

        if (invalidConfig) {
            translationResponse.setMessage(MESSAGE_INVALID_CONFIG);
            return Response.ok(translationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if (translationMap.containsKey(code)) {
            translationResponse.setMessage(MESSAGE_SUCCESS);
            translationResponse.setTranslations(Arrays.asList(translationMap.get(code)));
            return Response.ok(translationResponse).status(Status.OK).build();
        } else {
            translationResponse.setMessage(new StringBuilder().append(MESSAGE_BAD_LANGUAGE_CODE)
                .append(code).toString());
            return Response.ok(translationResponse).status(Status.NOT_FOUND).build();
        }
    }

    public Response updateTranslation(String code, LanguageTranslationRequest translation, String requestingUser) {
        Response response;
        LanguageTranslationResponse translationResponse = new LanguageTranslationResponse();

        if (invalidConfig) {
            translationResponse.setMessage(MESSAGE_INVALID_CONFIG);
            return Response.ok(translationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if (isNotAuthorized(requestingUser)) {
            translationResponse.setMessage(MESSAGE_NOT_AUTHORIZED_MODIFY);
            return Response.ok(translationResponse).status(Status.FORBIDDEN).build();
        }

        if (!translationMap.containsKey(code)) {
            translationResponse.setMessage(new StringBuilder().append(MESSAGE_BAD_LANGUAGE_CODE)
                    .append(code).toString());
            return Response.ok(translationResponse).status(Status.NOT_FOUND).build();
        }

        if (translation == null || (translation.getKey() == null || translation.getKey().isEmpty()) ||
                (translation.getValue() == null || translation.getValue().isEmpty())) {
            translationResponse.setMessage(MESSAGE_MISSING_FIELDS);
            return Response.ok(translationResponse).status(Status.BAD_REQUEST).build();
        }

        final String key = translation.getKey();
        final String value = translation.getValue();
        try {
            updateTranslationMap(code, key, value);
        } catch (IOException ioe) {
            translationResponse.setMessage(MESSAGE_FAIL_LANGUAGE_WRITE);
            return Response.ok(translationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        translationResponse.setMessage(MESSAGE_SUCCESS);
        return Response.ok(translationResponse).status(Status.OK).build();
    }

    public Response createLanguage(LanguageTranslationRequest translation, String requestingUser) {
        Response response;
        LanguageTranslationResponse translationResponse = new LanguageTranslationResponse();

        if (invalidConfig) {
            translationResponse.setMessage(MESSAGE_INVALID_CONFIG);
            return Response.ok(translationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if (isNotAuthorized(requestingUser)) {
            translationResponse.setMessage(MESSAGE_NOT_AUTHORIZED_CREATE);
            return Response.ok(translationResponse).status(Status.FORBIDDEN).build();
        }

        if (translation == null || (translation.getCode() == null || translation.getCode().isEmpty()) ||
                (translation.getLanguage() == null || translation.getLanguage().isEmpty()) ||
                (translation.getSelectOrgText() == null || translation.getSelectOrgText().isEmpty())) {
            translationResponse.setMessage(MESSAGE_MISSING_FIELDS);
            return Response.ok(translationResponse).status(Status.BAD_REQUEST).build();
        }

        final String code = translation.getCode().toLowerCase();
        final String language = translation.getLanguage();
        final String selectOrgText = translation.getSelectOrgText();
        final LanguageTranslation lt = new LanguageTranslation(code, language, false,
                selectOrgText, generateDefaultTranslationMap());
        try {
            saveTranslation(lt);

            translationResponse.setMessage(MESSAGE_SUCCESS);
            translationResponse.setTranslations(Arrays.asList(lt));
            return Response.ok(translationResponse).status(Status.CREATED).build();
        } catch (IOException ioe) {
            translationResponse.setMessage(MESSAGE_FAIL_LANGUAGE_WRITE);
            return Response.ok(translationResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Utility method to handle adding LanguageTranslation to the translation map.
     */
    private void addLanguageTranslationToMap(final String code, final LanguageTranslation lt) {
        translationMap.put(code, lt);
    }

    /**
     * Utility method to save updated LanguageTranslation objects to the filesystem.
     * @param lt {@link LanguageTranslation}
     */
    private void saveTranslation(final LanguageTranslation lt) throws IOException {
            final String outPath = new StringBuilder().append(rootTranslationPath)
                    .append(File.separator)
                    .append(lt.getCode().toLowerCase())
                    .append(".json").toString();
            final FileWriter writer = new FileWriter(outPath);
            writer.write(lt.toFileFormat().toString());
            writer.flush();
            writer.close();
    }

    /**
     * Utility method to handle accessing and mutation of the translation map.
     */
    private void updateTranslationMap(final String code, final String key, final String value) throws IOException {
        final LanguageTranslation lt = translationMap.get(code);
        lt.updateTranslation(key, value);
        translationMap.put(code, lt);

        saveTranslation(lt);
    }

    /**
     * Utility method to check if user is an admin or super user.
     *
     * @param username username of the user to check for authorization
     *
     * @return true if the user is NOT authorized, false otherwise
     */
    private boolean isNotAuthorized(final String username) {
        return !userorgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID) &&
                !userorgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID);
    }

    /**
     * Utility method to generate a default Language translation map.
     */
    private JSONObject generateDefaultTranslationMap() {
        final JSONObject json = new JSONObject();
        try {
            for (String entry : DEFAULT_TERMS) {
                json.put(entry, "");
            }
        } catch (JSONException je) {
            LOG.error(MESSAGE_EXCEPTION_DEFAULT_MAP, je);
        }
        return json;
    }

    /**
     * List of the default Translation terms.
     */
    private static final String[] DEFAULT_TERMS = new String[] {
            "4. San Francisco Bay Bridge",
            "A DCDS Welcome Packet has been e-mailed to the newly enabled user(s).",
            "A first and last name is required.",
            "Academia",
            "Access Denied",
            "Account Information",
            "Acknowledge Date",
            "Acknowledged",
            "acres",
            "Active Incident",
            "Active Users",
            "Add",
            "Add Data Source",
            "Add Photos",
            "Add Users",
            "Address / Placemark",
            "Address Geocode Error",
            "admin",
            "Admin Users",
            "Administration",
            "Aerial Hazard",
            "Aerial Ignition",
            "After",
            "After Shock",
            "Agricultural Laboratories S1",
            "Agricultural Laboratories S2",
            "Agricultural Laboratories S3",
            "Agricultural Laboratories S4",
            "Air",
            "Air Accident",
            "Air Hijacking",
            "Air Incident",
            "Aircraft Accident",
            "AirOperationsBranchDir",
            "AirSupportGroupSup",
            "AirTacticalGroupSup",
            "Align Text Left",
            "Align Text Right",
            "Align text to the left.",
            "Align text to the right.",
            "All",
            "All permissions were successfully updated.",
            "All Users",
            "Allow others to move this map",
            "Ambulance",
            "An email address is required.",
            "An error occurred while attempting to delete an announcement.",
            "An error occurred while attempting to post a new announcement.",
            "An error occurred while attempting to retrieve announcements.",
            "An error occurred while persisting report.",
            "An error occurred while registering your account.",
            "An unexpected error occurred while attempting to update the folder.",
            "and",
            "Animal Feedlots S1",
            "Animal Feedlots S2",
            "Animal Feedlots S3",
            "Animal Feedlots S4",
            "Animal Issue:  Issue including aggression location assistance needed etc.",
            "Announcement",
            "Announcement Error",
            "Announcements",
            "Anonymous",
            "Apply",
            "April",
            "Archived Incident Lookup",
            "Archived Incidents",
            "Are you sure you want delete this datalayer?",
            "Area",
            "Assign...",
            "Assigned",
            "Assigned To",
            "ATF",
            "August",
            "Authentication Error",
            "Avalanche",
            "Before",
            "Bird Infestation",
            "Blank Incident",
            "Blizzard",
            "Bn Medical Section",
            "Bold (Ctrl+B)",
            "Bomb",
            "Bomb Explosion",
            "Bomb Threat",
            "Border Patrol",
            "Branch Break",
            "BranchDir1",
            "BranchDir2",
            "Browse",
            "Browser",
            "Bullet List",
            "CA-FIRESCOPE - Not Applicable",
            "CA-FIRESCOPE - Not Threatened",
            "CA-FIRESCOPE - Threated Defensible",
            "CA-FIRESCOPE - Threated Non-Defensible",
            "Camp",
            "Cancel",
            "Category",
            "CBRN Contamination",
            "CbrnContamination",
            "CDF IMT",
            "Cell Phone",
            "Census",
            "Census Application",
            "Center Text",
            "Center text in the editor.",
            "Change Organization",
            "Change the background color of the selected text.",
            "Change the color of the selected text.",
            "Chat",
            "Chemical Agents",
            "Choose a",
            "Choose a Collaboration Room to use the Whiteboard",
            "Choose a dbf file",
            "Choose a month (Control+Up/Down to move years)",
            "Choose a prj file",
            "Choose a shape file to load as a data layer",
            "Choose a shx file",
            "Choose a sld file",
            "Civil Affairs",
            "Civil Demonstrations",
            "Civil Displaced Population",
            "Civil Disturbance",
            "Civil Rioting",
            "Civil Unrest",
            "Class I",
            "Class I Circle",
            "Class II",
            "Class II Circle",
            "Class III",
            "Class III Circle",
            "Class IV",
            "Class IV Circle",
            "Class IX",
            "Class IX Circle",
            "Class V",
            "Class V Circle",
            "Class VI",
            "Class VI Circle",
            "Class VII",
            "Class VII Circle",
            "Class VIII",
            "Class VIII Circle",
            "Class X",
            "Class X Circle",
            "Clear",
            "Clear Selection",
            "Click here to ",
            "Click here to return to login page",
            "Close panel",
            "Collaboration Room Error",
            "Collapse Panel",
            "Color",
            "Columns",
            "COMBAT ARMS - Aviation",
            "COMBAT ARMS - Engineer",
            "COMBAT ARMS - Field Artillery",
            "COMBAT ARMS - Infantry",
            "COMBAT ARMS - Special Forces",
            "COMBAT SUPPORT - Aviation",
            "COMBAT SUPPORT - Chemical",
            "COMBAT SUPPORT - Military Intelligence",
            "COMBAT SUPPORT - Military Police",
            "COMBAT SUPPORT - Signal",
            "Command Staff",
            "Comments",
            "Communications",
            "CommunicationsUnitLeader",
            "CompensationAndClaimsUnitLeader",
            "Completed Dozer Line",
            "Completed Fire Line",
            "Condition",
            "Configuration Error",
            "Confirm Password",
            "Confirmed Victim:  Confirmed live survivor (visual audible physical confirmation)",
            "Contact Info",
            "Contact Type",
            "Contains",
            "Copy",
            "Copy Drawings from Workspace to current collaboration room",
            "Corporate",
            "Corrosive Material",
            "CostUnitLeader",
            "Could not find username",
            "Country",
            "County",
            "Create",
            "Create a census region by clicking the mouse to create each point in the polygon.",
            "Create a new report",
            "Create a weather region by clicking the mouse to create each point in the polygon.",
            "Create Display Name in DCDS",
            "Create Explosives Report",
            "Create Incident",
            "Create New Incident",
            "Create New Room",
            "Create Room",
            "Created",
            "Criminal Activity",
            "Criminal Activity Incident",
            "CST",
            "CSV",
            "Customs Service",
            "Cyclone",
            "Damage",
            "Dangerous When Wet",
            "Data",
            "Data Access Error",
            "Data access exception attempting to log User out",
            "Data access exception retrieving Incident.",
            "Data access failure. Unable to read all incidents.",
            "Data Layer",
            "Data Source",
            "Data Sources",
            "Date",
            "Date Submitted",
            "Days",
            "DCDS Private Chat Message",
            "DEA",
            "December",
            "Decimal Degrees",
            "Decimal Degrees - DDD.DDDDD&deg;",
            "Decrease the font size.",
            "Default Language",
            "Default Latitude",
            "Default Longitude",
            "Degrees Decimal Minutes",
            "Degrees Minutes Seconds",
            "Delete",
            "Delete Datalayer?",
            "Delete Folder?",
            "Delete Layer",
            "Delete selected users",
            "Delete Selection",
            "Deleting this folder will delete all child folders and datalayers. Do you want to continue?",
            "Description",
            "Details",
            "Device Types",
            "Disable All Views",
            "Disable workspace sharing",
            "Disabled",
            "Display Name",
            "Displaying",
            "Distance",
            "Distribution List",
            "Division Break",
            "DivisionSupA",
            "DivisionSupB",
            "DivisionSupC",
            "DivisionSupD",
            "DivisionSupX",
            "DivisionSupY",
            "DivisionSupZ",
            "Do you want to remove the",
            "DOJ",
            "Double click when the polygon is complete.",
            "Drag and drop a username to allow specific permissions in this room.",
            "Drag and drop a username to archive/activate an incident.",
            "Drag and drop a username to enable/disable.",
            "Dragging",
            "Draw",
            "Draw Circle",
            "Draw Dashed Line",
            "Draw Hexagon",
            "Draw Label",
            "Draw Markers",
            "Draw Medium Line",
            "Draw Square",
            "Draw Thick Line",
            "Draw Thin Line",
            "Draw Triangle",
            "Draw Vector Polygon",
            "Drawing Tools",
            "Drizzle",
            "Drop on a private chat window to invite participants.",
            "Drop Point",
            "Dropped",
            "Drought",
            "Earth Quake Epicenter",
            "Earthquake",
            "Edit",
            "Edit Comments",
            "Edit Selected Incident",
            "Email",
            "Email Feedback Report",
            "Emergency Collection Evacuation Point",
            "Emergency Food Distribution Center",
            "Emergency Incident Command Center",
            "Emergency medical Operation",
            "Emergency Operation",
            "Emergency Operations Center",
            "Emergency Public Information Center",
            "Emergency Shelters",
            "Emergency Staging Area",
            "Emergency teams",
            "Emergency Water Distribution Center",
            "Empty Text",
            "EMT Station Location",
            "Enable All Views",
            "Enable workspace sharing",
            "Enabled Users",
            "Engineering",
            "Enter Filter Text...",
            "Enter label text",
            "Enter Number...",
            "Enter text here to filter PLI",
            "EOD",
            "EquipmentTimeRecorder",
            "Erase",
            "Error logging out",
            "Error processing Identity",
            "Error reading token. Cookie value was null/empty",
            "Error receiving cookie. Cookie was null",
            "Error updating incident.",
            "Error updating user profile.",
            "Errors",
            "ESRI Shape File Import",
            "Evacuated:  Survivors transported to collection point",
            "Exact",
            "Example",
            "Examples",
            "Exception getting form type id.",
            "Expectation Failed",
            "Explosive",
            "Explosives",
            "Export Current Room",
            "Export Format ",
            "Export Get Capabilities",
            "Export Room",
            "Export View",
            "Extra 21:  Mission specific placeholder to be determined (e.g. abandoned vehicle commercial structure evidence)",
            "Extra 22:  Mission specific placeholder to be determined",
            "Extra 23:  Mission specific placeholder to be determined",
            "Extra 24:  Mission specific placeholder to be determined",
            "FacilitiesUnitLeader",
            "Failed",
            "Failed retrieving workspaces for this instance.",
            "Failed to create MDTrack",
            "Failed to delete data layer.",
            "Failed to insert data layer.",
            "Failed to insert data source.",
            "Failed to parse capabilities",
            "Failed to publish a collaboration room Feature Change message event.",
            "Failed to publish a Delete CollabRoom Feature message event.",
            "Failed to read reports.",
            "Failed to retrieve data layers.",
            "Failed to retrieve data sources.",
            "Failed to retrieve service capabilities",
            "Failed to update data layer.",
            "Failed to upload file.",
            "Failed to upload your file.",
            "Failed to upload your files.",
            "FBI",
            "Feature Details",
            "features were copied to the current workspace",
            "February",
            "Federal",
            "Federal IMT",
            "Feedback message content not valid",
            "Feedback Report from",
            "FieldObserver",
            "File Import",
            "file to load as a data layer",
            "File uploaded successfully.",
            "Files uploaded successfully.",
            "Finance",
            "FinanceAdminSectionChief",
            "Financial Exchanges S1",
            "Financial Exchanges S2",
            "Financial Exchanges S3",
            "Financial Exchanges S4",
            "Find the coordinates for a location or vice-versa",
            "Fire",
            "Fire (Structure)",
            "Fire Hotspot",
            "Fire Hydrant",
            "Fire Incident",
            "Fire Incident  General fire occurrence",
            "Fire Origin",
            "Fire Spread Prediction",
            "Fire Station",
            "Fire Suppression Operation",
            "Fire(Wildland)",
            "Fire/Rescue",
            "First Aid Station",
            "First Name",
            "First Page",
            "Fixed Wing",
            "Flammable Gas",
            "Flammable Liquid",
            "Flammable Solid",
            "Fld Cbt Trains",
            "Flood",
            "Flood/Water Level:  Predetermined site for documentation of water line",
            "Fog",
            "Folder Management",
            "Follow-Up Form:  Additional information required not adequately described by symbol set",
            "Font Color",
            "Forbidden",
            "Forgot Password",
            "Forgotten Password Error",
            "Form cannot be null",
            "Friday",
            "Friendly Unit",
            "ft",
            "ft²",
            "Generate graphs",
            "Geocode Location",
            "Geocode was not successful for the following reason",
            "GISS",
            "Graphs",
            "GroundSupportUnitLeader",
            "Group",
            "Grow Text",
            "Hail",
            "Hazardous",
            "Hazardous Material Incident",
            "Hazardous Material Incident:  Nuclear biological or chemical incident",
            "Hazardous Materials",
            "The minimum length for this field is",
            "Health Department Facility",
            "Helibase",
            "Helicopter Landing Site:  Appropriate site for landing zone",
            "Helispot",
            "Help",
            "Home Phone",
            "Hospital",
            "Hospital Ship",
            "Hot Spot",
            "Hours",
            "Human Remains Removed:  Human remains removed from specific location",
            "Human Remains:  Confirmed victim determined to be deceased",
            "Hurricane",
            "Hyperlink",
            "ICS",
            "Id",
            "Images",
            "Immediate",
            "Impact On Mission",
            "Import Data",
            "Import Data Layer",
            "Import from Data Source",
            "IMT",
            "Incident",
            "Incident Base",
            "Incident Command Post",
            "Incident Data",
            "Incident Details",
            "Incident Error",
            "Incident Lookup",
            "Incident Map ",
            "Incident Name",
            "Incident name already exists",
            "Incident successfully updated",
            "Incident Types",
            "IncidentCommander",
            "Incidents",
            "Incorrect password",
            "Increase the font size.",
            "Indirect",
            "Infrastructure S1",
            "Infrastructure S2",
            "Infrastructure S3",
            "Infrastructure S4",
            "Input Error",
            "Insect Infestation",
            "Invalid configuration",
            "Invalid folder id value.",
            "Invalid form entity.",
            "Invalid MDTrack sent",
            "Invalid organization.",
            "Invalid report type.",
            "Invalid user id for username",
            "Invalid user id value.",
            "Invalid user name or password",
            "Invalid workspace configuration.",
            "Inversion",
            "Invite to Chat",
            "IR Downlink",
            "Is a value that has been excluded",
            "Is in the wrong format",
            "is inviting you to a private chat. Accept?",
            "is not a valid date - it must be in the format",
            "Is not a valid email address",
            "is not a valid time",
            "Is not in the list of acceptable values",
            "Italic (Ctrl+I)",
            "item(s)",
            "January",
            "Job Description",
            "Job Title",
            "Join",
            "Join Incident",
            "July",
            "June",
            "km",
            "km²",
            "Label",
            "Land Line",
            "Landslide",
            "Last Name",
            "Last Page",
            "Last Seen",
            "Last Update",
            "Last Updated",
            "Latitude",
            "Law Enforcement",
            "Law Enforcement Operation",
            "Layer",
            "Layer to import",
            "Legend",
            "Legends",
            "Length must be at least",
            "Length must be between",
            "Length must be no more than",
            "LiaisonOfficer",
            "Life Hazard",
            "Line",
            "Loading",
            "Loading...",
            "Locate",
            "Locate PLI",
            "Location",
            "Log back in",
            "Logged In",
            "Login Error",
            "Login failed for user",
            "Logistics",
            "LogisticsSectionChief",
            "Logout",
            "Longitude",
            "Lookout",
            "Lookup Users",
            "Looting",
            "m",
            "m²",
            "Make sure your username was entered correctly",
            "Make the selected text a hyperlink.",
            "Make the selected text bold.",
            "Make the selected text italic.",
            "Manage Settings",
            "Management Action Point",
            "Map Position Sync ",
            "Maps",
            "March",
            "Marine",
            "Marine Accident",
            "Marine Hijacking",
            "Marine Incident",
            "Marker",
            "Markers",
            "Mass Casualty",
            "May",
            "Measure Distance",
            "Meaure Area",
            "Mechanical Infantry",
            "Medical",
            "Medical Evacuation Helicopter Station",
            "Medical facilities Out Patient",
            "Medical Facility",
            "MediVac Site",
            "Message",
            "mi",
            "Military",
            "Military Exploitation UAV",
            "Military Soldier",
            "Military Vehicle",
            "Minor",
            "Missing search parameters.",
            "Mission",
            "Mobile Weather Unit",
            "Mobility Enhancement",
            "Modify Room Permissions",
            "Monday",
            "Morgue",
            "Mortuary",
            "Motor Transport",
            "Motorized Infantry",
            "Motorized Scouts",
            "Move others to my current map location",
            "MRGS",
            "Multi Incident View",
            "Multi-Incident-View Error",
            "Must be between",
            "Must be must be at least",
            "Must be no more than than",
            "Must be numeric",
            "Must be present",
            "N/A",
            "Name",
            "Natural Event",
            "Natural Event A",
            "Natural Event B",
            "Navigation",
            "New",
            "New Folder",
            "New Password",
            "New passwords do not match.",
            "Next",
            "Next Month (Control+Right)",
            "Next Page",
            "No",
            "No API is configured for this instance.",
            "No archived incidents where found that match the given criteria.",
            "No data to display",
            "No description available",
            "No file attachment found.",
            "No Incident Selected",
            "No organizations configured please contact your system administrator",
            "No Photos to Display",
            "No Threat",
            "No user found.",
            "No users where found that match the given criteria",
            "No Workspace Features Copied",
            "No workspace features were found to copy",
            "No workspaces are configured for this instance. ",
            "Non Residential Fire",
            "None Selected",
            "Non-Flammable Gas",
            "Non-Governmental Organization",
            "Notifications",
            "November",
            "Nuclear Accident",
            "Numbered List",
            "NWCG  - Defensible - Standalone",
            "NWCG - Defensible - Prep and Hold",
            "NWCG - Non-Defensible - Prep and Leave",
            "NWCG - Non-Defensible - Rescue Drive-By",
            "Occupants",
            "October",
            "of",
            "Office of Emergency Service",
            "Office Phone",
            "Oil Spill",
            "OK",
            "Old Password",
            "On",
            "One or more users failed to be added to the collaboration room",
            "Operation S1",
            "Operation S2",
            "Operation S3",
            "Operation S4",
            "Operations",
            "OperationsSectionChief",
            "Optional",
            "Or enter a custom room name",
            "Org Prefix",
            "Organic Peroxies",
            "Organization",
            "Organization Types",
            "Organization Update",
            "Organization was successfully updated.",
            "Organizations",
            "Other Government",
            "Other Local IMT",
            "Other Phone",
            "Other Water Supply Location",
            "Oxidizers",
            "Page",
            "Parent",
            "Password",
            "Password can not match old password. Please enter a new password.",
            "Passwords do not match.",
            "Paste",
            "Permission denied to view this room.",
            "Permissions Error",
            "Permissions Update",
            "Phone number is not a valid format.",
            "Photo",
            "Placed",
            "Planned Event",
            "Planned Fire Line",
            "PlanningSectionChief",
            "Plans",
            "Please Choose a view and an export format",
            "Please choose a workspace",
            "Please contact a system Administrator",
            "please contact one of the following DCDS Administrators ",
            "Please contact your system administrator.",
            "Please enter a new folder name",
            "Please enter a new layer name",
            "Please enter a user name or password that is authorized to access this application.",
            "Please enter any details pertaining to the error.",
            "Please enter either a State (US) or a Region",
            "Please enter the URL for the link",
            "Please include a display name.",
            "Please include a GPX file.",
            "Please include a JSON file.",
            "Please include a KML file.",
            "Please include a KMZ file.",
            "Please include a new password.",
            "Please include your old password.",
            "Please join a collaboration room.",
            "Please select an area less than 1000 square miles (current area",
            "Please try again. If failure continues, please contact an administrator",
            "Please Wait...",
            "PLI",
            "PLI labels",
            "Plot",
            "Point",
            "Point Maintenance",
            "Poisoning",
            "Police",
            "Polygon",
            "Possible IED",
            "Post Incident failed.",
            "Preferred Language",
            "Prefix",
            "Previous",
            "Previous Month (Control+Left)",
            "Previous Page",
            "Print",
            "Priority",
            "Prison",
            "Private Chat Invitation",
            "Private Volunteer Organization",
            "Projected",
            "Property Address",
            "Property City",
            "Property Zip Code",
            "Proposed Dozer Line",
            "Protective Measures",
            "Province",
            "Psyop",
            "Public Health / Medical Emergency",
            "PublicInformationOfficer",
            "Radio Number",
            "Radioactive Maerial",
            "Rail",
            "Rail Accident",
            "Rail Hijacking",
            "Rail Incident",
            "Rain",
            "Rank",
            "Read/Write Users",
            "readOnly",
            "Recipient",
            "Recommended Priority",
            "Recon Cav",
            "Redo",
            "Refresh",
            "Refresh Rate",
            "Region",
            "Register Error",
            "Registration failed",
            "Registration Success",
            "Remove Features?",
            "Rename",
            "Rename Datalayer",
            "Rename Folder",
            "Rename Layer",
            "Repeater Mobile Relay",
            "Reporting Location",
            "Reporting Unit",
            "Reports",
            "Reports by Unit",
            "Reports Per Day",
            "Request ignored.",
            "Rescued: Technical rescue that required physical intervention",
            "Reset Tool",
            "Residential Fire",
            "Resource",
            "Resource Threatened",
            "ResourcesUnitLeader",
            "Results will open in a new Web browser tab and may take up to a few minutes.",
            "Results will open in a new Web browser tab.",
            "Room Error",
            "Room Management",
            "Rooms",
            "Root",
            "Rotate",
            "Route Blocked: Inaccessible route by land or water",
            "Safety Zone",
            "SafetyOffice",
            "Sand Dust",
            "Saturday",
            "Save",
            "Saving",
            "says",
            "School Fire",
            "Search",
            "Search and Rescue",
            "Search results",
            "Search Type",
            "Search Value",
            "Seconday Fire Line",
            "Secret Service",
            "Secure",
            "Secure Collaboration Room",
            "Secure Room",
            "Security",
            "Segment Break",
            "Select a language...",
            "Select a room to create",
            "Select an incident to update",
            "Select an organization for this session",
            "Select an organization for this session:",
            "Select Box",
            "Select Organization",
            "Select Photo",
            "selected features?",
            "selected row(s)",
            "Send",
            "Send a message",
            "Sender",
            "Sending",
            "September",
            "Settings",
            "Shape",
            "Shape File",
            "Share Workspace",
            "Share your workspace contents with your collaboration room participants",
            "Share your workspace with the collaboration room",
            "Shelter in Place:  Survivors have chosen to remain at location",
            "Shooting",
            "Show in Groups",
            "Show Legend",
            "Shrink Text",
            "Side Panel",
            "SituationUnitLeader",
            "Size",
            "Smoke",
            "Snow",
            "Sort Ascending",
            "Sort Descending",
            "Source Edit",
            "Special Needs Fire",
            "Spontaneously Combustible",
            "Spot Fire",
            "square miles)",
            "Staging Area",
            "StagingAreaManager",
            "Start a bulleted list.",
            "Start a numbered list.",
            "Start a private chat with the selected user(s)",
            "State",
            "Static KML",
            "Status",
            "Street Address City Place or Landmark",
            "Striver Lav",
            "Structure Damaged:  Medium Risk structure is moderately damaged",
            "Structure Destroyed:  Complete destruction of structure",
            "Structure Failed:  High Risk may be subject to sudden collapse",
            "Structure No Damage:  Low Risk low probability of further collapse",
            "Subgroup",
            "submit",
            "Submit",
            "Subsidence",
            "Successfully registered!",
            "Successfully Submitted!",
            "Sunday",
            "Super",
            "Supply",
            "Supply Trains",
            "Supply Transport ",
            "SupplyUnitLeader",
            "Survey Team",
            "Survey Transport",
            "Switch to source editing mode.",
            "System Role",
            "Targeted Search:  Specific location or condition requiring increased search effort",
            "Telephone",
            "Terrorist Threat / Attack",
            "Text Highlight Color",
            "The date in this field must be after",
            "The date in this field must be before",
            "The export format type is not valid.",
            "The feature was not successfully removed from the user map.",
            "The following users were not successfully added ",
            "The maximum length for this field is",
            "The maximum value for this field is",
            "The minimum value for this field is",
            "The time in this field must be equal to or after",
            "The time in this field must be equal to or before",
            "The total area must be less than 1000 square miles. Double click when the polygon is complete.",
            "The username and password were not valid.",
            "The value in this field is invalid",
            "There are no active users",
            "There are no active vehicles",
            "There are no reports",
            "There is no current collab room. Please choose one from the list or join a new room.",
            "There was a problem saving the datasource",
            "There was an error creating the announcement",
            "There was an error deleting the announcement",
            "There was an error removing the organization from the orgtype.",
            "There was an error setting the account of",
            "There was an error unsecuring the room.",
            "There was an error updating the organization.",
            "There was an issue enabling the user.",
            "This date is after the maximum date",
            "This date is before the minimum date",
            "This field is required",
            "This field must contain single or multiple valid email addresses separated by a comma",
            "This field should be a URL in the format http//www.example.com",
            "This field should be an e-mail address in the format user@example.com",
            "This field should only contain decimal latitude and longitude numbers",
            "This field should only contain letters and _",
            "This field should only contain letters numbers and _",
            "This field should only contain letters numbers apostrophes and - .  +  ? _ %",
            "This field should only contain numbers and .  - %",
            "This field should only contain numbers spaces apostrophes - . and _",
            "Thrown",
            "Thunder Storm",
            "Thunderstorm",
            "Thursday",
            "Time since creation",
            "Title",
            "to",
            "To multi-select rows hold down the control key while selecting.",
            "To view this room select the tab from above.",
            "Today",
            "Tools",
            "Tornado",
            "Toxic and Infectious",
            "Toxic Gas",
            "Tracking",
            "Transportation",
            "Triage",
            "Tropical Cyclone",
            "Tropical Storm",
            "Try Again",
            "TSA",
            "Tsunami",
            "Tuesday",
            "Type",
            "Unable to authenticate source.",
            "Unable to join incident",
            "Unable to load legend",
            "Unable to read all organizations.",
            "Unable to read organization",
            "Unable to send feedback report",
            "Unacknowledged",
            "Uncontrolled Fire Edge Line",
            "Underline (Ctrl+U)",
            "Underline the selected text.",
            "Undo",
            "Unexpected error attempting to copy features",
            "Unexploded Ordnance",
            "Unhandled Exception",
            "Unhandled exception attempting to log User out",
            "Unhandled exception logging in",
            "Unhandled exception retrieving Incident.",
            "Unhandled exception while persisting Chat.",
            "Unhandled exception while persisting Feature change.",
            "Unhandled exception while persisting organization.",
            "Unhandled exception while persisting User Feature.",
            "Unhandled exception. Unable to read all incidents.",
            "Unsecure Room",
            "Update",
            "Update Button",
            "Update Incident",
            "Upload",
            "Uploading file...",
            "URL",
            "US Coast Guard",
            "US Marshals Service",
            "US National Grid System (USNG) / Military Grid Reference System (MGRS)",
            "USAR",
            "User",
            "User Account Info",
            "User Contact Info",
            "User Full Name",
            "User info has been updated.",
            "User Lookup",
            "User Name",
            "User organization has been changed.",
            "Username",
            "username already exists",
            "Username could not be found",
            "Users",
            "users were not successfully added to the organization. Please confirm that they are not already members.",
            "USNG / MGRS",
            "UXO Type",
            "Valid input includes letters numbers spaces and these special characters:    _ # ! . -",
            "Value",
            "Vehicle",
            "Vehicle Accident",
            "Vehicle Hijacking",
            "Vehicle Incident",
            "Victim Detected:  Potential victim detected (including canine alert or intelligence)",
            "View Archived Incidents",
            "Volcanic Eruption",
            "Volcanic threat",
            "Waiting for participants to accept invitiation",
            "Warning",
            "Water",
            "Water Source",
            "Waypoint Symbol",
            "Weather",
            "Weather Application",
            "Wednesday",
            "WFS Get Capabilities",
            "Whiteboard Chat",
            "Wild Fire",
            "Wind Direction",
            "WMS Get Capabilities",
            "Working Map",
            "Workspace",
            "Workspace Features Copied",
            "Would you like to secure the datasource?",
            "Yes",
            "You are already in the collaboration room",
            "You are in read-only mode. To modify this map",
            "You are in read-only mode. To modify this map, please contact one of the following DCDS adminstrators:",
            "You are not currently in a collaboration room.",
            "You are not currently in an incident.",
            "You do not have permission to edit this profile",
            "You do not have permission to view this profile",
            "You do not have permissions to modify this room.",
            "You do not have permissions to perform this function.",
            "You have been logged out",
            "You have been logged out due to an expired session, or by someone signing in with your account on another computer.",
            "You must choose an organization.",
            "You must select at least one item in this group",
            "You must select one item in this group",
            "You must supply one State or one Region.",
            "Your new data layer has been created",
            "Your password must be a minimum 8 characters long and a maximum of 20 with at least one digit one upper case letter one lower case letter and one special symbol",
            "Your username must be in a valid email format.",
            "You've successfully registered for DCDS. Your account will now be under review,  and enabled by an administrator.",
            "You've successfully requested your DCDS password be reset. You should receive an email shortly.",
            "Zone Break",
            "Zoom",
            "Zoom Box",
            "Zoom Level",
            "Zoom to selected report"
    };
}
