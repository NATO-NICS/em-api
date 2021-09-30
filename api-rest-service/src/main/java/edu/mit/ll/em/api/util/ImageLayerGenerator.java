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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.mit.ll.em.api.util;

import org.locationtech.jts.geom.Envelope;

import edu.mit.ll.nics.common.geoserver.api.GeoServer;

/**
 * CollabFeedGeoserver extends the Geoserver class from the geoserver rest api. Compounds a variety of geoserver methods
 * for collab feed specific functions.
 *
 * @author LE22005
 */
public class ImageLayerGenerator extends GeoServer {

    private String workspaceName; //The Geoserver workspace to use, this should be unique per NICS server instancve
    private String dataStoreName; //The datastore used to store NICS incident and room information (the SACORE database)
    public static int SRID = 3857;
    public static String SRS_STRING = "EPSG:3857";
    //Initialize the max extent of the projection {minx maxx miny maxy}
    //Layer extents are hard coded to the extents of the USA so the bounds don't need be repeatedly updated
    public Envelope maxExtent = new Envelope(-14084454.868, -6624200.909, 1593579.354, 6338790.069);
    public Envelope maxExtentLatLon = new Envelope(-126.523, -59.506, 14.169, 49.375);

    /**
     * Constructor for CollabFeedGeoserver
     *
     * @param url           The web URL for the geoserver instance to connect to
     * @param username      for the Rest interface
     * @param password      for the Rest interface
     * @param workspaceName The Geoserver workspace to use, this should be unique per NICS server instancve
     * @param dataStoreName The datastore used to store NICS incident and room information (the SACORE database)
     */
    public ImageLayerGenerator(String url, String username, String password, String workspaceName,
                               String dataStoreName) {
        super(url, username, password);
        this.workspaceName = workspaceName;
        this.dataStoreName = dataStoreName;
    }

    /**
     * Add a SQL view layer of an incident view (polygons representing the bounds of the collab rooms) to the geoserver
     *
     * @param incidentName name of the incident to be added
     * @param incidentId   id of the incident to be added
     * @return
     */
    public boolean addImageLayer(String layerId, String title) {
        if(this.addFeatureTypeSQL(workspaceName, dataStoreName, layerId, SRS_STRING,
                "SELECT * from imagefeature where imageid='" + layerId + "'",
                "location", "Geometry", SRID)) {
            this.updateLayerStyle(layerId, workspaceName, "point");
            this.updateFeatureTypeTitle(layerId, workspaceName, dataStoreName, title);
            this.updateFeatureTypeBounds(workspaceName, dataStoreName, layerId, maxExtent, maxExtentLatLon, SRS_STRING);
            this.updateFeatureTypeEnabled(workspaceName, dataStoreName, layerId, true);
            this.updateLayerEnabled(layerId, workspaceName, true);
            return true;
        } else {
            return false;
        }
    }
}
