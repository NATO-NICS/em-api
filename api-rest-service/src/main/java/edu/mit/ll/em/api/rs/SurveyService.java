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
package edu.mit.ll.em.api.rs;


import edu.mit.ll.nics.common.entity.Survey;
import edu.mit.ll.nics.common.entity.SurveyResult;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("/survey")
public interface SurveyService {

    /**
     * Gets Survey with specified ID
     *
     * @param surveyId the id of the survey to get
     *
     * @return Response with statusCode and an entity representing the survey
     */
    @GET
    @Path("/{surveyId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurvey(@PathParam("surveyId") final int surveyId);

    /**
     * Gets Survey via mapped formtypename
     */
    @GET
    @Path("/formtypename/{formTypeName}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurveyByFormTypeName(@PathParam("formTypeName") final String formTypeName);

    /**
     * Get all surveys
     *
     * @return Response with statusCode and an array of entities representing surveys
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurveys(@QueryParam("metadata") boolean metadata);

    /**
     * Create a survey
     *
     * @param request the Survey to persist
     *
     * @return Response with statusCode and an entity reporting success or failure
     */
    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response createSurvey(final Survey request);

    /**
     * Updates the specified survey
     *
     * @param survey the Survey to update
     *
     * @return Response with statusCode and an entity reporting success or failure
     */
    @PUT
    @Path("/{surveyId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateSurvey(@PathParam("surveyId") final int surveyId, final Survey survey);

    /**
     * Delete the Survey with specified id
     *
     * @param surveyId
     *
     * @return Response with statusCode and an entity reporting success or failure
     */
    @DELETE
    @Path("/{surveyId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response removeSurvey(@PathParam("surveyId") final int surveyId);

    /**
     * Get SurveyResult with specified ID
     *
     * @param resultId the id of the surveyresult to get
     *
     * @return Response with statusCode and an entity representing the surveyresult if found
     */
    @GET
    @Path("/results/{resultId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurveyResult(@PathParam("resultId") final int resultId);

    /**
     * Get all SurveyResults
     *
     * @return Response with statusCode and an entity containing a list of survey results if found
     */
    @GET
    @Path("/results")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurveyResults();

    /**
     * Get SurveyResults for the specified Survey
     *
     * @param surveyId the survey ID to get survey results for
     *
     * @return Response with statusCode and an entity containing a list of survey results if found
     */
    @GET
    @Path("/results/survey/{surveyId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurveyResultsForSurvey(@PathParam("surveyId") final int surveyId);

    /**
     * Create a SurveyResult
     *
     * @param surveyResult the SurveyResult to persist
     *
     * @return Response with statusCode and and entity containing the newly created SurveyResult
     */
    @POST
    @Path("/results")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response createSurveyResult(final SurveyResult surveyResult);

    /**
     * Update SurveyResult
     *
     * @param surveyResult the SurveyResult to update
     *
     * @return Response with statusCode and and entity containing surveyResults if found
     */
    @PUT
    @Path("/results/{resultId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateSurveyResult(@PathParam("resultId") final int resultId, final SurveyResult surveyResult);


    /**
     * Delete the SurveyResult specified by the resultId
     *
     * @param resultId the id of the surveyresult to delete
     *
     * @return Response with statusCode and and entity with information on success or failure
     */
    @DELETE
    @Path("/results/{resultId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response removeSurveyResult(@PathParam("resultId") final int resultId);

    /**
     * Gets Survey version with specified ID
     *
     * @param surveyHistoryId the id of the surveyhistory to get
     *
     * @return Response with statusCode and an entity representing the surveyhistory
     */
    @GET
    @Path("/history/{surveyHistoryId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurveyHistoryById(@PathParam("surveyHistoryId") final long surveyHistoryId);

    /**
     * Gets Survey versions for specified survey ID
     *
     * @param surveyId the id of the survey to get histories for
     *
     * @return Response with statusCode and a collection of surveys if found
     */
    @GET
    @Path("/history/survey/{surveyId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSurveyHistoriesForSurvey(@PathParam("surveyId") final int surveyId);

    /**
     * Delete the Survey History specified by the Survey History ID
     *
     * @param surveyHistoryId the id of the surveyhistory to delete
     *
     * @return Response with statusCode and and entity with information on success or failure
     */
    @DELETE
    @Path("/history/{surveyHistoryId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response removeSurveyHistory(@PathParam("surveyHistoryId") final int surveyHistoryId);

    /**
     * Delete all Survey History for the Survey specified
     *
     * @param surveyId the id of the survey to delete survey history for
     *
     * @return Response with statusCode and and entity with information on success or failure
     */
    @DELETE
    @Path("/history/survey/{surveyId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response removeAllHistoryForSurvey(@PathParam("surveyId") final int surveyId);
}
