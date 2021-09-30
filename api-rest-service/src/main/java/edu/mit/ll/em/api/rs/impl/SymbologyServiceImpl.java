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

import edu.mit.ll.em.api.rs.SymbologyService;
import edu.mit.ll.em.api.rs.SymbologyServiceResponse;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.constants.SADisplayConstants;
import edu.mit.ll.nics.common.entity.OrgSymbology;
import edu.mit.ll.nics.common.entity.Symbology;
import edu.mit.ll.nics.nicsdao.impl.SymbologyDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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


/**
 * Symbology Service Implementation. Provides CRUD operations on the Symbology and related entities.
 */
public class SymbologyServiceImpl implements SymbologyService {

    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SymbologyServiceImpl.class);

    /**
     * Symbology DAO Instance.
     */
    private static final SymbologyDAOImpl symbologyDao = new SymbologyDAOImpl();

    /**
     * UserOrg DAO Instance for verifying user roles.
     */
    private static final UserOrgDAOImpl userorgDao = new UserOrgDAOImpl();

    /**
     * Text denoting a valid Symbology entity.
     */
    private static final String VALID = "valid";

    /**
     * Regular Expression pattern to match accepted default symbology file types .png and .svg.
     */
    private static final String fileExtPatternDefault = ".*[\\.svg|\\.png]$";

    /**
     * The root symbology path from the em-api configuration.
     */
    private static String rootSymbologyPath = null;


    @Override
    public Response getSymbology(final String owner, final String requestingUser) {
        Response response;
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();

        Map<String, Object> filter = new HashMap<>();
        if(owner != null) {
            filter.put("owner", owner);
        }

        try {
            List<Symbology> results = symbologyDao.getAllSymbology(filter);

            symbologyServiceResponse.setMessage("Successfully retrieved Symbologies");
            symbologyServiceResponse.setSymbologies(results);
            symbologyServiceResponse.setCount(results.size());

            response = Response.ok(symbologyServiceResponse).status(Status.OK).build();

        } catch(DataAccessException e) {
            LOG.error("Exception fetching Symbology: ", e);
            response = buildErrorResponse("Exception fetching symbology", e,
                    Status.INTERNAL_SERVER_ERROR);
        } catch(Exception e) {
            LOG.error("Unhandled exception fetching Symbology: ", e);
            response = buildErrorResponse("Unhandled exception fetching Symbology",
                    Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response getSymbologyById(final int symbologyId, final String requestingUser) {

        Response response;
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();
        Status status = Status.OK;

        try {
            Symbology result = symbologyDao.getSymbologyById(symbologyId);
            if(result != null) {
                symbologyServiceResponse.setMessage("Successfully retrieved Symbology");
                symbologyServiceResponse.setSymbologies(Arrays.asList(result));
                symbologyServiceResponse.setCount(1);
                response = Response.ok(symbologyServiceResponse).status(status).build();
            } else {
                symbologyServiceResponse.setMessage(String.format("No Symbology found with ID(%d)", symbologyId));
                response = Response.ok(symbologyServiceResponse).status(Status.NOT_FOUND).build();
            }

        } catch(DataAccessException e) {
            LOG.error("Exception fetching Symbology: ", e);
            response = buildErrorResponse("Exception fetching symbology", e,
                    Status.INTERNAL_SERVER_ERROR);

        } catch(Exception e) {
            LOG.error("Unhandled exception fetching Symbology: ", e);
            response = buildErrorResponse("Unhandled exception fetching Symbology",
                    Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response getSymbologyByOrgId(final int orgId, final String requestingUser) {
        Response response;
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();
        Status status = Status.OK;

        try {
            List<Symbology> results = symbologyDao.getSymbologyByOrgId(orgId);
            if(results != null && !results.isEmpty()) {
                symbologyServiceResponse.setMessage("Successfully retrieved Symbology for Organization");
                symbologyServiceResponse.setSymbologies(results);
                symbologyServiceResponse.setCount(results.size());
                response = Response.ok(symbologyServiceResponse).status(status).build();
            } else {
                symbologyServiceResponse.setMessage(String.format("No Symbology enabled for Org %d", orgId));
                response = Response.ok(symbologyServiceResponse).status(Status.NOT_FOUND).build();
            }

        } catch(DataAccessException e) {
            LOG.error("Exception fetching Symbology by Org ID: ", e);
            response = buildErrorResponse("Exception fetching symbology by Org ID", e,
                    Status.INTERNAL_SERVER_ERROR);
        } catch(Exception e) {
            LOG.error("Unhandled exception fetching Symbology by Org ID: ", e);
            response = buildErrorResponse("Unhandled exception fetching Symbology by Org ID",
                    Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    /**
     * Delete the directory created by this failed symbology upload
     *
     * @param symbologyExtractionPath
     *
     * @return true if successfully deleted symbology path, false otherwise
     */
    private boolean rollback(final Path symbologyExtractionPath) {
        boolean deleted = false;
        try {
            FileUtils.deleteDirectory(symbologyExtractionPath.toFile());
            deleted = true;
        } catch(IOException e) {
            LOG.error("Problem deleting failed symbology extraction path", e);
        }

        return deleted;
    }

    /**
     * Process the metadata.txt file to build the metadata listing for the symbology.
     *
     * @param parentPath root path the files are in, only used to add to metadata
     * @param metadataFile the full path to an extracted metadata.txt file to process
     * @param fileEntries a mapping of a given file name and a count, 1 if found, 0 if not
     *
     * @return JSONObject representing the metadata if successfully processed, null otherwise (if file not found,
     * or has no contents)
     *
     * // TODO: throw exception so caller can try building default if this fails?
     *
     */
    private JSONObject processMetadata(final String parentPath, final Path metadataFile,
                                       final Map<String, Integer> fileEntries, final boolean isUpdate) {

        if(metadataFile == null || Files.notExists(metadataFile)) {
            LOG.debug("Path parameter to metadata file was null, or does not exist: {}",
                    metadataFile == null ? null : metadataFile.getFileName());
                    // TODO: is getFileName() getting the absolute path?

            return null;
        }

        JSONArray jsonArray = new JSONArray();
        JSONObject tmpObj;
        JSONObject metadata = null;

        if(isUpdate) {
            // TODO: do extra metadata processing for when it's an update?
            //  whether or not to overwrite existing or not... fetch from db, or
            //  require flag for telling to overwrite or not
        }

        try {

            List<String> lines = Files.readAllLines(metadataFile);
            if(lines != null && !lines.isEmpty()) {

                String fname;
                for(String line : lines) {
                    tmpObj = new JSONObject();

                    String[] parts = line.split(",");
                    fname = "";
                    if(parts.length == 1) {
                        // Don't know if it's the filename, but making a guess
                        fname = parts[0].trim();
                        tmpObj.put("filename", fname);
                        tmpObj.put("desc", fname); // Since no description, use the filename
                    } else if(parts.length == 2){
                        fname = parts[1].trim();
                        tmpObj.put("filename", fname);

                        String desc = parts[0].trim();
                        tmpObj.put("desc", "".equals(desc) ? fname : desc);
                    }

                    LOG.debug("Looking for: {}", fname);
                    if(fileEntries.containsKey(fname)) {
                        LOG.debug("\tMarking file as found");
                        fileEntries.put(fname, 1);
                    } else {
                        LOG.debug("\tMarking {} as NOT found", fname);
                    }

                    jsonArray.put(tmpObj);
                }

                metadata = new JSONObject();
                metadata.put("parentPath", parentPath);
                metadata.put("listing", jsonArray);

                // TODO: May want to handle differently if it's an update so we don't overwrite
                //  any updates made through the UI, or other meanss
                validateAndAddMissingMetadata(fileEntries, metadata);

            } else {
                // no metadata in the file
                LOG.debug("No contents in the metadata file: {}", metadataFile.getFileName());
            }

        } catch(IOException e) {
            LOG.error("Error reading metadata file", e);
        }

        return metadata;
    }

    /**
     * Processes the specified metadata, and checks it against the specified fileEntries list. If an
     * entry in the list has a count of 0, a default filename and desc is given.The updated listing
     * overwrites the original if at least one entry was missing.
     *
     * @param fileEntries   all file entries in the zip, which may or may not match the metadata
     * @param metadata      the existing metadata
     *
     * @return true if all files have a matching metadata entry, false if 1 or more were missing
     */
    private boolean validateAndAddMissingMetadata(final Map<String, Integer> fileEntries, final JSONObject metadata) {

        // TODO: have this return the updated metadata, if changed, where we add a default for any
        //  missing ones

        LOG.debug("Metadata prior to validation:\n{}", metadata);

        int missing = 0;
        JSONArray listing = metadata.getJSONArray("listing");
        JSONObject missingEntry;
        for(Map.Entry<String, Integer> entry : fileEntries.entrySet()) {

            if(!"metadata.txt".equals(entry.getKey()) && entry.getValue() == 0) {
                missing++;
                // TODO: keep track of entries missing, and add defaults, and maybe warn user
                missingEntry = new JSONObject();
                missingEntry.put("filename", entry.getKey());
                missingEntry.put("desc", uppercaseFilenameNoExt(entry.getKey()));
                listing.put(missingEntry);
            }
        }

        if(missing > 0) {
            LOG.debug("File entries were missing, new listing is being written to metadata json...");

            // TODO: currently not doing anything with this metadata listing
            metadata.put("listing", listing);
        } else {
            LOG.debug("File entries were NOT missing, so listing remains unchanged.");
        }

        LOG.debug("Validated metadata:\n{}", metadata);

        return missing == 0;
    }

    /**
     * Utility method for converting a string to uppercase, replacing any File separators,
     * and removing the file extension.
     *
     * @param filename a filename, presumably ending in .[ext], where [ext] is the file extension
     *
     * @return the original string uppercased, path separators replaced, and any file extension removed,
     * or null if the given string is null or empty
     */
    private String uppercaseFilenameNoExt(String filename) {
        if(filename == null || filename.isEmpty()) {
            return null;
        }

        if(filename.contains(File.separator)) {
            // TODO: Don't want to just remove paths in case the same filename shows up in different paths
            //  make configurable property on how to handle/and which character?
            filename = filename.replace(File.separator, "-");
        }

        if(filename.contains(".")) {
            return filename.substring(0, filename.lastIndexOf(".")).toUpperCase();
        } else {
            return filename.toUpperCase();
        }
    }

    /**
     * Utility method to check if a filename is the metadata file to process. The check ignores case.
     *
     * @param filename
     *
     * @return true if entry is metadata file, false otherwise
     */
    private boolean isMetadataEntry(final String filename) {

        if(filename == null || !"metadata.txt".equalsIgnoreCase(filename)) {
            return false;
        }

        return true;
    }

    /**
     * Utility method to check if a zip file entry is ignorable. The 'metadata.txt' is ignorable, as well
     * as any files that aren't valid icon filetypes.
     *
     * @param filename filename to check for ignoring
     *
     * @return true if the filename is ignorable or given filename was null or empty, false otherwise
     */
    private boolean isIgnorableFile(final String filename) {
        boolean isIgnorable = false;

        if(filename == null || filename.isEmpty()) {
            return true;
        }

        if(isMetadataEntry(filename)) {
            isIgnorable = true;
        }

        final Pattern fileExtPattern = Pattern.compile(fileExtPatternDefault);
        Matcher fileExtMatcher;
        fileExtMatcher = fileExtPattern.matcher(filename);
        if(fileExtMatcher.matches()) {
            isIgnorable = false;
        }

        return isIgnorable;
    }

    /**
     * Builds a default JSON metadata based on just the file listing. The filename of the
     * symbol, without the file extension, is used as the Short Description field.
     *
     * @param entryListing a mapping where the keys are the file entries contained in the zip archive
     *
     * @return JSON representing the symbology metadata if successful, null otherwise
     */
    private JSONObject defaultMetadata(final String parentPath, final Map<String, Integer> entryListing,
                                       final boolean isUpdate) {

        if(isUpdate) {
            // TODO: do extra metadata processing for when it's an update?
            //  do what, fetch json from db and diff? Or require param to overwrite or not?
        }

        if(entryListing == null || entryListing.isEmpty()) {
            return null;
        }

        JSONArray jsonArray = new JSONArray();
        JSONObject tmpObj;
        for(final String entry : entryListing.keySet()) {

            if(isIgnorableFile(entry)) {
                LOG.debug("Ignoring file entry: {}", entry);
                continue;
            }

            tmpObj = new JSONObject();

            try {
                tmpObj.put("filename", entry);
                int startIdx = 0;
                if(entry.lastIndexOf(File.separator) >= 0) {
                    startIdx = entry.lastIndexOf(File.separator) + 1;
                }

                tmpObj.put("desc", entry.substring(startIdx, entry.lastIndexOf(".")).toUpperCase());

            } catch(Exception e) {
                // TODO: if we miss processing an entry, should we fail the whole upload?
                LOG.error("Exception processing filename {}", entry, e);
            }
            jsonArray.put(tmpObj);
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("parentPath", parentPath);
        jsonObject.put("listing", jsonArray);

        return jsonObject;
    }

    /**
     * Extracts the contents of the zip file, and processes metadata
     *
     * @param uploadedZipPath
     * @param symbologyExtractionPath
     *
     * @return JSON Metadata if successful, null otherwise
     *
     * @throws Exception when there was an issue extracting one or more entries from the zip archive
     */
    private JSONObject unzipAndProcessContents(final Path uploadedZipPath, final Path symbologyExtractionPath,
                                               final boolean isUpdate) throws Exception {

        boolean hasMetadata = false;
        Map<String, Integer> fileEntries = new HashMap<>();
        JSONObject jsonMetadata;
        Path metadataFile = null;

        String parentPath = uploadedZipPath.getFileName().toString();
        parentPath = parentPath.substring(0, parentPath.lastIndexOf("."));

        final Pattern fileExtPattern = Pattern.compile(fileExtPatternDefault);
        Matcher fileExtMatcher;

        try(
                FileInputStream fis = new FileInputStream(uploadedZipPath.toFile());
                ZipInputStream zipStream = new ZipInputStream(fis)
        ) {

            if(Files.notExists(symbologyExtractionPath)) {
                Files.createDirectory(symbologyExtractionPath);
            }

            ZipEntry entry;
            int entryFailures = 0;
            while((entry = zipStream.getNextEntry()) != null) {
                if(entry.getSize() == 0) {
                    continue;
                }

                String entryName = entry.getName();
                Path outPath = symbologyExtractionPath.resolve(entryName);

                // TODO: Allow them to update the short descriptions in the UI? Or add ability to
                //  post a metadata file or json to an existing symbology?
                if(isMetadataEntry(entryName)) {
                    hasMetadata = true;
                    metadataFile = outPath;
                } else {
                    fileExtMatcher = fileExtPattern.matcher(entryName.toLowerCase());
                    if(!fileExtMatcher.matches()) {
                        LOG.debug("Skipping unmatched file type: {}", entryName);
                        // TODO: track skip files for showing user
                        continue;
                    }
                }

                if(entryName.contains(File.separator)) {
                    Files.createDirectories(outPath.getParent());
                }

                try(OutputStream output = Files.newOutputStream(outPath)) {
                    IOUtils.copy(zipStream, output);
                    fileEntries.put(entryName, 0);
                } catch(IOException e) {
                    LOG.error("Failed to copy file from zip: {}", entry.getName(), e);
                    entryFailures++;
                }
            }

            if(entryFailures == 0) {

                if(hasMetadata) {
                    jsonMetadata = processMetadata(parentPath, metadataFile, fileEntries, isUpdate);
                    // TODO: if not thrown exception, try out null check...
                } else {
                    jsonMetadata = defaultMetadata(parentPath, fileEntries, isUpdate);
                }

                LOG.debug("Entry Listing:\n{}", Arrays.toString(fileEntries.keySet().toArray()));
            } else {
                LOG.debug("There were failures extracting all files in archive");
                throw new IOException("Exception extracting file from archive");
            }

        } catch(IOException e) {
            LOG.error("Failure unzipping file contents", e);
            if(!isUpdate) {
                rollback(symbologyExtractionPath);
            }

            // TODO: Ugly, but want to give more info on exception, not just give a null response, should
            //      make a custom Exception to return
            throw new Exception("Errors unzipping file contents.", e);
        }

        return jsonMetadata;
    }

    /**
     * Writes the uploaded file from the attachment to the root Symbology path specified by the system. If the zip file
     * already exists there, the existing one is backed up before writing out the new one.
     *
     * @param uploadedZipFilename the full path and filename of where to save the zip file
     * @param attachment          the attachment containing the uploaded zip file
     *
     * @throws IOException when there's a problem processing the file(s)
     */
    private void writeUploadedZipAndBackup(final String uploadedZipFilename, final Attachment attachment)
            throws IOException {

        Path uploadedZipPath;

        uploadedZipPath = Paths.get(uploadedZipFilename);

        if(Files.exists(uploadedZipPath)) {
            Files.copy(uploadedZipPath, Paths.get(String.format("%s.%d",
                    uploadedZipFilename, System.currentTimeMillis())));
        }

        InputStream is = attachment.getDataHandler().getInputStream();
        Files.copy(is, uploadedZipPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Processes a Symbology attachment, resulting in JSON metadata. Checks to see if the resulting
     * destination already exists. Calls {@link SymbologyServiceImpl#writeUploadedZipAndBackup(String, Attachment)}
     * to handle saving the archive, and managing backups. Finally calls
     * {@link SymbologyServiceImpl#unzipAndProcessContents(Path, Path, boolean)} to extract the files, and
     * build the metadata.
     *
     * @param symbology          the incoming symbology entity
     * @param attachment         the presumed zip file attachment with icons in it
     * @param attachmentFilename the filename of the zip archive that was uploaded
     * @param isUpdate           whether or not this is an update to an existing Symbology
     *
     * @return JSON with filename and description entries
     *
     * @throws Exception when there's a failure to save to disk, or when the Symbology path already exists
     */
    private JSONObject processAttachment(final Symbology symbology, final Attachment attachment,
                                         final String attachmentFilename, final boolean isUpdate)
            throws Exception {

        final String attachmentFilenameNoExt = attachmentFilename.substring(0,
                attachmentFilename.lastIndexOf("."));

        // The full path where we're going to extract the zip contents
        final String symbologyExtractionDir = String.format("%s%s%s%s",
                getRootSymbologyPath(),
                File.separator,
                attachmentFilenameNoExt,
                File.separator);

        final String uploadedZipFilename = String.format("%s%s%s",
                getRootSymbologyPath(),
                File.separator,
                attachmentFilename
        );

        Path symbologyExtractionPath = Paths.get(symbologyExtractionDir);
        if(!isUpdate && Files.exists(symbologyExtractionPath)) {
            throw new IOException(String.format("Symbology '%s' destination already exists",
                    symbology.getName()));
        }

        Path uploadedZipPath = Paths.get(uploadedZipFilename);
        try {
            writeUploadedZipAndBackup(uploadedZipFilename, attachment);
        } catch(IOException e) {
            throw new Exception("Writing attachment to disk failed. Cannot process further.", e);
        }

        // Extract zip to configured location, and build metadata
        return unzipAndProcessContents(uploadedZipPath, symbologyExtractionPath, isUpdate);
    }

    /**
     * Core logic for creating and updating symbology given a zip archive containing the symbology.
     *
     * @param name the short name of the Symbology
     * @param description a short description of the Symbology
     * @param owner the owner/point of contact for the Symbology. Must be an existing NICS user
     * @param attachment the zip archive containing the symbology
     * @param requestingUser the username of the user making the request
     * @param symbologyId the ID of the existing symbology to update, only applies when using to update
     *
     * @return Response containing the newly created/updated Symbology if successful, or appropriate error
     *          response otherwise
     */
    private Response createUpdateSymbology(final String name, final String description, final String owner,
                                           final Attachment attachment, final String requestingUser,
                                           final int symbologyId) {

        boolean isUpdate = symbologyId > 0;

        if(isNotAuthorized(requestingUser)) {
            return buildErrorResponse("Not authorized to create Symbology", Status.FORBIDDEN);
        }

        if(getRootSymbologyPath() == null) {
            return buildErrorResponse(String.format("%s%s%s%s%s",
                    "Symbology location not configured. ",
                    "Please contact your administrator. ",
                    "The '", APIConfig.SYMBOLOGY_PATH,
                    "' property must be set to use this feature."),
                    Status.INTERNAL_SERVER_ERROR);
        }

        if(isUpdate && !symbologyDao.exists(symbologyId)) {
            return buildErrorResponse("Specified Symbology does not exist. Not updating.",
                    Status.NOT_FOUND);
        }

        Symbology symbology = new Symbology(name, description, owner);
        if(isUpdate) {
            symbology.setSymbologyid(symbologyId);
        }

        final String validMessage = validateSymbology(symbology);
        if(!validMessage.equals(VALID)) {
            return buildErrorResponse(validMessage, Status.BAD_REQUEST);
        }

        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();
        String attachmentFilename;
        JSONObject processedMetadata;

        LOG.debug("Got file attachment for Symbology: {}",
                attachment.getContentType().toString());

        String filenameParameter = attachment.getContentDisposition().getParameter("filename");
        if(filenameParameter != null) {
            attachmentFilename = filenameParameter.toLowerCase();
        } else {
            return buildErrorResponse("The request is missing the required 'filename' parameter",
                    Status.BAD_REQUEST);
        }

        if((attachment.getContentType().toString().equals("application/zip")
                || attachment.getContentType().toString().equals("application/x-zip-compressed"))
                && attachmentFilename.endsWith(".zip")) {

            if(!isUpdate && symbologyPathExists(attachmentFilename.substring(0,
                    attachmentFilename.lastIndexOf(".")))) {

                return buildErrorResponse("Symbology with that filename already exists. " +
                        "Rename the archive and try again if this is a new Symbology. If you're" +
                        " trying to update an existing Symbology, please update instead.", Status.BAD_REQUEST);
            }

            LOG.debug("Got symbology filename: {}", attachmentFilename);

            try {
                // A null check on this return happens lower down, but is generic
                processedMetadata = processAttachment(symbology, attachment, attachmentFilename, isUpdate);
            } catch(Exception e) {
                LOG.error("Error processing Symbology attachment", e);
                return buildErrorResponse(e.getMessage(), Status.BAD_REQUEST);
            }

        } else {
            return buildErrorResponse("No zip attachment found.", Status.BAD_REQUEST);
        }

        if(processedMetadata != null) {

            Symbology newSymbology;
            try {
                symbology.setListing(processedMetadata.toString());
                LOG.info("Got metadata:\n{}", processedMetadata);

                final String successMessage = "Symbology " + (isUpdate ? "updated" : "created") + (" successfully");

                if(isUpdate) {
                    newSymbology = symbologyDao.updateSymbology(symbology);
                } else {
                    newSymbology = symbologyDao.createSymbology(symbology);
                }

                symbologyServiceResponse.setMessage(successMessage);
                symbologyServiceResponse.setSymbologies(Arrays.asList(newSymbology));
                symbologyServiceResponse.setCount(1);

                if(isUpdate) {
                    return Response.ok(symbologyServiceResponse).status(Status.OK).build();
                } else {
                    return Response.ok(symbologyServiceResponse).status(Status.CREATED).build();
                }

            } catch(DataAccessException e) {
                String message = "Exception " + (isUpdate ? "updating" : "creating") + " Symbology";
                LOG.error("{}: {}", message, e.getMessage(), e);

                rollback(getAbsoluteSymbologyPath( attachmentFilename.substring(0,
                        attachmentFilename.lastIndexOf("."))));

                return buildErrorResponse(message, e, Status.INTERNAL_SERVER_ERROR);
            }

        } else {
            LOG.error("Failed to process metadata of Symbology");
            rollback(getAbsoluteSymbologyPath( attachmentFilename.substring(0,
                    attachmentFilename.lastIndexOf("."))));

            return buildErrorResponse("Failed to process the Symbology archive.",
                    Status.INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public Response createSymbology(final String name, final String description, final String owner,
                                    final Attachment attachment, final String requestingUser) {

        return createUpdateSymbology(name, description, owner, attachment, requestingUser, -1);
    }

    @Override
    public Response updateSymbologyNoZip(final int symbologyId, final Symbology symbology,
                                         final String requestingUser) {

        final String validMessage = validateSymbology(symbology);
        if(!validMessage.equals(VALID)) {
            return buildErrorResponse(validMessage, Status.BAD_REQUEST);
        }

        if(symbologyId != symbology.getSymbologyid()) {
            return buildErrorResponse("Specified ID and ID of Symbology entity do not match",
                    Status.BAD_REQUEST);
        }

        if(!symbologyDao.exists(symbologyId)) {
            return buildErrorResponse("Symbology with that ID was not found",
                    Status.NOT_FOUND);
        }

        Response response;
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();

        try {
            Symbology result = symbologyDao.updateSymbology(symbology);
            if(result != null) {
                symbologyServiceResponse.setMessage("Successfully updated Symbology");
                symbologyServiceResponse.setSymbologies(Arrays.asList(result));
                symbologyServiceResponse.setCount(1);
                response = Response.ok(symbologyServiceResponse).status(Status.OK).build();
            } else {
                symbologyServiceResponse.setMessage("Failed to update Symbology");
                response = Response.ok(symbologyServiceResponse).status(Status.INTERNAL_SERVER_ERROR).build();
            }

        } catch(DataAccessException e) {
            LOG.error("Exception updating Symbology: ", e);
            response = buildErrorResponse("Exception updating symbology", e,
                    Status.INTERNAL_SERVER_ERROR);
        } catch(Exception e) {
            LOG.error("Unhandled exception updating Symbology: ", e);
            response = buildErrorResponse("Unhandled exception updating Symbology",
                    Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response updateSymbology(final int symbologyId, final String name, final String description, final String owner,
                                    final Attachment attachment, final String requestingUser) {

        return createUpdateSymbology(name, description, owner, attachment, requestingUser, symbologyId);
    }

    @Override
    public Response deleteSymbology(final int symbologyId, final String requestingUser) {

        Response response;
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();

        if(isNotSuper(requestingUser)) {
            return buildErrorResponse("Not authorized to delete Symbology", Status.FORBIDDEN);
        }

        try {
            // TODO: add an alternate markInactive endpoint since
            //  we don't want the icon directory deleted if they were used in incidents
            int result = symbologyDao.delete(symbologyId);
            if(result == 1) {
                symbologyServiceResponse.setMessage("Successfully deleted Symbology");
                response = Response.ok(symbologyServiceResponse).status(Status.OK).build();
            } else {
                symbologyServiceResponse.setMessage(String.format("Symbology with ID(%d) not found", symbologyId));
                response = Response.ok(symbologyServiceResponse).status(Status.NOT_FOUND).build();
            }

        } catch(DataAccessException e) {
            LOG.error("Exception deleting Symbology: ", e);
            response = buildErrorResponse("Exception deleting symbology", e,
                    Status.INTERNAL_SERVER_ERROR);
        } catch(Exception e) {
            LOG.error("Unhandled exception deleting Symbology: ", e);
            response = buildErrorResponse("Unhandled exception deleting Symbology",
                    Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response createOrgSymbology(final int orgId, final int symbologyId, final String requestingUser) {
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();
        Response response;

        if(isNotAuthorized(requestingUser)) {
            return buildErrorResponse("Not authorized to create Org Symbology mapping",
                    Status.FORBIDDEN);
        }

        try {
            int affected = symbologyDao.createOrgSymbology(orgId, symbologyId);
            if(affected == 1) {
                symbologyServiceResponse.setMessage("Org Symbology mapping created successfully.");
                response = Response.ok(symbologyServiceResponse).status(Status.CREATED).build();
            } else {
                symbologyServiceResponse.setMessage("Organization or Symbology not found. Mapping not created.");
                response = Response.ok(symbologyServiceResponse).status(Status.NOT_FOUND).build();
            }
        } catch(DataAccessException e) {
            LOG.error("Exception creating OrgSymbology mapping", e);
            response = buildErrorResponse("Problem creating OrgSymbology mapping", e,
                    Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response getOrgSymbologyMappings(final String requestingUser) {
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();
        Response response;

        try {
            List<OrgSymbology> mappings = symbologyDao.getOrgSymbologyMappings();
            symbologyServiceResponse.setOrgSymbologies(mappings);
            symbologyServiceResponse.setCount(mappings.size());
            symbologyServiceResponse.setMessage("Successfully retrieved Org Symbology mappings");
            response = Response.ok(symbologyServiceResponse).status(Status.OK).build();
        } catch(DataAccessException e) {
            LOG.error("Exception getting OrgSymbology mappings", e);
            response = buildErrorResponse("Problem getting OrgSymbology mappings",
                    e, Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response deleteOrgSymbology(final int orgId, final int symbologyId, final String requestingUser) {
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse();
        Response response;

        if(isNotAuthorized(requestingUser)) {
            return buildErrorResponse("Not authorized to delete Org Symbology mapping",
                    Status.FORBIDDEN);
        }

        try {
            int affected = symbologyDao.deleteOrgSymbology(orgId, symbologyId);
            if(affected == 1) {
                symbologyServiceResponse.setMessage("Org Symbology mapping deleted successfully.");
                response = Response.ok(symbologyServiceResponse).status(Status.OK).build();
            } else {
                symbologyServiceResponse.setMessage("Org Symbology mapping was not removed.");
                response = Response.ok(symbologyServiceResponse).status(Status.NOT_FOUND).build();
            }
        } catch(DataAccessException e) {
            LOG.error("Exception deleting OrgSymbology mapping", e);
            response = buildErrorResponse("Problem deleting OrgSymbology mapping", e,
                    Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    /**
     * Checks to see if the directory that would result in extracting this symbology already exists.
     *
     * @param name the filename of the incoming Symbology zip, minus the .zip extension
     *
     * @return true if the resulting directory exists already, false otherwise
     */
    private boolean symbologyPathExists(final String name) {
        return Files.exists(getAbsoluteSymbologyPath(name));
    }

    /**
     * Reads the APIConfig.SYMBOLOGY_PATH property, and stores it in the rootSymbologyPath member variable. If
     * it's not configured in em-api.properties, null is returned.
     *
     * @return the configured symbology path directory
     */
    private String getRootSymbologyPath() {
        if(rootSymbologyPath == null) {
            rootSymbologyPath = APIConfig.getInstance().getConfiguration()
                    .getString(APIConfig.SYMBOLOGY_PATH, null);
        }

        return rootSymbologyPath;
    }

    /**
     * Build the Symbology path given the name of the folder the Symbology was extracted to. The full path is the
     * configured root symbology folder, with the given subfolder appended.
     *
     * @param foldername the name of the Symbology subdirectory/directory name
     * @return Path object representing the full symbology path
     */
    private Path getAbsoluteSymbologyPath(final String foldername) {
        return Paths.get(String.format("%s%s%s",
                getRootSymbologyPath(), File.separator, foldername));
    }

    /**
     * Utility method for building error Response objects
     *
     * @param message the message to set
     * @param status  the HTTP status code to return
     *
     * @return a Response object populated with the information provided
     */
    private Response buildErrorResponse(final String message, final DataAccessException e, final Status status) {
        String nonNested;
        if(e != null) {
            nonNested = e.getMessage().contains("; nest") ? e.getMessage().substring(0,
                    e.getMessage().indexOf("; nest")) : e.getMessage();
        } else {
            nonNested = "";
        }
        final String fullMessage = String.format("%s%s", message, e == null ? "" : ": " + nonNested);
        SymbologyServiceResponse symbologyServiceResponse = new SymbologyServiceResponse(fullMessage);
        return Response.ok(symbologyServiceResponse).status(status).build();
    }

    /**
     * Utility method to call {@link SymbologyServiceImpl#buildErrorResponse(String,DataAccessException, Status)}
     * with a null Exception parameter.
     *
     * @param message message to send to buildErrorResponse
     * @param status status to send to buildErrorResponse
     *
     * @return the response from buildErrorResponse
     */
    private Response buildErrorResponse(final String message, final Status status) {
        return buildErrorResponse(message, null, status);
    }

    /**
     * Validates a Symbology entity by checking that the name, owner, and description fields are valid.
     *
     * @param symbology the symbology entity to validate
     *
     * @return {@link SymbologyServiceImpl#VALID} if the entity is valid, otherwise, a User Friendly message
     * detailing which fields failed and how
     */
    private String validateSymbology(final Symbology symbology) {
        StringBuilder message = new StringBuilder("Invalid Symbology: ");
        boolean failed = false;

        if(symbology == null) {
            message.append("Null Symbology entity");
            return message.toString();
        }

        if(symbology.getName() == null || symbology.getName().isEmpty()
                || symbology.getName().replaceAll(" ", "").isEmpty()) {

            message.append("Name cannot be empty, ");
            failed = true;
        }

        // TODO: validate name maybe in same way as Room names? Limit length/characters, etc
        //  get a regex to test, no spaces, no special characters that can't be used in a directory name

        if(symbology.getDescription() == null || symbology.getDescription().isEmpty()
                || symbology.getDescription().replaceAll(" ", "").isEmpty()) {

            message.append("Description cannot be empty, ");
            failed = true;
        }

        if(symbology.getOwner() == null || symbology.getOwner().isEmpty()
                || symbology.getOwner().replaceAll(" ", "").isEmpty()) {

            message.append("Owner cannot be empty, ");
            failed = true;
        }

        if(failed) {
            String result = message.toString();
            result = result.substring(0, result.length() - 2);
            return result;
        }

        return VALID;

    }

    /**
     * Utility method to check if user is an admin or super user
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
     * Utility method to check if a user is a super user
     *
     * @param username username of the user to check for the super role
     *
     * @return true if the user is NOT a superuser, false otherwise
     */
    private boolean isNotSuper(final String username) {
        return !userorgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID);
    }
}