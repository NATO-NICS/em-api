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
package edu.mit.ll.em.api.dataaccess;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ICSDatastoreException extends Exception implements Serializable {

    private static final long serialVersionUID = 6198179929211261881L;

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(ICSDatastoreException.class);

    public ICSDatastoreException(String msg) {
        super(msg);
    }

    public static void handle(String cname, String msg, Exception e) throws ICSDatastoreException {
        StringBuilder sb = new StringBuilder();
        if(msg != null) {
            sb.append(msg);
        }
        Throwable c = e.getCause();
        if(c != null) {
            String m = c.getMessage();
            if(m != null) {
                sb.append(" - CauseMsg: ").append(m);
            }
        }
        String m = e.getMessage();
        if(m != null) {
            sb.append(" - ExceptionMsg: ").append(m);
        }
        handle(cname, sb.toString());
    }

    public static void handle(String cname, String msg) throws ICSDatastoreException {
        log.error(msg);
        throw new ICSDatastoreException(msg);
    }

    public static void logIt(String cname, String msg, Exception e) {
        StringBuilder sb = new StringBuilder();
        if(msg != null) {
            sb.append(msg);
        }
        Throwable c = e.getCause();
        if(c != null) {
            String m = c.getMessage();
            if(m != null) {
                sb.append(" - CauseMsg: ").append(m);
            }
        }
        String m = e.getMessage();
        if(m != null) {
            sb.append(" - ExceptionMsg: ").append(m);
        }
        log.error(sb.toString(), e);
    }
}
