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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.rabbitmq.client.AlreadyClosedException;
import edu.mit.ll.nics.common.entity.CollabroomDatalayer;
import edu.mit.ll.nics.common.entity.datalayer.*;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.referencing.FactoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import edu.mit.ll.em.api.dataaccess.ShapefileDAO;
import edu.mit.ll.em.api.rs.DatalayerDocumentServiceResponse;
import edu.mit.ll.em.api.rs.DatalayerService;
import edu.mit.ll.em.api.rs.DatalayerServiceResponse;
import edu.mit.ll.em.api.rs.FieldMapResponse;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.FileUtil;
import edu.mit.ll.em.api.util.ImageLayerGenerator;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.entity.UserOrg;
import edu.mit.ll.nics.common.geoserver.api.GeoServer;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.DatalayerDAO;
import edu.mit.ll.nics.nicsdao.DocumentDAO;
import edu.mit.ll.nics.nicsdao.FolderDAO;
import edu.mit.ll.nics.nicsdao.UserDAO;
import edu.mit.ll.nics.nicsdao.impl.DatalayerDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.DocumentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.FolderDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserSessionDAOImpl;
import edu.mit.ll.nics.tools.image_processing.ImageProcessor;
import com.drew.lang.GeoLocation;


/**
 * @AUTHOR st23420
 */
public class DatalayerServiceImpl implements DatalayerService {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(DatalayerServiceImpl.class);

    /**
     * A standard KML root element.
     */
    private static final String KML_ROOT_START_TAG =
            "<kml xmlns=\"http://www.opengis.net/kml/2.2\" " +
                    "xmlns:gx=\"http://www.google.com/kml/ext/2.2\" " +
                    "xmlns:kml=\"http://www.opengis.net/kml/2.2\" " +
                    "xmlns:atom=\"http://www.w3.org/2005/Atom\">";

    /**
     * A pattern that matches KML documents without a root <kml> element.
     */
    private static final Pattern MALFORMED_KML_PATTERN = Pattern.compile("^\\s*<\\?xml[^>]+>\\s*<Document>",
            Pattern.MULTILINE);

    /**
     * Folder DAO
     */
    private static final DatalayerDAO datalayerDao = new DatalayerDAOImpl();
    private static final FolderDAO folderDao = new FolderDAOImpl();
    private static final DocumentDAO documentDao = new DocumentDAOImpl();
    private static final UserDAO userDao = new UserDAOImpl();
    private static final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();
    private static final UserSessionDAOImpl usersessionDao = new UserSessionDAOImpl();

    private static final String FAILED_TO_RETRIEVE = "Failed to retrieve data layers.";
    private static final String FAILED_TO_RETRIEVE_DATASOURCES = "Failed to retrieve data sources.";
    private static final String FAILED_TO_INSERT_DATASOURCE = "Failed to insert data source.";
    private static final String FAILED_TO_INSERT_DATALAYER = "Failed to insert data layer.";
    private static final String FAILED_TO_DELETE_DATALAYER = "Failed to delete data layer.";
    private static final String FAILED_TO_UPDATE_DATALAYER = "Failed to update data layer.";
    private static final String FAILED_UPLOAD = "Failed to upload file.";
    private static final String FAILED_UPLOAD_MISSING_FOLDER = "Failed to find folder to upload datalayer.";
    private static final String NO_FOLDER_NAME = "None";
    private static final String FAILED_PERMIISSION_DATASOURCE =
            "The user does not have permission to delete a data source";
    private static final String DELETE_DATASOURCE_ERROR =
            "The user does not have permission to delete a data source";
    private static final String MISSING_DATASOURCE_ID_ERROR = "Missing data source id.";

    private static String fileUploadPath;
    private static String mapserverURL;
    private static String mapserverPublicURL;
    private static String geoserverWorkspace;
    private static String geoserverDatastore;
    private static String webserverURL;

    private RabbitPubSubProducer rabbitProducer;

    private final Client jerseyClient;

    public DatalayerServiceImpl() {
        Configuration config = APIConfig.getInstance().getConfiguration();
        fileUploadPath = config.getString(APIConfig.FILE_UPLOAD_PATH, "/opt/data/nics/upload");
        geoserverWorkspace = config.getString(APIConfig.IMPORT_SHAPEFILE_WORKSPACE, "nics");
        geoserverDatastore = config.getString(APIConfig.IMPORT_SHAPEFILE_STORE, "shapefiles");
        mapserverURL = config.getString(APIConfig.EXPORT_MAPSERVER_URL);
        mapserverPublicURL = config.getString(APIConfig.EXPORT_MAPSERVER_PUBLIC_URL);
        webserverURL = config.getString(APIConfig.EXPORT_WEBSERVER_URL);
        jerseyClient = ClientBuilder.newClient();
    }

    @Override
    public Response getDatalayers(String folderId) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        try {
            datalayerResponse.setDatalayerfolders(datalayerDao.getDatalayerFolders(folderId));
        } catch(Exception e) {
            log.error("Failed to retrieve data layers", e);
            datalayerResponse.setMessage(FAILED_TO_RETRIEVE);
            return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(datalayerResponse).status(Status.OK).build();
    }

    public Response getCollabRoomDatalayers(int collabRoomId, String enablemobile) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        try {
            datalayerResponse.setDatalayers(datalayerDao.getCollabRoomDatalayers(collabRoomId, enablemobile));
        } catch(Exception e) {
            log.error("Failed to retrieve data layers", e);
            datalayerResponse.setMessage(FAILED_TO_RETRIEVE);
            return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(datalayerResponse).status(Status.OK).build();
    }


    @Override
    public Response getTrackingLayers(int workspaceId) {
        FieldMapResponse response = new FieldMapResponse();
        try {
            List<Map<String, Object>> layers = datalayerDao.getTrackingLayers(workspaceId, true);
            layers.addAll(datalayerDao.getTrackingLayers(workspaceId, false));
            response.setData(layers);
        } catch(Exception e) {
            log.error("Failed to retrieve data layers", e);
            response.setMessage(FAILED_TO_RETRIEVE);
            return Response.ok(response).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(response).status(Status.OK).build();
    }

    @Override
    public Response getDatasources(String type) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        try {
            datalayerResponse.setDatasources(datalayerDao.getDatasources(type));
        } catch(Exception e) {
            log.error("Failed to retrieve data sources", e);
            datalayerResponse.setMessage(FAILED_TO_RETRIEVE_DATASOURCES);
        }

        return Response.ok(datalayerResponse).status(Status.OK).build();
    }

    @Override
    public Response postDatasource(String type, Datasource source) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        Response response = null;

        try {
            int dataSourceTypeId = datalayerDao.getDatasourceTypeId(type);
            source.setDatasourcetypeid(dataSourceTypeId);

            String dataSourceId = datalayerDao.insertDataSource(source);
            Datasource newSource = datalayerDao.getDatasource(dataSourceId);
            datalayerResponse.setDatasources(Arrays.asList(newSource));
            datalayerResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(datalayerResponse).status(Status.OK).build();
        } catch(Exception e) {
            e.printStackTrace();
            log.error("Failed to insert data source", e);
            datalayerResponse.setMessage(FAILED_TO_INSERT_DATALAYER);
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    @Override
    public Response postTrackingLayer(int workspaceId, String dataSourceId, Datalayer datalayer) {
        return this.createDataLayer(workspaceId, dataSourceId, datalayer, this.getTrackingFolderId(workspaceId));
    }

    @Override
    public Response postDataLayer(int workspaceId, String dataSourceId, Datalayer datalayer, String folderId) {
        if(folderId == null || StringUtils.isBlank(folderId) ||
                folderId.equalsIgnoreCase(NO_FOLDER_NAME)){
            folderId = this.getUploadFolderId(workspaceId);
        }
        if(folderId == null){
            DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
            datalayerResponse.setSuccess(false);
            datalayerResponse.setMessage(FAILED_UPLOAD_MISSING_FOLDER);
            return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return this.createDataLayer(workspaceId, dataSourceId, datalayer, folderId);
    }

    private Response createDataLayer(int workspaceId, String dataSourceId, Datalayer datalayer, String folderId) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        Response response = null;
        Datalayerfolder newDatalayerFolder = null;

        try {
            datalayer.setCreated(new Date());
            datalayer.getDatalayersource().setCreated(new Date());
            datalayer.getDatalayersource().setDatasourceid(dataSourceId);

            String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);

            int orgId = -1;
            Set<DatalayerOrg> orgs = datalayer.getDatalayerOrgs();

            if(orgs != null) {
                for(DatalayerOrg org : orgs) {
                    orgId = org.getOrgid();
                    datalayerDao.insertDatalayerOrg(datalayerId, orgId);
                }

            }

            int collabroomId = -1;
            Set<CollabroomDatalayer> collabrooms = datalayer.getCollabroomDatalayers();
            if(collabrooms.size() == 1) {

                // Upload to collab room only

                for(CollabroomDatalayer collabroom : collabrooms) {
                    collabroomId = collabroom.getCollabroomid();
                }
                Response collabroomResponse = addCollabroomDatalayer(collabroomId, datalayerId);
                // TODO: check collabroomResponse for success

                datalayerResponse.setMessage(Status.OK.toString());
                response = Response.ok(datalayerResponse).status(Status.OK).build();
            } else {
                // Upload to master datalayer list

                //Currently always uploads to Data
                //Rootfolder folder = folderDao.getRootFolder("Data", workspaceId);
                //Currently always uploads to Upload
                if(folderId != null) {
                    int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folderId);
                    datalayerDao.insertDataLayerFolder(folderId, datalayerId, nextFolderIndex);
                    newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folderId);

                    datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
                    datalayerResponse.setMessage(Status.OK.toString());
                    response = Response.ok(datalayerResponse).status(Status.OK).build();
                }else{
                    log.error("No folder indicated and the upload folder is not configured");
                    response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                }
            }
        } catch(Exception e) {
            log.error("Failed to insert data layer", e);
            datalayerResponse.setMessage("failed");
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                notifyNewChange(newDatalayerFolder, workspaceId);
            } catch(IOException | TimeoutException | AlreadyClosedException e) {
                log.error("Failed to publish DatalayerService message event", e);
            } catch(Exception e) {
                log.error("Failed to publish DatalayerService message event", e);
            }
        }

        return response;
    }

    @Override
    public Response deleteDataLayer(int workspaceId, String dataSourceId) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        Response response = null;
        boolean deleteDatalayer = false;

        try {

            deleteDatalayer = datalayerDao.removeDataLayer(dataSourceId);

            if(deleteDatalayer) {
                datalayerResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(datalayerResponse).status(Status.OK).build();
            } else {
                datalayerResponse.setMessage(FAILED_TO_DELETE_DATALAYER);
                response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }


        } catch(Exception e) {
            log.error("Failed to delete data layer", e);
            datalayerResponse.setMessage(FAILED_TO_DELETE_DATALAYER);
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                notifyDeleteChange(dataSourceId);
            } catch(IOException | TimeoutException | AlreadyClosedException e) {
                log.error("Failed to publish DatalayerService message event", e);
            } catch(Exception e) {
                log.error("Failed to publish DatalayerService message event", e);
            }
        }

        return response;
    }

    public Response updateDataLayer(int workspaceId, Datalayer datalayer) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        Response response = null;
        Datalayer dbDatalayer = null;

        try {

            dbDatalayer = datalayerDao.updateDataLayer(datalayer);

            if(dbDatalayer != null) {
                datalayerResponse.setCount(1);
                datalayerResponse.setMessage(Status.OK.getReasonPhrase());
                response = Response.ok(datalayerResponse).status(Status.OK).build();
            } else {
                datalayerResponse.setMessage(FAILED_TO_UPDATE_DATALAYER);
                response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }


        } catch(Exception e) {
            log.error("Failed to delete data layer", e);
            datalayerResponse.setMessage(FAILED_TO_UPDATE_DATALAYER);
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                notifyUpdateChange(dbDatalayer);
            } catch(IOException | TimeoutException | AlreadyClosedException e) {
                log.error("Failed to publish DatalayerService message event", e);
            } catch(Exception e) {
                log.error("Failed to publish DatalayerService message event", e);
            }
        }

        datalayerResponse.setMessage(Status.OK.getReasonPhrase());
        response = Response.ok(datalayerResponse).status(Status.OK).build();

        return response;
    }

    public Response postShapeDataLayer(int workspaceId, String displayName, MultipartBody body,
                                       String username, String folderId) {
        if(!userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) &&
                !userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID) &&
                !userOrgDao.isUserRole(username, SADisplayConstants.GIS_ROLE_ID)) {
            return getInvalidResponse();
        }

        ShapefileDAO geoserverDao = ShapefileDAO.getInstance();
        GeoServer geoserver = getGeoServer(APIConfig.getInstance().getConfiguration());
        String dataSourceId = getMapserverDatasourceId();
        if(dataSourceId == null) {
            throw new WebApplicationException("Failed to find configured NICS wms datasource");
        }

        Attachment aShape = body.getAttachment("shpFile");
        if(aShape == null) {
            throw new WebApplicationException("Required attachment 'shpFile' not found");
        }
        String shpFilename = aShape.getContentDisposition().getParameter("filename");
        String batchName = shpFilename.replace(".shp", "").replace(" ", "_");
        String layerName = batchName.concat(String.valueOf(System.currentTimeMillis()));

        //write all the uploaded files to the filesystem in a temp directory
        Path shapesDirectory = Paths.get(fileUploadPath, "shapefiles");
        Path batchDirectory = null;
        try {
            Files.createDirectories(shapesDirectory);

            batchDirectory = Files.createTempDirectory(shapesDirectory, batchName);
            List<Attachment> attachments = body.getAllAttachments();
            for(Attachment attachment : attachments) {
                String filename = attachment.getContentDisposition().getParameter("filename");
                String extension = FileUtil.getFileExtension(filename);
                if(extension != null) {
                    Path path = batchDirectory.resolve(batchName.concat(extension));
                    InputStream is = attachment.getDataHandler().getInputStream();
                    Files.copy(is, path);
                }
            }

            //attempt to read our shapefile and accompanying files
            Path shpPath = batchDirectory.resolve(batchName.concat(".shp"));
            FileDataStore store = FileDataStoreFinder.getDataStore(shpPath.toFile());
            SimpleFeatureSource featureSource = store.getFeatureSource();

            //attempt to insert our features into their own table
            geoserverDao.insertFeatures(layerName, featureSource);
        } catch(IOException | FactoryException e) {
            try {
                geoserverDao.removeFeaturesTable(layerName);
            } catch(IOException ioe) { /* bury */}
            throw new WebApplicationException("Failed to import shapefile", e);
        } finally {
            //always clean up our temp directory
            if(batchDirectory != null) {
                try {
                    FileUtil.deleteRecursively(batchDirectory);
                } catch(IOException e) {
                    log.error("Failed to cleanup shapefile batch directory", e);
                }
            }
        }

        //add postgis layer to map server
        if(!geoserver.addFeatureType(geoserverWorkspace, geoserverDatastore, layerName, "EPSG:3857")) {
            try {
                geoserverDao.removeFeaturesTable(layerName);
            } catch(IOException e) { /* bury */}
            throw new WebApplicationException("Failed to create features " + layerName);
        }

        //apply styling default or custom sld
        String defaultStyleName = "defaultShapefileStyle";
        Attachment aSld = body.getAttachment("sldFile");
        if(aSld != null) {
            String sldXml = aSld.getObject(String.class);
            if(geoserver.addStyle(layerName, sldXml)) {
                defaultStyleName = layerName;
            }
        }
        geoserver.updateLayerStyle(layerName, defaultStyleName);
        geoserver.updateLayerEnabled(layerName, true);

        //create datalayer and datalayersource for our new layer
        int usersessionid = usersessionDao.getUserSessionid(username);

        Datalayer datalayer = new Datalayer();
        datalayer.setCreated(new Date());
        datalayer.setBaselayer(false);
        datalayer.setDisplayname(displayName);
        datalayer.setUsersessionid(usersessionid);

        Datalayersource dlsource = new Datalayersource();
        dlsource.setLayername(layerName);
        dlsource.setCreated(new Date());
        dlsource.setDatasourceid(dataSourceId);
        datalayer.setDatalayersource(dlsource);

        String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);

        if(folderId == null || StringUtils.isBlank(folderId) ||
                folderId.equalsIgnoreCase(NO_FOLDER_NAME)){
            folderId = this.getUploadFolderId(workspaceId);
            if(folderId == null){
                DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
                datalayerResponse.setSuccess(false);
                datalayerResponse.setMessage(FAILED_UPLOAD_MISSING_FOLDER);
                return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        }

        int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folderId);
        datalayerDao.insertDataLayerFolder(folderId, datalayerId, nextFolderIndex);

        //retrieve the new datalayerfolder to return to the client and broadcast
        Datalayerfolder newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId,folderId);

        try {
            notifyNewChange(newDatalayerFolder, workspaceId);
        } catch(IOException | TimeoutException | AlreadyClosedException e) {
            log.error("Failed to publish DatalayerService message event", e);
        } catch(Exception e) {
            log.error("Failed to publish DatalayerService message event", e);
        }

        DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
        datalayerResponse.setSuccess(true);
        datalayerResponse.setCount(1);
        datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
        return Response.ok(datalayerResponse).status(Status.OK).build();
    }


    /**
     * Uses the {@link GeoServer} API to find out whether or not the given layer exists in the given workspace.
     *
     * @param displayName the name of the layer to check for
     * @param workspace   the workspace the layer belongs to
     * @return true if layer exists, false otherwise
     *
     * @throws Exception if there was an error discovering if layer exists or not
     */
    public boolean geoserverLayerExists(String displayName, String workspace) throws Exception {
        // TODO: should add this as utility method in Geoserver for simple boolean layerExists() call

        boolean result;

        // Check to see if the <workspace>:<layername> already exists
        GeoServer geoserverApi = getGeoServer(APIConfig.getInstance().getConfiguration());
        String existingLayer = geoserverApi.getLayer(displayName, workspace, null);
        if(existingLayer != null && existingLayer.contains("No such layer")) {
            // safe
            log.debug("GeoServer responded with no such layer when checking for {}:{} - {}",
                    workspace, displayName, existingLayer);

            result = false;

        } else if(existingLayer != null && existingLayer.contains("Name: " + displayName)) {
            // Default html response contains "<li>Name: NAME</li>" if layer is found, and html is what it
            // currently sends. However, you could also add .json to the endpoint in the Geoserver class,
            // and get back JSON, which you'd have to match differently, but is more desirable.

            result = true;
        } else {
            // Unexpected result
            log.debug("Couldn't verify whether or not layer '{}' already exists in " +
                            "workspace '{}'. <br/><br/>{}",
                    displayName, geoserverWorkspace, existingLayer);
            throw new Exception("Couldn't verify whether or not layer exists in workspace: " + existingLayer);
        }

        return result;
    }


    /**
     * Utility method for building a DatalayerDocumentServiceResponse with a failure message and status
     *
     * @param message
     * @param status  HTTP Status reason representative of failure
     * @return
     */
    private Response buildFailResponse(String message, Status status) {
        DatalayerDocumentServiceResponse datalayerServiceResponse = new DatalayerDocumentServiceResponse();
        datalayerServiceResponse.setSuccess(false);
        datalayerServiceResponse.setMessage(message);
        return Response.ok(datalayerServiceResponse).status(status).build();
    }


    /**
     * Auto-creates a coverage store and adds the specified geotiff file to it, as well as creates the layer in
     * GeoServer.
     *
     * @param workspaceId the workspace to create the NICS layer in
     * @param displayName the name of the Layer to be displayed
     * @param fileName    A local to the mapserver filename specified in the form
     * @param isMosaic    whether or not this is a zip file containing images to be used as a mosiac
     * @param body        multipart form body containing displayname and uploaded filename
     * @param username    the NICS user adding the geotiff file
     * @return response specifying success/failure and reason
     */
    public Response postGeotiffDataLayer(int workspaceId, String displayName, String fileName, Attachment uploadedFile,
                                         boolean isMosaic, MultipartBody body,
                                         String username, String folderId) {
        int usersessionid = -1;
        if(username != null) { // Save a call to usersessionDao if username is null
            usersessionid = usersessionDao.getUserSessionid(username);
        }

        // Can't continue without a valid current usersessionid
        if(usersessionid == -1) {
            return buildFailResponse(String.format(
                    "Failure: Received invalid usersessionid(%d) for user '%s'. Can't create datalayer.",
                    usersessionid, username),
                    Status.PRECONDITION_FAILED);
        }

        // Check if the layer name already exists in the workspace
        boolean layerExists;
        try {
            layerExists = geoserverLayerExists(displayName, geoserverWorkspace);
            if(layerExists) {
                // layer exists
                return buildFailResponse("Layer already exists: " + geoserverWorkspace + ":" + displayName,
                        Status.PRECONDITION_FAILED);
            }
        } catch(Exception e) {
            log.error("Exception checking whether or not layer exists", e);
            return buildFailResponse("Unable to determine if layer exists: " + e.getMessage(),
                    Status.PRECONDITION_FAILED);
        }

        DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
        boolean dataPayloadFound = false;
        String attachmentContentType = null;
        boolean isLocalFile = false;
        boolean isUploaded = false;
        String uploadedFileName = null;
        StringBuilder filePath = null;

        // Check uploadedFile attachment. Even if it's not specified, it's still not null
        // So check filename
        if(uploadedFile != null && uploadedFile.getDataHandler().getName() != null) {
            uploadedFileName = uploadedFile.getDataHandler().getName();
            log.debug("UploadedFilename: {}", uploadedFileName);

            // Check content type
            // TODO: use constants, any variations?
            // Other clients/browsers may change zip to application/octet-stream, for example
            attachmentContentType = uploadedFile.getContentType().toString();
            if(!attachmentContentType.equals(null) &&
                    (attachmentContentType.contains("application/zip") ||
                            attachmentContentType.contains("image/tiff"))) {

                log.debug("Got valid content type '{}' for uploaded attachment", attachmentContentType);

            } else {
                log.warn("Incorrect content type. Expected application/zip or image/tiff, but received '{}'",
                        attachmentContentType);

                return buildFailResponse(
                        "Incorrect content type. Expected application/zip or image/tiff, but received '" +
                                attachmentContentType + "'", Status.PRECONDITION_FAILED);
            }
            isUploaded = true;
        } else {
            log.debug("No uploaded file was specified");
        }

        if(fileName != null && !fileName.isEmpty()) {
            isLocalFile = true;
        }

        // Can't specify both at the same time
        if((isUploaded && isLocalFile) || (!isUploaded && !isLocalFile)) {
            return buildFailResponse("Must at least specify a file local to the server, " +
                    "OR upload a file, not both.", Status.PRECONDITION_FAILED);
        }

        String workingFilename = null;
        String filePathGeoserver = null; // The filePath parameter to the geoserver call
        String dirName;
        String storeName;

        if(isUploaded) {
            workingFilename = uploadedFileName;
            dataPayloadFound = true;

            // Build the path to where we will store the uploaded file
            filePath = new StringBuilder(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.GEOTIFF_UPLOAD_PATH,
                            "/opt/data/nics/upload/images/geotiff"));

            // Only add the "/" if it's not already there
            if(!filePath.toString().endsWith(File.separator)) {
                filePath.append(File.separator);
            }
            log.debug("geotiff:FILE PATH : {}", filePath.toString());

        } else if(isLocalFile) {
            // Just want the filename, not the whole path, for the local file, since it's used
            // as the directory name and store name
            String[] parts = fileName.split("/");
            workingFilename = parts[parts.length - 1];
            filePathGeoserver = fileName;
            dataPayloadFound = true;
        }
        // Get the file extension. Could/should probably use File or something to get it instead of manually
        String ext = workingFilename.substring(workingFilename.indexOf("."), workingFilename.length());
        log.debug("Got extension from working file '{}': {}", workingFilename, ext);

        // Remove extension, and change any spaces to underscores...
        dirName = workingFilename.replace(ext, "").replace(" ", "_");

        // Add the current time to the filename as a differentiator, and use as the store name
        // for both uploaded files and locally specified files
        storeName = dirName.concat(String.valueOf(System.currentTimeMillis()));

        // Create the directory to write the file to, and read the content of the file
        Path path = null;
        byte[] content = null;
        if(isUploaded) {
            // Add the generated store name to the path
            filePath.append(storeName);
            path = FileUtil.getInstance().createFile(uploadedFile, Paths.get(filePath.toString()));
            if(path == null) {
                return buildFailResponse("Failed to create directory", Status.PRECONDITION_FAILED);
            }

            attachmentContentType = uploadedFile.getContentType().toString();
            filePathGeoserver = filePath.toString() + "/" + path.getFileName();

            try {
                content = IOUtils.toByteArray(uploadedFile.getDataHandler().getInputStream());
                log.debug("Read {} bytes from attachment", content.length);
            } catch(IOException e) {
                log.error("Exception reading bytes from attachment with content-type '{}' and filename '{}'",
                        uploadedFile.getContentType(),
                        uploadedFile.getContentDisposition().getParameter("filename"));

                return buildFailResponse(
                        "Failed to read uploaded file contents - " + e.getMessage(),
                        Status.INTERNAL_SERVER_ERROR);
            }
        }


        final GeoServer geoserver = getGeoServer(APIConfig.getInstance().getConfiguration());

        // Make the request to Geoserver to add the file
        String addSuccess = geoserver.addGeotiff(
                geoserverWorkspace,
                storeName, displayName,
                attachmentContentType,
                filePathGeoserver,
                content, // Just null if isLocalFile
                isMosaic);

        // Newline was breaking json parsing
        addSuccess = addSuccess.replace("\n", "");

        JSONObject addSuccessJson = null;
        try {
            addSuccessJson = new JSONObject(addSuccess);
        } catch(JSONException e) {
            log.error("Exception processing addGeotiff success response", e);
        }


        if(addSuccessJson.optString("status", "fail") == "success" ||
                // Fallback in case of json parse error?
                addSuccess.contains("'status':'success'")) {

            log.debug("\n!!!Successfully added to GeoServer!!!\n{}", addSuccess);

            Response result = finishGeotiffLayer(usersessionid, workspaceId, displayName, folderId);
            DatalayerServiceResponse dlResponse;
            String msg = "";
            try {
                dlResponse = (DatalayerServiceResponse) result.getEntity();
                msg = dlResponse.getMessage();
            } catch(Exception e) {
                log.error("Exception reading entity from response", e);
                msg += e.getMessage();
            }

            int status = result.getStatus();
            String reason = result.getStatusInfo().getReasonPhrase();

            log.debug("Status: {} - {}", status, msg);

            if(status == 200) {
                datalayerResponse.setSuccess(true);
                datalayerResponse.setMessage(
                        "Successfully created store/layer in GeoServer and in NICS");
            } else {
                datalayerResponse.setSuccess(false);
                datalayerResponse.setMessage("Successfully created store/layer in GeoServer, but failed " +
                        "to create layer in NICS with: " + reason + "(" + status + ")<br/><br/> " + msg);
                // TODO: Delete store and layer, or give info for admin to manually add the layer?
            }

        } else {
            log.warn("\n!!!FAILED to add to GeoServer!!!\n{}", addSuccess);
            datalayerResponse.setSuccess(false);
            final String failMessage = addSuccessJson.optString("msg", "Reason not specified");
            datalayerResponse.setMessage(failMessage);
        }

        if(!dataPayloadFound) {
            datalayerResponse.setSuccess(false);
            datalayerResponse.setMessage("No image or zip found.");
        }

        return Response.ok(datalayerResponse).status(Status.OK).build();
    }


    /**
     * @param workspaceId the workspace to create the NICS layer in
     * @param displayName the name of the georss Layer to be displayed
     * @param feedUrl     url to the georss feed
     * @param body        multipart form body containing displayname and uploaded filename
     * @param username    the NICS user adding the georss url
     * @return
     */
    public Response postGeorssDataLayer(int workspaceId, String displayName,
                                        String feedUrl, MultipartBody body, String username,
                                        String folderId) {

        if(!userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) &&
                !userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID) &&
                !userOrgDao.isUserRole(username, SADisplayConstants.GIS_ROLE_ID)) {
            return getInvalidResponse();
        }


        int usersessionid = -1;
        if(username != null) { // Save a call to usersessionDao if username is null
            usersessionid = usersessionDao.getUserSessionid(username);
        }

        // Can't continue without a valid current usersessionid
        if(usersessionid == -1) {
            return buildFailResponse(String.format(
                    "Failure: Received invalid usersessionid(%d) for user '%s'. Can't create GeoRSS layer.",
                    usersessionid, username),
                    Status.PRECONDITION_FAILED);
        }

        // layer will not exist on geoserver

        DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
        boolean dataPayloadFound = false;
        String attachmentContentType = null;
        boolean isLocalFile = false;
        boolean isUploaded = false;
        String uploadedFeedName = null;

        if(feedUrl != null && !feedUrl.isEmpty() && displayName != null && !displayName.isEmpty()) {
            log.debug("Uploaded GeoRSS display name: {}", displayName);
            log.debug("Uploaded GeoRSS url: {}", feedUrl);

            Datalayersource datalayerSource = new Datalayersource();
            datalayerSource.setLayername(displayName);

            Datalayer datalayer = new Datalayer();
            datalayer.setDatalayersource(datalayerSource);
            datalayer.setBaselayer(false);
            datalayer.setCreated(new Date());
            datalayer.setDisplayname(displayName);
            datalayer.setUsersessionid(usersessionid);

            return this.postDataLayer(workspaceId, null, datalayer, folderId);
        } else {
            DatalayerDocumentServiceResponse badDataSourceResponse = new DatalayerDocumentServiceResponse();
            // TODO: shouldn't put html in responses since it's not always for display in a browser, but I do
            // TODO: like the option if we knew the client was nics-web
            final String message = "Invalid GeoRSS URL";
            badDataSourceResponse.setMessage(message);
            badDataSourceResponse.setSuccess(false);
            return Response.ok(badDataSourceResponse).status(Status.PRECONDITION_FAILED).build();
        }

    }

    /**
     * Handles adding the freshly added geotiff layer in GeoServer as a layer in NICS
     *
     * @param usersessionId the current usersessionId of the user posting the layer
     * @param workspaceId   the workspace id of the workspace this is to be uploaded under
     * @param layerName     the displayName/Layer name
     * @return a Response specifying whether or not the datalayer was successfully created or not
     */
    public Response finishGeotiffLayer(int usersessionId, int workspaceId, String layerName,
                                       String folderId) {

        String datasourceId = datalayerDao.getDatasourceId(
                APIConfig.getInstance().getConfiguration().getString(APIConfig.IMPORT_GEOTIFF_DATASOURCE_URL));

        if(datasourceId == null) {
            DatalayerDocumentServiceResponse badDataSourceResponse = new DatalayerDocumentServiceResponse();
            // TODO: shouldn't put html in responses since it's not always for display in a browser, but I do
            // TODO: like the option if we knew the client was nics-web
            final String message = String.format("Failed to get datasourceId specified by: <ul><li>Property: %s</li>" +
                            "<li>Value: %s</li></ul>",
                    APIConfig.IMPORT_GEOTIFF_DATASOURCE_URL,
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.IMPORT_GEOTIFF_DATASOURCE_URL));
            badDataSourceResponse.setMessage(message);
            badDataSourceResponse.setSuccess(false);
            return Response.ok(badDataSourceResponse).status(Status.PRECONDITION_FAILED).build();
        }

        Datalayersource datalayerSource = new Datalayersource();
        datalayerSource.setLayername(layerName);

        Datalayer datalayer = new Datalayer();
        datalayer.setDatalayersource(datalayerSource);
        datalayer.setBaselayer(false);
        datalayer.setCreated(new Date());
        datalayer.setDisplayname(layerName);
        datalayer.setUsersessionid(usersessionId);

        log.debug("Post Geotiff Datalayer (workspaceId, datasourceId, layerName): {}, {}, {}",
                workspaceId, datasourceId, datalayer.getDisplayname());

        return this.postDataLayer(workspaceId, datasourceId, datalayer, folderId);
    }

    public Response postImageDataLayer(int workspaceId, String id, MultipartBody body, String username) {
        DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
        boolean imageFound = false;
        Status responseStatus = null;

        for(Attachment attachment : body.getAllAttachments()) {

            log.debug("Content Type: {}", attachment.getContentType().getType());

            if(attachment.getContentType().getType().contains("image")) {
                imageFound = true;

                StringBuffer filePath = new StringBuffer(
                        APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_FEATURE_PATH,
                                "/opt/data/nics/upload/images"));
                filePath.append("/");
                filePath.append(id);

                log.debug("FILE PATH : {}", filePath.toString());

                Path path = FileUtil.getInstance().createFile(attachment, Paths.get(filePath.toString()));

                if(path != null) {
                    log.debug("PATH : {}/{}", filePath.toString(), path.getFileName());

                    GeoLocation location = ImageProcessor.getLocation(filePath.toString() + "/" +
                            path.getFileName());

                    if(location != null) {
                        StringBuffer locationString = new StringBuffer("POINT(");
                        locationString.append(location.getLongitude());
                        locationString.append(" ");
                        locationString.append(location.getLatitude());
                        locationString.append(")");

                        log.debug("LOCATION : {},{}", location.getLongitude(), location.getLatitude());

                        if(datalayerDao.insertImageFeature(
                                id, locationString.toString(),
                                id + "/" + path.getFileName().toString()) == 1) {
                            datalayerResponse.setSuccess(true);
                            datalayerResponse.setCount(1);
                            responseStatus = Status.OK;
                        } else {
                            datalayerResponse.setSuccess(false);
                            datalayerResponse.setMessage("There was an error persisting the image.");
                            responseStatus = Status.INTERNAL_SERVER_ERROR;
                        }
                    } else {
                        log.debug("No location found for the image...");
                        datalayerResponse.setSuccess(false);
                        datalayerResponse.setMessage("No location metadata found in image. Can't import to layer.");
                        responseStatus = Status.PRECONDITION_FAILED;
                        datalayerResponse.setMessage("No location found for image.");
                    }
                } else {
                    datalayerResponse.setSuccess(false);
                    datalayerResponse.setMessage("There was an error creating the directory.");
                    responseStatus = Status.INTERNAL_SERVER_ERROR;
                }
            }

            if(!imageFound) {
                datalayerResponse.setSuccess(false);
                datalayerResponse.setMessage("No image found.");
                responseStatus = Status.BAD_REQUEST;
            }
        }

        if(responseStatus == null) {
            responseStatus = Status.INTERNAL_SERVER_ERROR;
            datalayerResponse.setMessage("Unknown error.");
        }


        return Response.ok(datalayerResponse).status(responseStatus).build();
    }

    public Response finishImageLayer(boolean cancel, int workspaceId, String id,
                                     String title, int usersessionId, String folderId) {

        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        if(!cancel) {
            ImageLayerGenerator generator = new ImageLayerGenerator(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_URL),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_USERNAME),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_PASSWORD),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_WORKSPACE),
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_STORE));

            boolean layerCreated = generator.addImageLayer(id, title);

            log.debug("Layer Created : {}", layerCreated);

            if(layerCreated) {
                String datasourceId = datalayerDao.getDatasourceId(
                        APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_DATASOURCE_URL));

                Datalayer datalayer = new Datalayer();
                datalayer.setBaselayer(false);
                datalayer.setCreated(new Date());
                datalayer.setDisplayname(title);
                datalayer.setUsersessionid(usersessionId);

                Datalayersource datalayerSource = new Datalayersource();
                datalayerSource.setLayername(id);
                datalayer.setDatalayersource(datalayerSource);

                log.debug("Post Datalayer (workspaceId, datasourceId, displayName): {}, {}, {}",
                        workspaceId, datasourceId, datalayer.getDisplayname());

                return this.postDataLayer(workspaceId, datasourceId, datalayer, folderId);
            }
        } else {
            StringBuffer responseMessage = new StringBuffer();
            //Remove the image files from the file system
            StringBuffer filePath = new StringBuffer(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_FEATURE_PATH,
                            "/opt/data/nics/upload/images"));
            filePath.append("/");
            filePath.append(id);

            Path path = Paths.get(filePath.toString());

            try {
                Files.delete(path);
            } catch(NoSuchFileException x) {
                x.printStackTrace();
                responseMessage.append(String.format("%s: no such" + " file or directory%n", path));
            } catch(DirectoryNotEmptyException x) {
                x.printStackTrace();
                responseMessage.append(String.format("%s not empty%n", path));
            } catch(IOException x) {
                x.printStackTrace();
                responseMessage.append(x.getMessage());
            }

            if(responseMessage.length() != 0) {
                responseMessage.append(System.getProperty("line.separator"));
            }

            //Remove all imagefeature entries from the database
            int removed = this.datalayerDao.removeImageFeatures(id);
            if(removed < 1) {
                responseMessage.append("The images could not be removed from the database.");
            }

            if(responseMessage.length() != 0) {
                datalayerResponse.setMessage(responseMessage.toString());
            }
        }

        return Response.ok(datalayerResponse).status(Status.OK).build();
    }

    public Response postTrackingIcon(MultipartBody body, String username) {
        String filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.LAYER_ICON_UPLOAD_PATH,
                "/opt/data/nics/upload/tracking");

        DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
        try {
            System.out.println("Attachments: " + body.getAllAttachments().size());
            for(Attachment attachment : body.getAllAttachments()) {
                System.out.println("Content-Type: " + attachment.getContentType());
                if(attachment.getContentType().toString().indexOf("image") != -1 &&
                        filePath != null) {
                    Path path = FileUtil.getInstance().createFile(attachment, Paths.get(filePath));
                    datalayerResponse.setMessage(path.getFileName().toString());
                    datalayerResponse.setSuccess(true);
                    return Response.ok(datalayerResponse).status(Status.OK).build();
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();

    }

    public Response postDataLayerDocument(int workspaceId, String fileExt, int userOrgId, int refreshRate,
                                          MultipartBody body, String username) {
        DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
        Response response = null;
        Datalayerfolder newDatalayerFolder = null;
        Datalayer datalayer = new Datalayer();
        datalayer.setDatalayersource(new Datalayersource());
        String dataSourceId = null;
        Document doc = null;
        Boolean uploadedDataLayer = false;
        String fileName = null;
        String filePath = null;
        Boolean valid = false;
        User user = null;
        int orgId = -1;
        int collabroomId = -1;
        String folderId = null;

        try {

            user = userDao.getUser(username);
            Set<UserOrg> userOrgs = user.getUserorgs();
            Iterator<UserOrg> iter = userOrgs.iterator();

            while(iter.hasNext()) {

                UserOrg userOrg = (UserOrg) iter.next();

                if(userOrg.getUserorgid() == userOrgId &&
                        (userOrg.getSystemroleid() == SADisplayConstants.SUPER_ROLE_ID ||
                                userOrg.getSystemroleid() == SADisplayConstants.GIS_ROLE_ID ||
                                userOrg.getSystemroleid() == SADisplayConstants.ADMIN_ROLE_ID)) {
                    valid = true;
                }

            }

            if(!valid) {
                return getInvalidResponse();
            }

            for(Attachment attachment : body.getAllAttachments()) {

                Object propValue = attachment.getObject(String.class).toString();

                if(MediaType.TEXT_PLAIN_TYPE.isCompatible(attachment.getContentType())) {
                    log.info("plain text compatible! name: " + attachment.getContentDisposition()
                            .getParameter("name"));

                    String name = attachment.getContentDisposition().getParameter("name")
                            .toString();

                    if(name.equals("folderid")) {
                        folderId = propValue.toString();
                    }

                    if(name.equals("usersessionid")) {
                        datalayer.setUsersessionid(Integer.valueOf(propValue.toString()));
                    }

                    if(name.equals("displayname")) {
                        datalayer.setDisplayname(propValue.toString());
                    }

                    if(name.equals("baselayer")) {
                        datalayer.setBaselayer(Boolean.parseBoolean(propValue.toString()));
                    }

                    if(name.equals("orgid")) {
                        try {
                            orgId = Integer.parseInt(propValue.toString());
                        } catch(NumberFormatException ex) {
                            // orgId isn't an integer; datalayer should not be restricted to an organization
                            log.error("Exception parsing {} to an int", propValue.toString(), ex);
                        }
                    }

                    if(name.equals("collabroomId")) {
                        try {
                            collabroomId = Integer.parseInt(propValue.toString());
                            log.debug("============= collabroomId={}", collabroomId);
                        } catch(NumberFormatException ex) {
                            log.error("Exception parsing {} to an int", propValue.toString(), ex);
                        }
                    }
                } else {
                    String attachmentFilename = attachment.getContentDisposition().getParameter("filename")
                            .toLowerCase();
                    if(attachmentFilename.endsWith(".kmz")) {
                        filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.KMZ_UPLOAD_PATH,
                                "/opt/data/nics/upload/kmz");
                    } else if(attachmentFilename.endsWith(".gpx")) {
                        filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.GPX_UPLOAD_PATH,
                                "/opt/data/nics/upload/gpx");
                    } else if(attachmentFilename.endsWith(".json") || attachmentFilename.endsWith(".geojson")) {
                        filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.JSON_UPLOAD_PATH,
                                "/opt/data/nics/upload/geojson");
                    } else if(attachmentFilename.endsWith(".kml")) {
                        filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.KML_UPLOAD_PATH,
                                "/opt/data/nics/upload/kml");
                    }

                    if(filePath != null) {
                        doc = getDocument(attachment, Paths.get(filePath));
                    }
                }
            }

            if(doc != null) {

                doc.setUsersessionid(datalayer.getUsersessionid());
                doc = documentDao.addDocument(doc);

                dataSourceId = getFileDatasourceId(fileExt);

                if(dataSourceId != null) {
                    datalayer.setCreated(new Date());
                    datalayer.getDatalayersource().setCreated(new Date());
                    datalayer.getDatalayersource().setDatasourceid(dataSourceId);
                    datalayer.getDatalayersource().setRefreshrate(refreshRate);
                }

                String docFilename = doc.getFilename().toLowerCase();

                if(uploadedDataLayer = docFilename.endsWith(".kmz")) {
                    String subdir = docFilename.substring(0, docFilename.length() - 4);
                    Path kmzDir = Paths.get(filePath, subdir);
                    if(!Files.exists(kmzDir)) {
                        Files.createDirectory(kmzDir);
                    }

                    try(
                            FileInputStream fis = new FileInputStream(filePath + doc.getFilename());
                            ZipInputStream zipStream = new ZipInputStream(fis)
                    ) {
                        ZipEntry entry;

                        // Stream all KMZ entries into new files under this temp dir.
                        while((entry = zipStream.getNextEntry()) != null) {
                            if(entry.getSize() == 0) {
                                continue;
                            }

                            String entryName = entry.getName();
                            Path outPath = kmzDir.resolve(entryName);

                            if(entryName.toLowerCase().endsWith(".kml")) {
                                fileName = entryName;
                            }

                            if(entryName.contains("/")) {
                                Files.createDirectories(outPath.getParent());
                            }

                            try(
                                    OutputStream output = Files.newOutputStream(outPath)
                            ) {
                                // KML files may require some translation, to workaround broken input files.
                                if(fileName != null) {
                                    FileUtil.getInstance().copyKmlStream(zipStream, output);
                                }

                                // Just copy the content directly, without translation.
                                else {
                                    IOUtils.copy(zipStream, output);
                                }
                            }
                        }
                    } catch(IOException ex) {
                        log.error("Failed to unzip file", ex);
                        uploadedDataLayer = false;
                        FileUtils.deleteDirectory(kmzDir.toFile());
                    }

                    // Set the final file name of the data layer.
                    fileName = subdir + "/" + fileName;
                } else if(uploadedDataLayer = docFilename.endsWith(".gpx")) {
                    fileName = doc.getFilename();
                } else if(uploadedDataLayer = docFilename.endsWith(".json")) {
                    fileName = doc.getFilename();
                } else if(uploadedDataLayer = docFilename.endsWith(".geojson")) {
                    fileName = doc.getFilename();
                } else if(uploadedDataLayer = docFilename.endsWith(".kml")) {
                    fileName = doc.getFilename();
                } else if(uploadedDataLayer = doc.getFiletype().equalsIgnoreCase("georss")) {
                    fileName = doc.getFilename();  // GeoRSS feed URL
                }

            }

            if(uploadedDataLayer) {
                datalayer.getDatalayersource().setLayername(fileName);

                String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);

                if(orgId >= 0) {
                    datalayerDao.insertDatalayerOrg(datalayerId, orgId);
                }

                if(collabroomId >= 0) {
                    addCollabroomDatalayer(collabroomId, datalayerId);
                } else {
                    if(folderId == null || StringUtils.isBlank(folderId) ||
                            folderId.equalsIgnoreCase(NO_FOLDER_NAME)) {
                        folderId = this.getUploadFolderId(workspaceId);
                    }
                    if(folderId != null){
                        int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folderId);
                        datalayerDao.insertDataLayerFolder(folderId, datalayerId, nextFolderIndex);
                        newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folderId);
                        if(newDatalayerFolder != null){
                            datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
                            datalayerResponse.setMessage(Status.OK.getReasonPhrase());
                            datalayerResponse.setSuccess(true);
                            response = Response.ok(datalayerResponse).status(Status.OK).build();
                        }else{
                            datalayerResponse.setSuccess(false);
                            datalayerResponse.setMessage(FAILED_UPLOAD);
                            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                        }
                    }else{
                        datalayerResponse.setSuccess(false);
                        datalayerResponse.setMessage(FAILED_UPLOAD_MISSING_FOLDER);
                        response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                    }
                }
            } else {
                datalayerResponse.setSuccess(false);
                datalayerResponse.setMessage(FAILED_UPLOAD);
                response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            e.printStackTrace();
            log.error("Failed to insert data layer", e);
            datalayerResponse.setSuccess(false);
            datalayerResponse.setMessage(FAILED_TO_INSERT_DATALAYER);
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                notifyNewChange(newDatalayerFolder, workspaceId);
            } catch(IOException | AlreadyClosedException | TimeoutException e) {
                log.error("Failed to publish DatalayerService message event", e);
            }
        }

        return response;
    }

    @Override
    public Response postGeorssDataLayerDocument(int workspaceId, String fileExt, int userOrgId, int usersessionId,
                                                int refreshRate, String feedUrl, String displayName,
                                                String username, String folderId) {
        log.info("POST Georss DataLayer Document");
        log.info("fileExt: " + fileExt);
        log.info("Userssionid: " + usersessionId);
        log.info("refresh Rate: " + refreshRate);
        log.info("feed url: " + feedUrl);
        log.info("Display name: " + displayName);


        DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
        Response response = null;
        Datalayerfolder newDatalayerFolder = null;
        Datalayer datalayer = new Datalayer();
        datalayer.setDatalayersource(new Datalayersource());
        String dataSourceId = null;
        Document doc = null;
        Boolean uploadedDataLayer = false;
        Boolean isGeorss = false;
        String georssFeedUrl = null;
        String fileName = null;
        String filePath = null;
        Boolean valid = false;
        User user = null;
        int orgId = -1;
        int collabroomId = -1;

        try {

            user = userDao.getUser(username);
            Set<UserOrg> userOrgs = user.getUserorgs();
            Iterator<UserOrg> iter = userOrgs.iterator();

            while(iter.hasNext()) {

                UserOrg userOrg = (UserOrg) iter.next();

                if(userOrg.getUserorgid() == userOrgId &&
                        (userOrg.getSystemroleid() == SADisplayConstants.SUPER_ROLE_ID ||
                                userOrg.getSystemroleid() == SADisplayConstants.GIS_ROLE_ID ||
                                userOrg.getSystemroleid() == SADisplayConstants.ADMIN_ROLE_ID)) {
                    valid = true;
                }

            }

            if(!valid) {
                return getInvalidResponse();
            }

            datalayer.setDisplayname(displayName);
            datalayer.setUsersessionid(usersessionId);
            datalayer.setBaselayer(true);
            georssFeedUrl = feedUrl;

            if(fileExt.equalsIgnoreCase("georss") && georssFeedUrl != null && !georssFeedUrl.isEmpty() &&
                    displayName != null && !displayName.isEmpty()) {
                log.info("GeoRSS extension!");
                doc = getGeorssDocument(displayName, georssFeedUrl);
                log.info("Got GeoRSS document? " + (doc != null));
                dataSourceId = getGeorssDatasourceId(fileExt, feedUrl);
                log.info("Datasourceid: " + dataSourceId);
            }

            if(doc != null) {

                doc.setUsersessionid(datalayer.getUsersessionid());
                doc = documentDao.addDocument(doc);

                dataSourceId = getGeorssDatasourceId(fileExt, feedUrl);

                if(dataSourceId != null) {
                    datalayer.setCreated(new Date());
                    datalayer.getDatalayersource().setCreated(new Date());
                    datalayer.getDatalayersource().setDatasourceid(dataSourceId);
                    datalayer.getDatalayersource().setRefreshrate(refreshRate);
                }

                if(uploadedDataLayer = doc.getFiletype().equalsIgnoreCase("georss")) {
                    fileName = displayName; // instead of feed url
                }

            }

            if(uploadedDataLayer) {
                datalayer.getDatalayersource().setLayername(fileName);

                String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);

                if(orgId >= 0) {
                    datalayerDao.insertDatalayerOrg(datalayerId, orgId);
                }

                if(collabroomId >= 0) {
                    addCollabroomDatalayer(collabroomId, datalayerId);
                } else {
                    if(folderId == null || StringUtils.isBlank(folderId) ||
                            folderId.equalsIgnoreCase(NO_FOLDER_NAME)){
                        folderId = this.getUploadFolderId(workspaceId);
                    }
                    if(folderId != null) {
                        int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folderId);
                        datalayerDao.insertDataLayerFolder(folderId, datalayerId, nextFolderIndex);
                        newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folderId);
                        if(newDatalayerFolder != null) {
                            datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
                            datalayerResponse.setMessage(Status.OK.getReasonPhrase());
                            datalayerResponse.setSuccess(true);
                            response = Response.ok(datalayerResponse).status(Status.OK).build();
                        }
                    }else{
                        datalayerResponse.setSuccess(false);
                        datalayerResponse.setMessage(FAILED_UPLOAD_MISSING_FOLDER);
                        response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                    }
                }
            } else {
                datalayerResponse.setSuccess(false);
                datalayerResponse.setMessage(FAILED_UPLOAD);
                response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            e.printStackTrace();
            log.error("Failed to insert data layer", e);
            datalayerResponse.setSuccess(false);
            datalayerResponse.setMessage(FAILED_TO_INSERT_DATALAYER);
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if(Status.OK.getStatusCode() == response.getStatus()) {
            try {
                notifyNewChange(newDatalayerFolder, workspaceId);
            } catch(IOException | TimeoutException | AlreadyClosedException e) {
                log.error("Failed to publish DatalayerService message event", e);
            } catch(Exception e) {
                log.error("Failed to publish DatalayerService message event", e);
            }
        }

        return response;
    }

    public Response getToken(String url, String username, String password) {
        return Response.ok(this.requestToken(url, username, password)).status(Status.OK).build();
    }

    public Response getToken(String datasourceId) {
        List<Map<String, Object>> data = datalayerDao.getAuthentication(datasourceId);

        if(data.get(0) != null) {
            String internalUrl = (String) data.get(0).get(SADisplayConstants.INTERNAL_URL);
            String token = this.requestToken(internalUrl,
                    (String) data.get(0).get(SADisplayConstants.USER_NAME),
                    (String) data.get(0).get(SADisplayConstants.PASSWORD)
            );
            if(token != null) {
                return Response.ok(token).status(Status.OK).build();
            }
        }

        return Response.ok().status(Status.INTERNAL_SERVER_ERROR).build();
    }

    public Response addCollabroomDatalayer(int collabroomId, String datalayerId) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        Response response = null;
        CollabroomDatalayer datalayerCollabroom = null;

        try {

            datalayerCollabroom = datalayerDao.insertCollabRoomDatalayer(collabroomId, datalayerId);
            notifyNewCollabroom(datalayerCollabroom);

            datalayerResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(datalayerResponse).status(Status.OK).build();
        } catch(Exception ex) {
            log.error("Failed to add collabroom datalayer", ex);
            datalayerResponse.setMessage("failed");
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    public Response updateCollabroomDatalayer(CollabroomDatalayer collabroomDatalayer) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        Response response = null;

        if(datalayerDao.updateCollabroomDatalayer(collabroomDatalayer)) {
            datalayerResponse.setMessage(Status.OK.getReasonPhrase());
            response = Response.ok(datalayerResponse).status(Status.OK).build();
            try {
                this.notifyUpdateCollabroom(collabroomDatalayer);
            } catch(IOException ioe) {
                ioe.printStackTrace();
            } catch(TimeoutException toe) {
                toe.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        return response;
    }

    public Response deleteCollabroomDataLayer(ArrayList<CollabroomDatalayer> collabroomDatalayers) {
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
        Response response = null;
        boolean deletedCollabroomDatalayer = false;

        try {

            datalayerDao.deleteCollabRoomDatalayers(collabroomDatalayers);

            notifyDeleteCollabroom(collabroomDatalayers);
            datalayerResponse.setCount(collabroomDatalayers.size());
            datalayerResponse.setMessage(Status.OK.toString());
            response = Response.ok(datalayerResponse).status(Status.OK).build();

        } catch(Exception ex) {
            log.error("Failed to add collabroom datalayer", ex);
            datalayerResponse.setMessage("failed");
            response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }


        return response;
    }

    public Response deleteDatasource(String dataSourceId, String username){
        DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();

        if(!(userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) ||
                userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID))){
            datalayerResponse.setMessage(FAILED_PERMIISSION_DATASOURCE);
            return Response.ok(datalayerResponse).status(Status.FORBIDDEN).build();
        }
        if(dataSourceId != null) {
            int rowCount = datalayerDao.deleteDatasource(dataSourceId);
            if (rowCount > 0) {
                datalayerResponse.setCount(rowCount);
                datalayerResponse.setMessage(Status.OK.toString());
                return Response.ok(datalayerResponse).status(Status.OK).build();
            } else {
                datalayerResponse.setCount(rowCount);
                datalayerResponse.setMessage(DELETE_DATASOURCE_ERROR);
                return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        }else{
            datalayerResponse.setMessage(MISSING_DATASOURCE_ID_ERROR);
            return Response.ok(datalayerResponse).status(Status.BAD_REQUEST).build();
        }
    }

    private String requestToken(String internalUrl, String username, String password) {
        int index = internalUrl.indexOf("rest/services");
        if(index == -1) {
            index = internalUrl.indexOf("services");
        }

        if(index > -1) {
            StringBuffer url = new StringBuffer(internalUrl.substring(0, index));
            url.append("tokens/generateToken?");
            url.append("username=");
            url.append(username);
            url.append("&password=");
            url.append(password);
            url.append("&f=json");

            WebTarget target = jerseyClient.target(url.toString());
            Builder builder = target.request("json");
            return builder.get().readEntity(String.class);
        }

        return null;
    }

    private Document getDocument(Attachment attachment, Path directory) {

        Path path = FileUtil.getInstance().createFile(attachment, directory);

        Document doc = new Document();
        doc.setDisplayname(attachment.getContentDisposition().getParameter("filename"));
        doc.setFilename(path.getFileName().toString());
        doc.setFiletype(attachment.getContentType().toString());
        doc.setCreated(new Date());
        return doc;
    }

    private Document getGeorssDocument(String displayName, String feedUrl) {
        Document doc = new Document();
        doc.setDisplayname(displayName);
        doc.setFilename(feedUrl);
        doc.setFiletype("georss"); // application/xml
        doc.setCreated(new Date());
        return doc;
    }

    private String getMapserverDatasourceId() {
        if(mapserverPublicURL == null) {
            return null;
        }
        String wmsMapserverURL = mapserverPublicURL.concat("/wms");

        String datasourceId = datalayerDao.getDatasourceId(wmsMapserverURL);
        if(datasourceId == null) {
            int datasourcetypeid = datalayerDao.getDatasourceTypeId("wms");
            if(datasourcetypeid != -1) {
                Datasource ds = new Datasource();
                ds.setInternalurl(wmsMapserverURL);
                ds.setDatasourcetypeid(datasourcetypeid);
                ds.setDisplayname("NICS WMS Server");
                datasourceId = datalayerDao.insertDataSource(ds);
            }
        }
        return datasourceId;
    }

    private String getFileDatasourceId(String fileExt) {
        if(webserverURL == null) {
            return null;
        }
        String webServerURL = webserverURL.concat("/" + fileExt + "/");

        String datasourceId = datalayerDao.getDatasourceId(webServerURL);
        if(datasourceId == null) {
            int datasourcetypeid = datalayerDao.getDatasourceTypeId(fileExt);
            if(datasourcetypeid != -1) {
                Datasource ds = new Datasource();
                ds.setInternalurl(webServerURL);
                ds.setDatasourcetypeid(datasourcetypeid);
                datasourceId = datalayerDao.insertDataSource(ds);
            }
        }
        return datasourceId;
    }

    private String getGeorssDatasourceId(String fileExt, String feedUrl) {
        if(feedUrl == null) {
            return null;

        }

        String datasourceId = datalayerDao.getDatasourceId(feedUrl);
        if(datasourceId == null) {
            int datasourcetypeid = datalayerDao.getDatasourceTypeId(fileExt);
            if(datasourcetypeid != -1) {
                Datasource ds = new Datasource();
                ds.setInternalurl(feedUrl);
                ds.setDatasourcetypeid(datasourcetypeid);
                datasourceId = datalayerDao.insertDataSource(ds);
            }
        }
        return datasourceId;
    }

    private GeoServer getGeoServer(Configuration config) {
        String geoserverUrl = config.getString(APIConfig.EXPORT_MAPSERVER_URL);
        if(geoserverUrl == null) {
            log.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_URL);
        }

        String geoserverUsername = config.getString(APIConfig.EXPORT_MAPSERVER_USERNAME);
        if(geoserverUsername == null) {
            log.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_USERNAME);
        }

        String geoserverPassword = config.getString(APIConfig.EXPORT_MAPSERVER_PASSWORD);
        if(geoserverPassword == null) {
            log.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_PASSWORD);
        }

        return new GeoServer(geoserverUrl + APIConfig.EXPORT_REST_URL, geoserverUsername, geoserverPassword);
    }

    private void notifyNewChange(Datalayerfolder datalayerfolder, int workspaceId)
            throws IOException, AlreadyClosedException,
            TimeoutException {
        if(datalayerfolder != null) {
            String topic = String.format("iweb.NICS.%s.datalayer.new", workspaceId);
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(datalayerfolder);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyDeleteChange(String dataSourceId)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(dataSourceId != null) {
            String topic = String.format("iweb.NICS.datalayer.delete");
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(dataSourceId);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyUpdateChange(Datalayer datalayer)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(datalayer != null) {
            String topic = String.format("iweb.NICS.datalayer.update");
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(datalayer);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyNewCollabroom(CollabroomDatalayer collabroomDatalayer)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(collabroomDatalayer != null) {
            String topic = String.format("iweb.NICS.collabroom.%d.datalayer.new",
                    collabroomDatalayer.getCollabroomid());
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(collabroomDatalayer);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyDeleteCollabroom(ArrayList<CollabroomDatalayer> collabroomDatalayers)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(collabroomDatalayers != null) {
            String topic = String.format("iweb.NICS.collabroom.%d.datalayer.delete",
                    collabroomDatalayers.get(0).getCollabroomid());
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(collabroomDatalayers);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyUpdateCollabroom(CollabroomDatalayer collabroomDatalayer)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(collabroomDatalayer != null) {
            String topic = String.format("iweb.NICS.collabroom.%d.datalayer.update",
                    collabroomDatalayer.getCollabroomid());
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(collabroomDatalayer);
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

    private Response getInvalidResponse() {
        return Response.status(Status.BAD_REQUEST).entity(
                Status.FORBIDDEN.getReasonPhrase()).build();
    }

    private String getUploadFolderId(int workspaceId){
        Folder folder = folderDao.getFolderByName(SADisplayConstants.UPLOAD_FOLDER, workspaceId);
        if(folder != null){
           return folder.getFolderid();
        }
        return null;
    }

    private String getTrackingFolderId(int workspaceId){
        Folder folder = folderDao.getFolderByName(SADisplayConstants.TRACKING_FOLDER, workspaceId);
        if(folder != null){
            return folder.getFolderid();
        }
        return null;
    }

}
