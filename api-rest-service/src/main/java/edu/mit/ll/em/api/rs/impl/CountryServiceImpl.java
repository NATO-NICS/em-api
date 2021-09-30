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

import edu.mit.ll.em.api.rs.CountryServiceResponse;
import edu.mit.ll.em.api.rs.CountryService;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.nics.common.entity.Region;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.impl.CountryDAOImpl;

import java.io.IOException;
import java.util.Arrays;
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
public class CountryServiceImpl implements CountryService {

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
     * CountryDAO instance.
     */
    private final CountryDAOImpl countryDao = new CountryDAOImpl();


    /**
     * Retrieves Countries
     *
     * @return a Response with a Status indicating success or failure
     */
    @Override
    public Response getCountries() {
        CountryServiceResponse response = new CountryServiceResponse();
        try {
            response.setCountries(countryDao.getCountries());

            return Response.ok(response).status(Status.OK).build();
        } catch(Exception e) {
            LOG.error("Posting Region", e);
        }

        return Response.ok("Failed to return countries")
                .status(Status.PRECONDITION_FAILED).build();
    }

    /**
     * Persists Regions
     *
     * @param region region entity
     * @return a Response with a Status indicating success or failure
     */
    @Override
    public Response postRegion(Region region) {
        //TODO: Check to make sure user is a super user

        CountryServiceResponse response = new CountryServiceResponse();
        boolean success = false;
        if(region != null) {
            try {
                if(region.getRegionId() > 0) {
                    if(countryDao.updateRegion(region) == 1) {
                        success = true;
                    }
                } else {

                    int regionId = countryDao.addRegion(region);
                    region.setRegionId(regionId);

                    success = true;
                }

                if(success) {
                    this.notifyNewRegion(region);

                    response.setRegions(Arrays.asList(region));

                    response.setMessage(Status.OK.toString());

                    return Response.ok(response).status(Status.OK).build();
                }
            } catch(Exception e) {
                LOG.error("Posting Region", e);
            }
        }

        response.setMessage("Failed to add new region");

        return Response.ok(response)
                .status(Status.PRECONDITION_FAILED).build();
    }

    /**
     * Retrieves Regions
     *
     * @param countryId the country ID to retrieve regions from
     * @return a Response with a Status indicating success or failure
     */
    @Override
    public Response getRegions(int countryId) {
        if(countryId != -1) {
            CountryServiceResponse response = new CountryServiceResponse();
            try {
                response.setRegions(countryDao.getRegions(countryId));

                return Response.ok(response).status(Status.OK).build();
            } catch(Exception e) {
                LOG.error("Posting Region", e);
            }
        }

        return Response.ok("Failed: Region parameter is null")
                .status(Status.PRECONDITION_FAILED).build();
    }


    /**
     * Deletes Region.
     *
     * @param regionId the region to be deleted
     * @return a Response with a Status indicating sucess or failure
     */
    @Override
    public Response deleteRegion(int regionId, String username) {
        //TODO: Check to make sure user is a super user
        if(regionId != -1) {
            try {
                boolean success = countryDao.deleteRegion(regionId);

                this.notifyDeleteRegion(regionId);

                return Response.ok(success).status(Status.OK).build();
            } catch(Exception e) {
                LOG.error("Error Deleting Region", e);
            }
        }

        return Response.ok("Failed: Region parameter is null")
                .status(Status.PRECONDITION_FAILED).build();
    }

    /**
     * Publishes new region.
     *
     * @param region Region entity to publish
     * @throws IOException      when there's a problem converting the alert to JSON, or publishing the message
     * @throws TimeoutException when the connection times out attempting to send the message
     * @throws JSONException
     */
    private void notifyNewRegion(Region region) throws IOException,
            TimeoutException, AlreadyClosedException {
        ObjectMapper mapper = new ObjectMapper();
        String message = mapper.writeValueAsString(region);

        getRabbitProducer().produce("iweb.nics.country.region.new", message);
    }

    /**
     * Publishes deleted region.
     *
     * @param regionId Region id of removed region
     * @throws IOException      when there's a problem converting the alert to JSON, or publishing the message
     * @throws TimeoutException when the connection times out attempting to send the message
     * @throws JSONException
     */
    private void notifyDeleteRegion(int regionId) throws IOException,
            TimeoutException, AlreadyClosedException {
        ObjectMapper mapper = new ObjectMapper();
        String message = mapper.writeValueAsString(regionId);

        getRabbitProducer().produce("iweb.nics.country.region.delete", message);
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
