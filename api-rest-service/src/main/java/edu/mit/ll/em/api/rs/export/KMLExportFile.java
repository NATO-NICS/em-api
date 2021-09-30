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
package edu.mit.ll.em.api.rs.export;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.ll.em.api.util.APIConfig;

public class KMLExportFile extends DatalayerExportFile {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(KMLExportFile.class);

    private static String KML_ATTRIBS = "&bbox=-179,-89,179,89&layers=";
    private static final String KMZ_ICON_PATH_WITH_SLASH = "images/";

    private String mapserverURL;
    private String workspace;
    private String exportType;
    private String layername;
    private String kmlTemplate;
    private String kmlFilename;
    private String symbologyPath;
    private String symbologyHost;

    private Double longitude;
    private Double latitude;
    private boolean hasLatLon;

    public static String DYNAMIC = "dynamic";
    public static String STATIC = "static";

    private static final String DYNAMIC_KML_TEMPLATE_ERROR = "The dynamic kml template is not configured properly";

    /* KMLExport File
     * Represents the KML Export of a Collaboration Room (Static)
     */
    public KMLExportFile(String layername, String type, String mapserverURL, String workspace, String kmlFilename) {
        this(layername, type, mapserverURL, workspace, kmlFilename, null, null);
    }

    /* KMLExport File
     * Represents the KML Export of a Collaboration Room (Static)
     */
    public KMLExportFile(String layername, String type, String mapserverURL, String workspace, String kmlFilename,
                         Double latitude, Double longitude) {

        super(layername, false);

        this.mapserverURL = mapserverURL;
        this.workspace = workspace;
        this.exportType = type;
        this.layername = layername;
        this.kmlFilename = kmlFilename;
        this.symbologyPath = APIConfig.getInstance().getConfiguration().getString(APIConfig.SYMBOLOGY_PATH,
                "/opt/nics/static/symbology");

        symbologyHost = APIConfig.getInstance().getConfiguration().getString("em.api.service.symbology.hosts",
                null);
        if(symbologyHost == null) {
            // If symbology.hosts isn't set, try File Upload as a backup
            symbologyHost = APIConfig.getInstance().getConfiguration().getString(APIConfig.FILE_UPLOAD_URL,
                    null);
            symbologyHost = symbologyHost.substring(0, symbologyHost.indexOf("/upload"));
        }

        this.longitude = longitude;
        this.latitude = latitude;

        if(longitude != null && latitude != null) {
            hasLatLon = true;
        }
    }

    /**
     * Request the KML/KMZ file of the layer. If there are NICS hosted icons referenced, a KMZ
     * will be returned with relative icon paths, and icons packaged into the kmz. If no NICS
     * hosted icons are found, then just the kml file is returned as is.
     *
     * @return KMZ or KML file, depending on if there were icons to process
     */
    @Override
    public File getResponse() {
        File doc = null;
        File kmz = null;
        if(this.exportType.toLowerCase().equals(STATIC)) {
            StringBuffer url = new StringBuffer(mapserverURL);
            url.append(APIConfig.getInstance().getConfiguration().getString(APIConfig.KML_EXPORT_URL));
            url.append(KML_ATTRIBS);
            url.append(this.workspace);
            url.append(":");
            url.append(this.layername);

            doc = this.addFile(this.requestLayer(url.toString()), kmlFilename, KML);

            if(doc == null) {
                this.writeToTextFile("There was an error loading the requested KML document.");
                return this.getTextFile();
            }

            // Process
            kmz = process(doc);

            if(kmz == null) {
                return null;
            }

        }

        return kmz == null ? doc : kmz;
    }

    /**
     * Processes a line in the KML that geoserver generated. Expects one of the two lines:
     * <p>
     *     <ul>
     *         <li><longitude>LONVALUE</longitude></li>
     *         <li><latitude>LATVALUE</latitude></li>
     *     </ul>
     * </p>
     * Replaces the given values with {@link KMLExportFile#latitude} and {@link KMLExportFile#longitude}
     *
     * @param line the line from the KML containing the longitude or latitude element
     *
     * @return the line, with the value replaced with the current latitude or longitude if found,
     *      the original line is returned if pattern wasn't matched, and null is returned if the
     *      line parameter, longitude, or latitude are null
     */
    private String processKmlElementValue(String line, String element, String newValue) {
        final String OPEN = "<";
        final String CLOSE_OPENING = "<\\/";
        final String CLOSE = ">";
        final String strPattern = "([E0-9\\.]+)";

        if(latitude == null || longitude == null || line == null || line.isEmpty()) {
            return null;
        }

        String processedLine = null;

        Pattern pattern = Pattern.compile(String.format("%s%s%s%s%s%s%s",
                    OPEN,
                    element,
                    CLOSE,
                    strPattern,
                    CLOSE_OPENING,
                    element,
                    CLOSE
                ));

        Matcher matcher = pattern.matcher(line);

        if(matcher.find()) {
            String oval = matcher.group(1);
            processedLine = line.replace(oval, newValue);
        } else {
            processedLine = line;
        }

        return processedLine;
    }


    /**
     * Processes the KML document returned by Geoserver. It finds Icon references, and if they're hosted
     * by the configured NICS instance, they're downloaded for adding to a kmz to return, and the references
     * to them in the KML document are updated to be a local reference. If no icons are found, then just
     * the KML document is returned.
     *
     * @param kmlDocument the KML document returned from Geoserver representing the layer
     *
     * @return a KMZ file containing the updated kml document and any nics hosted icons, or if no icons found
     *         then just the original KML document
     */
    public File process(File kmlDocument) {
        log.debug("\nProcessing");

        // Get a tmp file for writing
        File kmlUpdated = new File(String.format("%s%s%s",
                getTempDirectory(), File.separator, "kmz" + kmlDocument.getName()));

        BufferedReader bufferedReader = null;
        FileReader fileReader = null;

        BufferedWriter bufferedWriter = null;
        FileWriter fileWriter = null;

        List<String> iconPaths = new ArrayList<>();

        try {
            fileReader = new FileReader(kmlDocument);
            bufferedReader = new BufferedReader(fileReader);

            fileWriter = new FileWriter(kmlUpdated);
            bufferedWriter = new BufferedWriter(fileWriter);

            String line = null;
            String tmpline = null;
            boolean lineHandled;
            while( (line = bufferedReader.readLine()) != null) {
                lineHandled = false;
                /*
                    <LookAt>
                        <longitude>180.0</longitude>
                        <latitude>0.0</latitude>
                        <altitude>1.5766377413365064E7</altitude>
                        <heading>0.0</heading>
                        <tilt>0.0</tilt>
                        <range>1.2740059922829097E7</range>
                        <altitudeMode>clampToGround</altitudeMode>
                    </LookAt>
                 */
                if(line.contains("LookAt")) {
                    lineHandled = true;
                    // Write <LookAt> line
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();

                    while(!line.contains("</LookAt>")) {
                        // TODO: use counter to implement a safety bail out after X line reads?

                        line = bufferedReader.readLine();
                        if(line.contains("<latitude>") && hasLatLon) {
                            String latLine = processKmlElementValue(line, "latitude", latitude+"");
                            if(latLine != null) {
                                bufferedWriter.write(latLine);
                                bufferedWriter.newLine();
                            } else {
                                bufferedWriter.write(line);
                                bufferedWriter.newLine();
                            }
                        } else if(line.contains("<longitude>") && hasLatLon) {

                            String lonLine = processKmlElementValue(line, "longitude", longitude+"");
                            if(lonLine != null) {
                                bufferedWriter.write(lonLine);
                                bufferedWriter.newLine();
                            } else {
                                bufferedWriter.write(line);
                                bufferedWriter.newLine();
                            }
                        } else if(line.contains("<altitude>")) {
                            // TODO: should be configurable
                            String altLine = processKmlElementValue(line, "altitude", "500");
                            if(altLine != null) {
                                bufferedWriter.write(altLine);
                                bufferedWriter.newLine();
                            } else {
                                bufferedWriter.write(line);
                                bufferedWriter.newLine();
                            }

                        } else if(line.contains("<range>")) {
                            // TODO: should be configurable
                            // Roughly puts LookAt viewpoint at roughly 500mi (800000m)
                            String rangeLine = processKmlElementValue(line, "range", "800000");
                            if(rangeLine != null) {
                                bufferedWriter.write(rangeLine);
                                bufferedWriter.newLine();
                            } else {
                                bufferedWriter.write(line);
                                bufferedWriter.newLine();
                            }

                        } else {
                            bufferedWriter.write(line);
                            bufferedWriter.newLine();
                        }
                    } // end LookAt

                }

                if(line.contains("<Icon>")) {
                    lineHandled = true;
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();

                    // Should be '<href>URL</href>', but KML output from GeoServer could change
                    line = bufferedReader.readLine();
                    tmpline = line.trim();

                    String url = tmpline.substring(6, tmpline.length() - 7);
                    log.debug("Found icon: " + url);

                    boolean nicshosted = false;

                    if(symbologyHost != null && url.contains(symbologyHost)) {
                        nicshosted = true;
                    }

                    if(nicshosted) {

                        String iconName;
                        if(url.contains("upload/symbology/")) {
                            iconName = url.substring(url.indexOf("upload/symbology/") + "upload/symbology/".length());
                        } else if(url.contains("geoserver/styles/")) {
                            iconName = url.substring(url.indexOf("geoserver/styles/") + "geoserver/styles/".length());
                        } else {
                            final String[] parts = url.split("/");
                            iconName = parts.length == 1 ? parts[0] : parts[parts.length - 1];
                        }

                        log.debug("Icon: " + KMZ_ICON_PATH_WITH_SLASH + iconName);

                        line = line.replace(url, KMZ_ICON_PATH_WITH_SLASH + iconName);
                        bufferedWriter.write(line);
                        bufferedWriter.newLine();

                        iconPaths.add(iconName);

                    } else {
                        // Leave be, it's a third-party icon that should be fetched from the net by the client
                        log.debug("\tIgnoring " + url);
                        bufferedWriter.write(line);
                        bufferedWriter.newLine();
                    }
                }

                if(!lineHandled) {
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();
                }
            }

        } catch(Exception e) {
            log.error("Error processing KML for icons: ", e);
        } finally {
            if(bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch(IOException e) {
                    log.error("Exception closing reader", e);
                }
            }

            if(bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch(IOException e) {
                    log.error("Exception closing writer: ", e);
                }
            }
        }

        List<File> iconFiles = new ArrayList<>();
        if(!iconPaths.isEmpty()) {
            iconFiles = processIcons(iconPaths);
            File kmz = buildKmz(kmlUpdated, iconFiles);

            return kmz;
        } else {
            return kmlUpdated;
        }
    }

    /**
     * Processes specified icons by building the absolute path and returning as a File.
     * They're presumed to be in the form:
     *
     * <p>"path/to/icon.ext"</p>
     *
     * Where the root path will be the configured symbology file location, usually "/opt/nics/static/symbology"
     *
     * @param icons a list of relative icon paths to build an absolute path to, e.g. "path/to/icon.png"     *
     *
     * @return a list of Files with absolute paths to the icon on disk
     */
    public List<File> processIcons(List<String> icons) {

        if(icons == null || icons.isEmpty()) {
            return new ArrayList<>();
        }

        String destIcon = null;

        List<File> iconFiles = new ArrayList<>();
		for(String icon : icons) {
            try {
                File destPath = new File(symbologyPath +
                        (symbologyPath.endsWith(File.separator) ? "" : File.separator) + icon);

                if(!destPath.exists()) {
                    log.warn("KML document contains reference to missing icon: {}", destPath.getAbsolutePath());
                    continue;
                }

                destIcon = destPath.getAbsolutePath();
                File destFile = new File(destIcon);

                if(destPath.exists()) {
                    iconFiles.add(destFile);
                }

            } catch(Exception e) {
                log.error("Exception copying icon to temp directory", e);
            }
        }

		return iconFiles;
    }

    /**
     * Builds a KMZ, assuming all icons have been downloaded and processed, and that the kml
     * file being passed in has been updated to local icon paths. This builds a kmz with
     * a single kml file in the root, and all icons in the images/ path, along with what ever
     * their relative path is.
     *
     * @param kml the kml document to place in the kmz
     * @param icons the icon files to add to the kmz to the icons/ directory
     *
     * @return a KMZ file for returning to the client if successful, null if there was a problem
     */
    private File buildKmz(File kml, List<File> icons) {

        try {
            String kmzFilename = kml.getName().substring(0, kml.getName().length() - 4);
            File zipFile = this.createTempFile(kmzFilename, KMZ);
            FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(fos);

            // Add KML
            ZipEntry kmlEntry = new ZipEntry(kml.getName());
            zos.putNextEntry(kmlEntry);
            int kmlEntryLen;
            byte[] kmlbuffer = new byte[1024];
            FileInputStream kmlEntryInputStream = new FileInputStream(kml);
            while((kmlEntryLen = kmlEntryInputStream.read(kmlbuffer)) > 0) {
                zos.write(kmlbuffer, 0, kmlEntryLen);
            }
            kmlEntryInputStream.close();

            // Add Icons
            for(File icon : icons) {

                byte[] buffer = new byte[1024];
                File f = icon;

                String internalDirectory = f.getAbsolutePath();
                internalDirectory = internalDirectory.replace(f.getName(), "");
                internalDirectory = internalDirectory.replace(symbologyPath, "");
                if(internalDirectory.startsWith(File.separator)) {
                    internalDirectory = internalDirectory.substring(1);
                }

                ZipEntry ze = new ZipEntry(String.format("%s%s%s", KMZ_ICON_PATH_WITH_SLASH,
                        internalDirectory, f.getName()));

                try {
                    zos.putNextEntry(ze);
                    FileInputStream in = new FileInputStream(f);

                    int len;
                    while((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }

                    in.close();
                } catch(ZipException e) {
                    log.error("Exception creating zip entry for icon {}", icon.getAbsolutePath(), e);
                }

            }
            zos.closeEntry();
            zos.close();

            return zipFile;

        } catch(IOException ex) {
            log.error("Exception getting zip file", ex);
        }

        return null;
    }

    /**
     * Build a KML document to dynamic layer information
     *
     * @param layername
     * @return KML
     */
    private File buildDynamicKML(String layername) {
        File file = null;
        String kml = null;

        if(this.kmlTemplate != null) {
            kml = this.kmlTemplate.replaceAll("WORKSPACENAME", this.workspace)
                    .replaceAll("LAYERNAME", layername)
                    .replaceAll("MAPSERVERURL", this.mapserverURL);
        } else {
            kml = DYNAMIC_KML_TEMPLATE_ERROR;
        }
        //Write either template or error to file
        try {
            file = this.createTempFile(layername, KML);
            BufferedWriter writer = null;

            writer = new BufferedWriter(new FileWriter(file));
            writer.write(kml);
            writer.close();
        } catch(IOException ex) {
            log.error("IOException while building KML for layername: {}", layername, ex);
            return null;
        }
        return file;
    }
}