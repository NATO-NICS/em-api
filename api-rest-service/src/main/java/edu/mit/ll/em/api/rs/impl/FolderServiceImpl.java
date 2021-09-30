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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.rabbitmq.client.AlreadyClosedException;
import edu.mit.ll.em.api.util.FileUtil;
import edu.mit.ll.nics.common.entity.datalayer.*;
import edu.mit.ll.nics.nicsdao.DocumentDAO;
import edu.mit.ll.nics.nicsdao.impl.DocumentDAOImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.ll.em.api.rs.FolderDataServiceResponse;
import edu.mit.ll.em.api.rs.DatalayerDocumentServiceResponse;
import edu.mit.ll.em.api.rs.FolderService;
import edu.mit.ll.em.api.util.APIConfig;

import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;

import edu.mit.ll.nics.nicsdao.DatalayerDAO;
import edu.mit.ll.nics.nicsdao.FolderDAO;
import edu.mit.ll.nics.nicsdao.IncidentDAO;
import edu.mit.ll.nics.nicsdao.impl.DatalayerDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.FolderDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;

/**
 * @AUTHOR st23420
 */
public class FolderServiceImpl implements FolderService {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(FolderServiceImpl.class);

    private static final String ERROR_MESSAGE = "An unexpected error occurred while attempting to update the folder.";

    /**
     * Folder DAO
     */
    private static final FolderDAO folderDao = new FolderDAOImpl();

    /**
     * Datalayer DAO
     */
    private static final DatalayerDAO datalayerDao = new DatalayerDAOImpl();

    /**
     * Incident DAO
     */
    private static final IncidentDAO incidentDao = new IncidentDAOImpl();

    /**
     * Document DAO
     */

    private static final DocumentDAO documentDao = new DocumentDAOImpl();

    private RabbitPubSubProducer rabbitProducer;

    private static final Log logger = LogFactory.getLog(FolderServiceImpl.class);

    private final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();

    /**
     * Return Folder items
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response getChildFolders(int workspaceId, String folderId) {
        try {
            return getFolderData(folderId, workspaceId);
        } catch(Exception e) {
            log.error("Unhandled exception calling getFolderData with folderId {}, and workspaceId {}",
                    folderId, workspaceId, e);
        }
        //Send error message back
        return Response.ok(new FolderDataServiceResponse()).status(Status.OK).build();
    }

    /**
     * Return Folder items
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response getFolderData(int workspaceId, String folderName) {
        try {
            Rootfolder root = folderDao.getRootFolder(folderName, workspaceId);
            if(root != null) {
                return getFolderData(root.getFolderid(), workspaceId);
            }
        } catch(Exception e) {
            log.error("Unhandled exception getting folder data for folder name {}, and workspace id {}",
                    folderName, workspaceId, e);
        }

        //Send error message back
        return Response.ok(new FolderDataServiceResponse()).status(Status.OK).build();
    }

    /**
     * Return Folder items
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response getDocumentFolderData(int workspaceId, int orgId, int incidentId, int collabroomId) {
        List<DocumentFolder> response = new ArrayList();
        List<String> folderIds = new ArrayList();

        try {
            if(orgId > 0) {
                folderIds = documentDao.getOrgDocumentFolderIds(orgId, workspaceId);
            } else if(incidentId > 0) {
                folderIds = documentDao.getIncidentDocumentFolderIds(incidentId, workspaceId);
            } else if(collabroomId > 0) {
                folderIds = documentDao.getCollabroomDocumentFolderIds(collabroomId, workspaceId);
            }
            if(!folderIds.isEmpty()) {
                for(Iterator<String> itr = folderIds.iterator(); itr.hasNext(); ) {
                    String folderId = itr.next();
                    DocumentFolder folder = new DocumentFolder();
                    folder.setFolderId(folderId);
                    folder.setDocuments(documentDao.getDocuments(folderId));
                    response.add(folder);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
            log.error("Unhandled exception getting org folder data for folder name {}, and workspace id {}", e);
        }

        //Send error message back
        return Response.ok(response).status(Status.OK).build();
    }

    /**
     * Return Folder items
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response getArchivedFolderData(int workspaceId, int orgId) {
        try {
            //TODO: Configure the folder name
            Rootfolder root = folderDao.getRootFolder(
                    APIConfig.getInstance().getConfiguration().getString(APIConfig.ARCHIVED_ROOT_FOLDER),
                    workspaceId);
            if(root != null) {
                return getIncidentFolderData(workspaceId, root.getFolderid(), orgId);
            }
        } catch(Exception e) {
            log.error("Unhandled exception getting folder data for folder Archived, and workspace id {}",
                    workspaceId, e);
        }

        //Send error message back
        return Response.ok(new FolderDataServiceResponse()).status(Status.OK).build();
    }

    /**
     * Return Folder items
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response getIncidentFolderData(int workspaceId, String folderId, int orgId) {
        try {
            FolderDataServiceResponse folderResponse = new FolderDataServiceResponse();

            folderResponse.setIncidents(incidentDao.getActiveIncidents(workspaceId, orgId, false, folderId));
            folderResponse.setFolders(folderDao.getOrderedFolders(folderId, workspaceId));
            folderResponse.setRootId(folderId);

            return Response.ok(folderResponse).status(Status.OK).build();
        } catch(Exception e) {
            e.printStackTrace();
            log.error("Unhandled exception getting folder data for folder id {}, and workspace id {}",
                    folderId, workspaceId, e);
        }

        //Send error message back
        return Response.ok(new FolderDataServiceResponse()).status(Status.OK).build();
    }

    /**
     * Create new folder
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response postFolder(int workspaceId, Folder folder) {

        Response response;
        FolderDataServiceResponse folderResponse = new FolderDataServiceResponse();
        Folder newFolder = null;

        try {
            folder.setWorkspaceid(workspaceId);
            folder.setIndex(folderDao.getNextFolderIndex(folder.getParentfolderid()));
            newFolder = folderDao.createFolder(folder);

            if(newFolder != null) {

                folderResponse.getFolders().add(newFolder);
                folderResponse.setMessage(Status.OK.getReasonPhrase());
                folderResponse.setCount(folderResponse.getFolders().size());
                response = Response.ok(folderResponse).status(Status.OK).build();

                try {
                    String topic = String.format("iweb.NICS.%s.folder.new", workspaceId);
                    notifyFolder(newFolder, topic);

                } catch(Exception e) {
                    log.error("Failed to publish updating a folder message event", e);
                }

            } else {
                folderResponse.setMessage(Status.EXPECTATION_FAILED.getReasonPhrase());
                folderResponse.setCount(0);
                response = Response.ok(folderResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch(Exception e) {
            folderResponse.setMessage(ERROR_MESSAGE);
            response = Response.ok(folderResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Update existing folder
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response updateFolder(int workspaceId, Folder folder) {
        Response response;
        FolderDataServiceResponse folderResponse = new FolderDataServiceResponse();
        Folder updatedFolder = null;

        try {
            folder.setWorkspaceid(workspaceId);
            updatedFolder = folderDao.updateFolder(folder);

            if(updatedFolder != null) {

                folderResponse.getFolders().add(updatedFolder);
                folderResponse.setMessage(Status.OK.getReasonPhrase());
                folderResponse.setCount(folderResponse.getFolders().size());
                response = Response.ok(folderResponse).status(Status.OK).build();

                try {
                    String topic = String.format("iweb.NICS.%s.folder.update", workspaceId);
                    notifyFolder(folder, topic);

                } catch(Exception e) {
                    log.error("Failed to publish updating a folder message event", e);
                }
            } else {
                folderResponse.setMessage(ERROR_MESSAGE);
                response = Response.ok(folderResponse).status(Status.INTERNAL_SERVER_ERROR).build();
                return response;
            }

        } catch(Exception e) {
            log.error("Data access exception while updating Folder: {}",
                    folder.getFoldername(), e);
            folderResponse.setMessage(ERROR_MESSAGE);
            folderResponse.setCount(folderResponse.getFolders().size());
            response = Response.ok(folderResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    /**
     * Delete folder
     *
     * @return Response
     *
     * @see FolderResponse
     */
    @Override
    public Response deleteFolder(int workspaceId, String folderId) {


        Response response;
        FolderDataServiceResponse folderResponse = new FolderDataServiceResponse();
        boolean deletedFolder = false;

        if(folderId == null) {
            logger.error("Invalid folderId value: " + folderId);
            folderResponse.setMessage("Invalid folder id value.");
            response = Response.ok(folderResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            return response;
        }

        try {
            deletedFolder = folderDao.removeFolder(folderId);

            if(deletedFolder) {

                folderResponse.setMessage(Status.OK.getReasonPhrase());
                folderResponse.setCount(folderResponse.getFolders().size());
                response = Response.ok(folderResponse).status(Status.OK).build();

                try {
                    String topic = String.format("iweb.NICS.%s.folder.delete", workspaceId);
                    notifyFolder(folderId, topic);

                } catch(Exception e) {
                    log.error("Failed to publish updating a folder message event", e);
                }

            } else {
                folderResponse.setMessage(ERROR_MESSAGE);
                response = Response.ok(folderResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }

        } catch(Exception e) {
            log.error("Data access exception while deleting Folder with folderId: {}",
                    folderId, e);
            folderResponse.setMessage(ERROR_MESSAGE);
            folderResponse.setCount(folderResponse.getFolders().size());
            response = Response.ok(folderResponse).status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    @Override
    public Response moveFolder(int workspaceId, String parentFolderId, String folderId, Integer datalayerfolderId,
                               int index) {
        Response response;
        FolderDataServiceResponse folderResponse = new FolderDataServiceResponse();

        if(folderId != null && !folderId.isEmpty()) {
            Folder folder = folderDao.getFolder(folderId);
            //decrement all higher indexes from previous parent OR REORDER
            folderDao.decrementIndexes(folder.getParentfolderid(), folder.getIndex());

            //increment all higher/equal indexes in new parent
            folderDao.incrementIndexes(parentFolderId, index);

            //update parent folder id
            folder.setParentfolderid(parentFolderId);
            //update index
            folder.setIndex(index);
            folder = folderDao.updateFolder(folder);

            try {
                String topic = String.format("iweb.NICS.%s.folder.update", workspaceId);
                notifyFolder(folder, topic);

            } catch(Exception e) {
                log.error("Failed to publish updating a folder message event", e);
            }

            folderResponse.setCount(1);
            folderResponse.setFolders(Arrays.asList(folder));
            response = Response.ok(folderResponse).build();
        } else if(datalayerfolderId != null) {
            Datalayerfolder dlFolder = datalayerDao.getDatalayerfolder(datalayerfolderId);

            //decrement all higher indexes from previous parent
            datalayerDao.decrementIndexes(dlFolder.getFolderid(), dlFolder.getIndex());

            //make space for destination. increment all higher/equal indexes in new parent
            datalayerDao.incrementIndexes(parentFolderId, index);

            //make the move. update parent folder id, update index
            dlFolder.setFolderid(parentFolderId);
            dlFolder.setIndex(index);
            dlFolder = datalayerDao.updateDatalayerfolder(dlFolder);
            dlFolder = datalayerDao.getDatalayerfolder(
                    dlFolder.getDatalayerid(), dlFolder.getFolderid());

            try {
                String topic = String.format("iweb.NICS.%s.datalayer.update", workspaceId);
                notifyFolder(dlFolder, topic);

            } catch(Exception e) {
                log.error("Failed to publish updating a folder message event", e);
            }

            folderResponse.setCount(1);
            folderResponse.setDatalayerfolders(Arrays.asList(dlFolder));
            response = Response.ok(folderResponse).build();
        } else {
            folderResponse.setMessage(ERROR_MESSAGE);
            response = Response.ok(folderResponse).status(Status.BAD_REQUEST).build();
        }

        return response;
    }

    private String getDocumentUploadPath(int orgId, int incidentId, int collabroomId) {
        if(orgId > 0) {
            return APIConfig.getInstance().getConfiguration().getString(
                    APIConfig.ORG_DOCUMENT_UPLOAD_PATH);
        } else if(incidentId > 0) {
            return APIConfig.getInstance().getConfiguration().getString(
                    APIConfig.INCIDENT_DOCUMENT_UPLOAD_PATH);
        } else if(collabroomId > 0) {
            return APIConfig.getInstance().getConfiguration().getString(
                    APIConfig.COLLABROOM_DOCUMENT_UPLOAD_PATH);
        }
        return null;
    }

    public Response deleteIncidentDocument(String folderId, String incidentId, String requestingUser){
        DocumentFolder response = new DocumentFolder();

        if(!userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
            response.setMessage("User not authorized");
            return Response.ok(response).status(Status.UNAUTHORIZED).build();
        }

        try {
            int result = documentDao.deleteIncidentDocument(folderId);

            if (result > 0) {
                response.setFolderId(folderId);
                this.notifyFolderDocument(
                        response,
                        String.format("iweb.nics.incident.%s.delete.documents", incidentId));
                response.setMessage("Documents were successfully deleted.");
            }
        }catch(Exception e){
            e.printStackTrace();
            response.setMessage("There was an error deleting the document.");
        }

        return Response.ok(response).status(Status.OK).build();
    }

    public Response deleteOrgDocument(String folderId, String orgId, String requestingUser){
        DocumentFolder response = new DocumentFolder();

        if(!userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
            response.setMessage("User not authorized");
            return Response.ok(response).status(Status.UNAUTHORIZED).build();
        }

        try {
            int result = documentDao.deleteOrgDocument(folderId);

            if (result > 0) {
                response.setFolderId(folderId);
                this.notifyFolderDocument(
                        response,
                        String.format("iweb.nics.org.%s.delete.documents", orgId));
                response.setMessage("Documents were successfully deleted.");
            }
        }catch(Exception e){
            e.printStackTrace();
            response.setMessage("There was an error deleting the document.");
        }

        return Response.ok(response).status(Status.OK).build();
    }

    public Response deleteCollabroomDocument(String folderId, String collabroomId, String requestingUser){
        DocumentFolder response = new DocumentFolder();

        if(!userOrgDao.isUserRole(requestingUser, SADisplayConstants.SUPER_ROLE_ID)) {
            response.setMessage("User not authorized");
            return Response.ok(response).status(Status.UNAUTHORIZED).build();
        }

        try {
            int result = documentDao.deleteCollabroomDocument(folderId);

            if (result > 0) {
                response.setFolderId(folderId);
                this.notifyFolderDocument(
                        response,
                        String.format("iweb.nics.collabroom.%s.delete.documents", collabroomId));
                response.setMessage("Documents were successfully deleted.");
            }
        }catch(Exception e){
            e.printStackTrace();
            response.setMessage("There was an error deleting the document.");
        }

        return Response.ok(response).status(Status.OK).build();
    }

    private Response uploadDocument(int orgId,
                                    int incidentId,
                                    int collabroomId,
                                    String folderId,
                                    int usersessionId,
                                    String displayname,
                                    String description,
                                    MultipartBody body,
                                    boolean update) {

        DatalayerDocumentServiceResponse response =
                new DatalayerDocumentServiceResponse();

        try {
            List<Attachment> attachments = body.getAllAttachments();
            boolean found = false;
            for(Attachment attachment : attachments) {
                MediaType attachmentType = attachment.getContentType();
                //Add document to the file system
                if(!MediaType.TEXT_PLAIN_TYPE.isCompatible(attachmentType)) {
                    String ext = FileUtil.getInstance().getFileExtension(attachment);

                    Path path = FileUtil.getInstance().createFile(attachment,
                            Paths.get(this.getDocumentUploadPath(orgId, incidentId, collabroomId)));

                    //Add document to the database
                    if(path != null && folderId != null) {
                        Document doc = new Document();
                        doc.setFolderid(folderId);
                        doc.setDisplayname(displayname);
                        doc.setFilename(path.getFileName().toString());
                        doc.setFiletype(ext);
                        doc.setUsersessionid(usersessionId);
                        doc.setCreated(new Date());
                        doc.setDescription(description);

                        Document pDoc = documentDao.addDocument(doc);

                        String topic = null;
                        if(orgId > 0) {
                            documentDao.addOrgDocument(orgId, pDoc.getDocumentid());
                            topic = String.format("iweb.nics.org.%s.documents", orgId);
                        } else if(incidentId > 0) {
                            documentDao.addIncidentDocument(incidentId, pDoc.getDocumentid());
                            topic = String.format("iweb.nics.incident.%s.documents", incidentId);
                        } else if(collabroomId > 0) {
                            documentDao.addCollabroomDocument(collabroomId, pDoc.getDocumentid());
                            topic = String.format("iweb.nics.collabroom.%s.documents", collabroomId);
                        }

                        if(update){
                            documentDao.setDocumentDisplayname(pDoc.getFolderid(), pDoc.getDisplayname());
                            documentDao.setDocumentDescription(pDoc.getFolderid(), pDoc.getDescription());
                        }

                        DocumentFolder folderResponse = new DocumentFolder();
                        folderResponse.setFolderId(folderId);
                        folderResponse.setDocuments(Arrays.asList(doc));

                        if(topic != null) {
                            //Notify the UI of a new file
                            this.notifyFolderDocument(folderResponse, topic);
                        }
                        found = true;
                        response.setSuccess(true);
                    } else {
                        return Response.status(Status.BAD_REQUEST).entity("There was an error uploading the document.")
                                .build();
                    }
                }
            }

            if(!found) {
                return Response.status(Status.BAD_REQUEST).entity("No File Attachment Found").build();
            }
        } catch(Exception e) {
            response.setSuccess(false);
            e.printStackTrace();
        }

        return Response.ok(response).status(Status.OK).build();
    }

    public Response postDocumentFolder(int workspaceId, int orgId, int incidentId,
                                       int collabroomId, int usersessionId, String displayname,
                                       String description,
                                       MultipartBody body,
                                       String requestingUser) {
        try {
            //Create a new folder
            Folder folder = new Folder();
            folder.setFoldername(displayname);
            folder.setWorkspaceid(workspaceId);

            Folder documentFolder = folderDao.createFolder(folder);
            return this.uploadDocument(
                    orgId, incidentId, collabroomId, documentFolder.getFolderid(),
                    usersessionId, displayname, description, body, false);
        } catch(Exception e) {
            e.printStackTrace();
            return Response.status(Status.BAD_REQUEST).entity("There was an error creating the data folder.").build();
        }
    }

    public Response updateDocumentFolder(String folderId, int orgId, int incidentId,
                                         int collabroomId, int usersessionId, String displayname,
                                         String description,
                                         MultipartBody body,
                                         String requestingUser) {
        return this.uploadDocument(
                orgId, incidentId, collabroomId, folderId,
                usersessionId, displayname, description, body, true);

    }


    private Response getFolderData(String folderId, int workspaceId) {
        FolderDataServiceResponse folderResponse = new FolderDataServiceResponse();

        folderResponse.setRootId(folderId);
        folderResponse.setDatalayerfolders(datalayerDao.getDatalayerFolders(folderId));
        folderResponse.setFolders(folderDao.getOrderedFolders(folderId, workspaceId));

        return Response.ok(folderResponse).status(Status.OK).build();
    }

    private void notifyFolder(Object folder, String topic)
            throws IOException, TimeoutException, AlreadyClosedException {
        if(folder != null) {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(folder);
            getRabbitProducer().produce(topic, message);
        }
    }

    private void notifyFolderDocument(Object folderDocument, String topic)
            throws IOException, AlreadyClosedException, TimeoutException {
        if(folderDocument != null) {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(folderDocument);
            getRabbitProducer().produce(topic, message);
        }
    }

    /**
     * Get Rabbit producer to send message
     *
     * @return
     *
     * @throws IOException
     * @throws AlreadyClosedException
     */
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

