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

import edu.mit.ll.em.api.rs.AlertService;
import edu.mit.ll.em.api.rs.AlertServiceResponse;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.entity.Alert;
import edu.mit.ll.nics.common.entity.AlertUser;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.impl.AlertDAOImpl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.rabbitmq.client.AlreadyClosedException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Service for publishing and retrieving alerts.
 */
public class AlertServiceImpl implements AlertService {

    /**
     * Success text for use in responses.
     */
    private static String SUCCESS = "success";

    /**
     * Logger instance.
     */
    private static final Logger LOG = LoggerFactory.getLogger(AlertServiceImpl.class);

    /**
     * Rabbit Producer for publishing alerts.
     */
    private RabbitPubSubProducer rabbitProducer;

    /**
     * AlertDAO instance.
     */
    private final AlertDAOImpl alertDao = new AlertDAOImpl();


    /**
     * {@inheritDoc}
     *
     * @param alert    the alert to persist
     * @param username the username of the user posting the alert
     * @return a Response object with a status of OK if successful, and PRECONDITION_FAILED otherwise
     */
    @Override
    public Response postAlert(Alert alert, String username) {
        if(alert != null) {
            try {
                return Response.ok(alertDao.persistAlert(alert)).status(Status.OK).build();
            } catch(Exception e) {
                LOG.error("postAlert", e);
            }
        }

        return Response.ok("Failed: Alert parameter is null").status(Status.PRECONDITION_FAILED).build();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Persists a UserAlert and publishes the alert via rabbitmq. If the userid is not set(-1), the alert is published
     * to the alert topic without the userid discriminator. Otherwise, both the userid and the the incidentid are used
     * in the topic.
     *
     * @param alertUser the alert to post
     * @param username  the username of the user that made the request
     * @return a Response object with a status of OK if successful, INTERNAL_SERVER_ERROR otherwise
     */
    @Override
    public Response postUserAlert(AlertUser alertUser, String username) {
        try {
            int alertId = alertUser.getAlertid();
            int incidentId = alertUser.getIncidentid();
            int userId = alertUser.getUserid();

            if(this.alertDao.persistUserAlert(alertId, incidentId, userId)) {
                String topic;
                if(userId == -1) {
                    topic = String.format("iweb.NICS.%s.alert", incidentId);
                } else {
                    topic = String.format("iweb.NICS.%s.%s.alert", incidentId, userId);
                }

                this.notifyAlert(this.alertDao.getAlert(alertId), topic);
                return Response.ok(SUCCESS).status(Status.OK).build();
            }
        } catch(Exception e) {
            LOG.error("postUserAlert", e);
        }

        return Response.ok("Failed to persist User Alert").status(Status.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * {@inheritDoc}
     *
     * @param alertId  the ID of the alert to delete
     * @param username the username of the user making the delete request
     * @return a Response object with Status OK if successful, INTERNAL_SERVER_ERROR otherwise
     */
    @Override
    public Response deleteAlert(int alertId, String username) {
        try {
            if(this.alertDao.delete(alertId) == 1) {
                return Response.ok(SUCCESS).status(Status.OK).build();
            }
        } catch(Exception e) {
            LOG.error("Unhandled exception deleting Alert with alertId {}, and username {}",
                    alertId, username, e);
        }
        return Response.ok("Failed to remove Alert").status(Status.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * {@inheritDoc}
     * <p/>
     *
     * @param incidentId the ID of the incident this alert is associated with
     * @param userId     the ID of the user associated with the alert
     * @param username   the username of the user making this request
     * @return a Response object with status OK if successful, INTERNAL_SERVER_ERROR otherwise
     */
    @Override
    public Response getAlerts(int incidentId, int userId, String username) {

        LOG.debug("Received getAlerts request: {}, {}, {}", incidentId,
                userId, username);

        AlertServiceResponse alertResponse = new AlertServiceResponse();
        List<Alert> alerts = null;
        try {
            alerts = alertDao.getAlerts(incidentId, userId);

            alertResponse.setMessage("Successfully retrieved alerts.");
            alertResponse.setResults(alerts);

            return Response.ok(alertResponse).status(Status.OK).build();

        } catch(Exception e) {
            return Response.ok("Failed to retrieve alerts: " + e.getMessage())
                    .status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Publishes alert to specified topic.
     *
     * @param alert Alert entity to publish
     * @param topic Topic to publish Alert entity to
     * @throws IOException      when there's a problem converting the alert to JSON, or publishing the message
     * @throws TimeoutException when the connection times out attempting to send the message
     * @throws JSONException
     */
    private void notifyAlert(Alert alert, String topic) throws IOException, TimeoutException, AlreadyClosedException {
        ObjectMapper mapper = new ObjectMapper();
        String message = mapper.writeValueAsString(alert);

        getRabbitProducer().produce(topic, message);
    }

    /**
     * Gets initialized Rabbit producer to send messages.
     *
     * @return an initialized rabbit producer
     *
     * @throws IOException      if there's an issue connecting to rabbitmq
     * @throws TimeoutException if there's an issue connecting to rabbitmq
     */
    private RabbitPubSubProducer getRabbitProducer() throws IOException, TimeoutException, AlreadyClosedException {
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
