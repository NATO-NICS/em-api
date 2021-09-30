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

import edu.mit.ll.nics.common.entity.MediaStream;
import org.testng.annotations.DataProvider;

public class MediaStreamServiceDataProvider {

    private static final long UNSET_MSID = -1;
    public static final String VALID_STREAM1_TITLE = "Stream 1";
    public static final String VALID_STREAM1_URL = "http://myteststream.io/stream1";

    public static final String VALID_STREAM1_TITLE2 = "Stream 2";
    public static final String VALID_STREAM1_URL2 = "http://myteststream.io/stream2";

    /**
     * Provides a valid {@link MediaStream} with values populated by the VALID_ constants. Not to be called directly,
     * but by setting the dataProvider attribute to "validMediaStreamProvider" on your test method's Test annotation,
     * e.g.:
     * <p>
     *
     * @return a valid, fully initialized MediaStream object
     *
     * @Test(dataProvider = "validMediaStreamProvider") public void testExample(MediaStream stream)
     * </p>
     */
    @DataProvider(name = "validMediaStreamProvider")
    public static Object[][] getValidMDT() {
        MediaStream stream = new MediaStream(UNSET_MSID, VALID_STREAM1_TITLE, VALID_STREAM1_URL);
        return new Object[][] {{stream}};
    }

    @DataProvider(name = "validMediaStreamProvider2")
    public static Object[][] getValidMDT2() {
        MediaStream stream = new MediaStream(UNSET_MSID, VALID_STREAM1_TITLE2, VALID_STREAM1_URL2);
        return new Object[][] {{stream}};
    }

}
