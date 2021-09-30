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
package edu.mit.ll.em.api.util;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;


public class FileUtil {

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

    public static String getFileExtension(String filename) {
        if(filename != null) {
            int idx = filename.lastIndexOf(".");
            if(idx >= 0) {
                return filename.substring(idx);
            }
        }
        return null;
    }

    public static void deleteRecursively(Path path) throws IOException {
        if(!Files.isDirectory(path)) {
            Files.delete(path);
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }

        });
    }

    public Path createFile(Attachment attachment, Path directory) {
        return createFile(attachment, directory, null);
    }

    public Path createFile(Attachment attachment, Path directory, String prefix) {
        Path tempPath = null, path = null;
        try {
            Files.createDirectories(directory);

            tempPath = Files.createTempFile(directory, prefix, null);
            byte[] digest = writeAttachmentWithDigest(attachment, tempPath, "MD5");

            String filename = new BigInteger(1, digest).toString();
            String ext = this.getFileExtension(attachment);
            if(ext != null) {
                filename += "." + ext;
            }
            path = directory.resolve(filename);
            path = Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);

            // Set proper file permissions on this file.
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_READ
            ));
        } catch(IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        } finally {
            //cleanup files
            if(tempPath != null) {
                File file = tempPath.toFile();
                if(file.exists()) {
                    file.delete();
                }
            }
        }
        return path;
    }

    public String getFileExtension(Attachment attachment) {
        String filename = attachment.getContentDisposition().getParameter("filename");

        int idx = filename.lastIndexOf(".");
        if(idx != -1) {
            return filename.substring(idx + 1);
        }
        return null;
    }

    public byte[] writeAttachmentWithDigest(Attachment attachment, Path path, String digestAlgorithm)
            throws IOException, NoSuchAlgorithmException {

        try(
                InputStream is = attachment.getDataHandler().getInputStream();
        ) {
            MessageDigest md = MessageDigest.getInstance(digestAlgorithm);

            String ext = getFileExtension(attachment);
            if("kml".equalsIgnoreCase(ext)) {
                try(
                        OutputStream os = Files.newOutputStream(path);
                        DigestOutputStream dos = new DigestOutputStream(os, md)
                ) {
                    copyKmlStream(is, dos);
                }
            } else {
                try(
                        DigestInputStream dis = new DigestInputStream(is, md)
                ) {
                    Files.copy(dis, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            return md.digest();
        }
    }

    /**
     * Utility method for copying (and possibly translating) a KML input stream to an output stream.
     */
    public void copyKmlStream(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[4096];
        int n;

        // Convert the first (maximum of) 4096 bytes to a string.
        if(-1 == (n = input.read(buffer))) {
            return;
        }
        String prologue = new String(buffer, 0, n, "UTF-8");

        // Attempt to repair the document prologue, if a root <kml> tag is missing.
        Matcher matcher = MALFORMED_KML_PATTERN.matcher(prologue);
        if(matcher.find()) {
            int insertionPoint = matcher.end() - 10; // Insertion point, before <Document> tag.

            IOUtils.write(prologue.substring(0, insertionPoint), output);
            IOUtils.write(KML_ROOT_START_TAG, output);
            IOUtils.write(prologue.substring(insertionPoint), output);
        }

        // Otherwise, simply write out the byte buffer and signal that no epilogue is needed.
        else {
            output.write(buffer, 0, n);
            prologue = null;
        }

        // Write out the rest of the stream.
        IOUtils.copy(input, output);

        // If an epilogue is needed, write it now.
        if(prologue != null) {
            IOUtils.write("</kml>", output);
        }
    }

    public static JSONObject parseJSONFromFile(final String filepath) throws JSONException, IOException {
        String data = new String(Files.readAllBytes(Paths.get(filepath)));
        return new JSONObject(data);
    }

    // Lazy-initialization Holder class idiom.
    private static class Holder {
        public static FileUtil instance = new FileUtil();
    }

    public static FileUtil getInstance() {
        return FileUtil.Holder.instance;
    }

    protected FileUtil() {
    }
}
