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
package edu.mit.ll.em.api.test.dataprovider;

import edu.mit.ll.em.api.rs.MDTrack;
import edu.mit.ll.nics.common.entity.MobileDeviceTrack;

import org.testng.annotations.DataProvider;

import java.sql.Timestamp;

/**
 * TestNG DataProvider for the MobileDeviceTrackService
 */
public class MDtrackServiceDataProvider {

    public static final String VALID_USERNAME = "tester@ll.mit.edu";
    public static final Double VALID_ALTITUDE = 57.5;
    public static final Integer VALID_WORKSPACEID = 1;
    public static final Timestamp VALID_TIMESTAMP = new Timestamp(System.currentTimeMillis());
    public static final String VALID_NAME = "EM-API Test";
    public static final Double VALID_LONGITUDE = -134.757472;
    public static final Double VALID_LATITUDE = 42.498242;
    public static final Double VALID_SPEED = 55.7;
    public static final String VALID_DEVICEID = "nicsdaotestdeviceid";
    public static final String VALID_DESCRIPTION = "This is a test description";
    public static final Double VALID_COURSE = 127.5;
    public static final Double VALID_ACCURACY = 0.59;
    public static final String VALID_EXTENDEDDATA = "{\"msg\":\"extended data\"}";

    /**
     * For use in Asserts on doubles, provide an allowable delta
     */
    public static final Double DOUBLE_DELTA = 0.0001;


    /**
     * Provides a valid {@link MobileDeviceTrack} with values populated by the VALID_ constants. Not to be called
     * directly, but by setting the dataProvider attribute to "validMdtProvider" on your test method's Test annotation,
     * e.g.:
     * <p>
     *
     * @return
     *
     * @Test(dataProvider = "validMdtProvider") public void testExample(MobileDeviceTrack mdt)
     * </p>
     */
    @DataProvider(name = "validMdtProvider")
    public static Object[][] getValidMDT() {

        MobileDeviceTrack mdt = new MobileDeviceTrack(VALID_DEVICEID, VALID_USERNAME, VALID_NAME, VALID_COURSE,
                VALID_SPEED, VALID_ALTITUDE, VALID_ACCURACY, VALID_TIMESTAMP, VALID_DESCRIPTION,
                VALID_EXTENDEDDATA, VALID_WORKSPACEID, VALID_LONGITUDE, VALID_LATITUDE);

        return new Object[][] {{mdt}};
    }


    /**
     * Provides a valid {@link MDTrack} with values populated by the VALID_ constants
     *
     * @return
     */
    @DataProvider(name = "legacyMdtProvider")
    public static Object[][] getLegacyMDT() {

        /*MobileDeviceTrack mdt = new MobileDeviceTrack(VALID_DEVICEID, VALID_USERNAME, VALID_NAME,VALID_COURSE,
                VALID_SPEED, VALID_ALTITUDE, VALID_ACCURACY, VALID_TIMESTAMP, VALID_DESCRIPTION,
                VALID_EXTENDEDDATA, VALID_WORKSPACEID, VALID_LONGITUDE, VALID_LATITUDE);*/

        MDTrack mdt = new MDTrack();
        mdt.setAccuracy(43.20000076293945);
        mdt.setAltitude(18.0);
        mdt.setCourse(0.0);
        mdt.setCreatedUTC(System.currentTimeMillis());
        mdt.setDeviceId("026061e030bf3601");
        mdt.setLatitude(40.8613318);
        mdt.setLongitude(-73.5288158);
        mdt.setSpeed(0.0);
        mdt.setUserId(1); // TODO: tough, since this needs to match the user used for openam authentication

        return new Object[][] {{mdt}};
    }

}
