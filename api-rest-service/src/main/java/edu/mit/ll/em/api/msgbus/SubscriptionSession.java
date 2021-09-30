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
package edu.mit.ll.em.api.msgbus;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.ll.em.api.rs.MsgEnvelope;

public class SubscriptionSession {

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(SubscriptionSession.class);

    private long subscriberId = -1;

    private String[] topicList = null;

    private int timeout = -1;   // Never, by default.

    private MsgBusQueue msgbusQueue = new MsgBusQueue();

    public static SubscriptionSession create(String subscriberIdStr,
                                             String[] topicList, String timeoutStr) throws
            MsgBusSubscriptionException {
        long subscriberId = -1;
        try {
            subscriberId = Long.parseLong(subscriberIdStr);
        } catch(NumberFormatException e) {
            String msg = "Cannot create subscription due to subscriber ID value error: " +
                    e.getMessage();
            MsgBusSubscriptionException ex = new MsgBusSubscriptionException(msg);
            log.error(msg, ex);
            throw ex;
        }

        int timeoutSecs = 600;
        try {
            timeoutSecs = Integer.parseInt(timeoutStr);
        } catch(NumberFormatException e) {
            String msg = "Cannot create subscription due to timout value error: " +
                    e.getMessage();
            MsgBusSubscriptionException ex = new MsgBusSubscriptionException(msg);
            log.error(msg, ex);
            throw ex;
        }

        SubscriptionSession s = new SubscriptionSession();
        s.setSubscriberId(subscriberId);
        s.setTopicList(topicList);
        s.setTimeout(timeoutSecs);
        return s;
    }

    public long getSubscriberId() {
        return subscriberId;
    }

    public void setSubscriberId(long subscriberId) {
        this.subscriberId = subscriberId;
    }

    public String[] getTopicList() {
        return topicList;
    }

    public void setTopicList(String[] topicList) {
        this.topicList = topicList;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void terminate() {
        // TODO: Implementation.
        log.info("Subscription terminated: {}", this.getSubscriberId());
    }

    public void post(MsgEnvelope msg) {
        msgbusQueue.put(msg);
    }

    public void post(Collection<MsgEnvelope> msgs) {
        msgbusQueue.put(msgs);
    }

    public Collection<MsgEnvelope> get() {
        return msgbusQueue.get();
    }
}
