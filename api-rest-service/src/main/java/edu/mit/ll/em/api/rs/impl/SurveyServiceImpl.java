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


import edu.mit.ll.em.api.rs.SurveyResponse;
import edu.mit.ll.em.api.rs.SurveyService;
import edu.mit.ll.nics.common.entity.Survey;
import edu.mit.ll.nics.common.entity.SurveyHistory;
import edu.mit.ll.nics.common.entity.SurveyResult;
import edu.mit.ll.nics.nicsdao.SurveyDAO;
import edu.mit.ll.nics.nicsdao.exceptions.SurveyHistoryException;
import edu.mit.ll.nics.nicsdao.impl.SurveyDAOImpl;
import org.apache.log4j.Logger;

import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status;

import java.util.List;


/**
 * SurveyService implementation. Manages Survey and SurveyResult actions.
 */
public class SurveyServiceImpl implements SurveyService {
    /** Logger */
    private static final Logger LOG = Logger.getLogger(SurveyServiceImpl.class);

    /** DAO */
    private SurveyDAO surveyDao = new SurveyDAOImpl();


    @Override
    public Response getSurvey(int surveyId) {
        Response response = null;
        Survey survey = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            survey = surveyDao.getById(surveyId);
            if (survey != null) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved survey");
                surveyResponse.getSurveys().add(survey);
                response = Response.ok(surveyResponse).status(Status.OK).build();
            } else {
                String msg = String.format("Survey with surveyid %d not found", surveyId);
                response = buildNotFoundResponse(msg);
            }
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting survey with surveyid %d: %s",
                    surveyId, e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response getSurveyByFormTypeName(String formTypeName) {
        Response response = null;
        Survey survey = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            survey = surveyDao.getByFormTypeName(formTypeName);
            if (survey != null) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved survey");
                surveyResponse.getSurveys().add(survey);
                response = Response.ok(surveyResponse).status(Status.OK).build();
            } else {
                String msg = String.format("Survey with associated formtypename %s not found", formTypeName);
                response = buildNotFoundResponse(msg);
            }
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting survey with associated formtypename %s: %s",
                    formTypeName, e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response getSurveys(boolean metadata) {

        Response response = null;
        List<Survey> surveys = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            surveys = surveyDao.getSurveys(metadata);
            if (surveys != null && !surveys.isEmpty()) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved surveys");
                surveyResponse.setSurveys(surveys);
                response = Response.ok(surveyResponse).build();
            } else if(surveys != null && surveys.isEmpty()) {
                String msg = "No survey results";
                surveyResponse.setMessage(msg);
                response = Response.ok(surveyResponse).build();
            } // TODO: ensure dao doesn't return a null
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting surveys: %s",
                    e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }


    @Override
    public Response createSurvey(Survey request) {
        Response response = null;
        SurveyResponse surveyResponse = null;
        Survey newSurvey = null;
        int newSurveyId;
        try {
            validateSurvey(request, false);
            newSurveyId = surveyDao.createSurvey(request);

            String msg;
            if(newSurveyId < 0) {
                switch(newSurveyId) {
                    case -1: // error
                        msg = "Error creating survey";
                        break;
                    case -2: // Title already exists
                        msg = "That survey title already exists. Try a different title.";
                        break;
                    default:
                        msg = "Unhandled response from server creating survey";
                }

                return buildErrorResponse(msg, Status.INTERNAL_SERVER_ERROR);
            }

            newSurvey = surveyDao.getById(newSurveyId);
            if (newSurvey != null) {
                msg = String.format("Created Survey with id, title: %d, %s",
                        newSurvey.getSurveyid(), newSurvey.getTitle());
                LOG.debug(msg);
                surveyResponse = new SurveyResponse(true, msg);
                surveyResponse.getSurveys().add(newSurvey);
                response = Response.ok(surveyResponse).status(Status.CREATED).build();
            } else {
                // TODO: double check dao, if null or throws exception
                response = buildErrorResponse("Unhandled response from server creating survey");
            }
        } catch(IllegalArgumentException e) {
            LOG.error("Invalid Survey", e);
            return buildErrorResponse("Invalid survey", Status.BAD_REQUEST);
        } catch(Exception e) {
            String msg = String.format("Unhandled exception creating survey: %s", e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg, Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response updateSurvey(final int surveyId, Survey survey) {
        Response response = null;
        Survey updatedSurvey = null;
        SurveyResponse surveyResponse = null;
        int updateSurveyResponse;
        try {
            // Won't update surveyid, so setting to provided surveyId
            if(survey.getSurveyid() != surveyId) {
                throw new IllegalArgumentException("surveyid doesn't match resource");
            }
            validateSurvey(survey, true);

            int updateStatus = updateSurveyResponse = surveyDao.updateSurvey(survey);
            if(updateStatus <= 1) {
                // Error
            }
            // TODO: don't grab it here, just return it from the updateSurvey...
            updatedSurvey = surveyDao.getById(surveyId);

            if(updatedSurvey != null) {
                surveyResponse = new SurveyResponse(true, "Successfully updated survey");
                surveyResponse.getSurveys().add(updatedSurvey);
                response = Response.ok(surveyResponse).build();
            } else {
                // TODO: double check dao, if null or throws exception
                switch(updateSurveyResponse) {
                    case 0: // Not Found
                        response = buildNotFoundResponse("Survey not found");
                        break;
                    case -1:
                        response = buildErrorResponse("Unhandled error updating survey");
                        break;
                    default:
                        response = buildErrorResponse("Unhandled error updating survey");

                }
            }

        } catch(IllegalArgumentException e) {
            String msg = String.format("Invalid survey: %s", e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg, Status.BAD_REQUEST);
        } catch(SurveyHistoryException e) {
            String msg = "Error backing up current Survey, try again.";
            LOG.error(msg, e);
            return buildErrorResponse(msg, Status.EXPECTATION_FAILED);
        } catch(Exception e) {
            String msg = String.format("Unhandled exception updating survey: %s", e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg, Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @Override
    public Response removeSurvey(int surveyId) {
        Response response = null;
        SurveyResponse surveyResponse = null;
        boolean ret = false;

        try {

            if (surveyId > 0) {
                int removedRows = surveyDao.removeSurvey(surveyId);
                if (removedRows == 1) {
                    surveyResponse = new SurveyResponse(true,
                            String.format("Successfully removed survey with ID: %d", surveyId));
                    response = Response.ok(surveyResponse).build();
                } else if(removedRows == 0) {
                    response = buildNotFoundResponse(String.format("Survey with ID %d not found", surveyId));
                } else {
                    response = buildErrorResponse("Unhandled exception removing survey");
                }

            } else {
                response = buildErrorResponse(String.format("Invalid SurveyID: %d", surveyId), Status.BAD_REQUEST);
            }

        } catch(Exception e) {
            final String msg = String.format("Unhandled exception deleting survey with ID %d", surveyId);
            LOG.error(msg, e);

            return buildErrorResponse(msg);
        }

        return response;
    }


    @Override
    public Response getSurveyResult(int resultId) {

        Response response = null;
        SurveyResult surveyResult = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            surveyResult = surveyDao.getSurveyResult(resultId);
            if (surveyResult != null) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved survey result");
                surveyResponse.getSurveyResults().add(surveyResult);
                response = Response.ok(surveyResponse).status(200).build();
            } else {
                String msg = String.format("Survey Result with ID %d not found", resultId);
                response = buildNotFoundResponse(msg);
            }
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting survey result with ID %d: %s",
                    resultId, e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }


    @Override
    public Response getSurveyResults() {
        Response response = null;
        List<SurveyResult> surveyResults = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            surveyResults = surveyDao.getSurveyResults();
            if (surveyResults != null && !surveyResults.isEmpty()) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved survey results");
                surveyResponse.setSurveyResults(surveyResults);
                response = Response.ok(surveyResponse).build();
            } else if(surveyResults != null && surveyResults.isEmpty()) {
                String msg = "No survey results";
                surveyResponse.setMessage(msg);
                response = Response.ok(surveyResponse).build();
            } // TODO: ensure dao doesn't return a null
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting survey results: %s",
                    e.getMessage());
            LOG.error(msg, e);
            response = buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response getSurveyResultsForSurvey(int surveyId) {
        Response response = null;
        List<SurveyResult> surveyResults = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            surveyResults = surveyDao.getSurveyResultsForSurvey(surveyId);
            if(surveyResults == null ) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("No results found for that survey");
                response = Response.ok(surveyResponse).status(Status.NOT_FOUND).build();
            } else if (surveyResults != null && !surveyResults.isEmpty()) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved survey results for survey");
                surveyResponse.setSurveyResults(surveyResults);
                response = Response.ok(surveyResponse).build();
            } else if(surveyResults != null && surveyResults.isEmpty()) {
                String msg = String.format("No survey results for survey with ID %d", surveyId);
                surveyResponse.setMessage(msg);
                response = Response.ok(surveyResponse).build();
            } // TODO: ensure dao doesn't return a null
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting survey results for surveyid %d: %s",
                    surveyId, e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }


    @Override
    public Response createSurveyResult(SurveyResult request) {
        Response response = null;
        SurveyResponse surveyResponse = null;
        SurveyResult newSurveyResult = null;
        int newSurveyResultId;
        try {
            validateSurveyResult(request, false);
            newSurveyResultId = surveyDao.createSurveyResult(request);
            newSurveyResult = surveyDao.getSurveyResult(newSurveyResultId);
            if (newSurveyResult != null) {
                String msg = "Created Survey Result";
                LOG.debug(msg);
                surveyResponse = new SurveyResponse(true, msg);
                surveyResponse.getSurveyResults().add(newSurveyResult);
                response = Response.ok(surveyResponse).status(Status.CREATED).build();
            } else {
                // TODO: double check dao, if null or throws exception
                response = buildErrorResponse("Unhandled response from server creating survey");
            }
        } catch(IllegalArgumentException e) {
            LOG.error("Invalid SurveyResult: " + e.getMessage(), e);
            return buildErrorResponse("Invalid SurveyResult: " + e.getMessage(), Status.BAD_REQUEST);
        } catch(Exception e) {
            String msg = String.format("Unhandled exception creating SurveyResult: %s", e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response updateSurveyResult(final int resultId, SurveyResult request) {
        Response response = null;
        SurveyResult updatedSurveyResult = null;
        SurveyResponse surveyResponse = null;
        int updateResultRows;
        try {
            // Won't update surveyresultid
            if(request.getSurveyresultid() != resultId) {
                throw new IllegalArgumentException("surveyresultid doesn't match resource");
            }

            validateSurveyResult(request, true);
            updateResultRows = surveyDao.updateSurveyResult(request);
            updatedSurveyResult = surveyDao.getSurveyResult(resultId);

            if(updatedSurveyResult != null) {
                surveyResponse = new SurveyResponse(true, "Successfully updated survey result");
                surveyResponse.getSurveyResults().add(updatedSurveyResult);
                response = Response.ok(surveyResponse).build();
            } else {

                if(updateResultRows == 0) {
                    response = buildNotFoundResponse("SurveyResult not found to update");
                } else {
                    response = buildErrorResponse("Unhandled response from server updating survey result");
                }
            }

        } catch(IllegalArgumentException e) {
            String msg = String.format("Invalid survey result: %s", e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg, Status.BAD_REQUEST);
        } catch(Exception e) {
            String msg = String.format("Unhandled exception updating survey result: %s", e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response removeSurveyResult(int resultId) {
        Response response = null;
        SurveyResponse surveyResponse = null;
        boolean ret = false;

        try {

            if (resultId > 0) {
                int removeRows = surveyDao.removeSurveyResult(resultId);

                if (removeRows == 1) {
                    surveyResponse = new SurveyResponse(true,
                            String.format("Successfully removed survey result with ID: %d", resultId));
                    response = Response.ok(surveyResponse).build();
                } else if(removeRows == 0) {
                    response = buildNotFoundResponse(String.format("Survey Result with ID %d not found", resultId));
                } else {
                    response = buildErrorResponse("Unhandled exception removing SurveyResult");
                }
            } else {
                response = buildErrorResponse(String.format("Invalid Survey Result ID: %d", resultId),
                        Status.BAD_REQUEST);
            }

        } catch(Exception e) {
            final String msg = String.format("Unhandled exception deleting survey result with ID %d", resultId);
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response getSurveyHistoryById(long surveyHistoryId) {
        Response response = null;
        SurveyHistory survey = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            survey = surveyDao.getSurveyHistoryById(surveyHistoryId);
            if (survey != null) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved survey history");
                surveyResponse.getSurveyHistories().add(survey);
                response = Response.ok(surveyResponse).status(Status.OK).build();
            } else {
                String msg = String.format("Survey history with surveyHistoryId %d not found", surveyHistoryId);
                response = buildNotFoundResponse(msg);
            }
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting survey history with surveyhistoryid %d: %s",
                    surveyHistoryId, e.getMessage());
            LOG.error(msg, e);
            return buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response getSurveyHistoriesForSurvey(int surveyId) {
        Response response = null;
        List<SurveyHistory> surveyHistories = null;
        SurveyResponse surveyResponse = new SurveyResponse();

        try {
            surveyHistories = surveyDao.getSurveyHistory(surveyId);
            if (surveyHistories != null && !surveyHistories.isEmpty()) {
                surveyResponse.setSuccess(true);
                surveyResponse.setMessage("Successfully retrieved survey results");
                surveyResponse.setSurveyHistories(surveyHistories);
                response = Response.ok(surveyResponse).build();
            } else if(surveyHistories != null && surveyHistories.isEmpty()) {
                String msg = "No history results for survey";
                response = buildNotFoundResponse(msg);
            } // TODO: ensure dao doesn't return a null
        } catch(Exception e) {
            String msg = String.format("Unhandled exception getting history for survey: %s",
                    e.getMessage());
            LOG.error(msg, e);
            response = buildErrorResponse(msg);
        }

        return response;
    }

    @Override
    public Response removeSurveyHistory(int surveyHistoryId) {
        return null;
    }

    @Override
    public Response removeAllHistoryForSurvey(int surveyId) {
        return null;
    }

    private void validateSurvey(Survey survey, boolean hasId) throws IllegalArgumentException {
        if(survey == null) {
            throw new IllegalArgumentException("Survey cannot be null");
        }

        boolean hasErrors = false;
        StringBuilder errors = new StringBuilder("Invalid survey: ");

        if(hasId && survey.getSurveyid() <= 0) {
            errors.append("surveyid must be greater than 0. ");
            hasErrors = true;
        }

        if(survey.getTitle() == null || survey.getTitle().isEmpty()) {
            errors.append("title must be specified. ");
            hasErrors = true;
        }

        if(survey.getSurvey() == null) {
            errors.append("Survey cannot be null. ");
            hasErrors = true;
        }

        // TODO: validate against json schema?

        if(hasErrors) {
            throw new IllegalArgumentException(errors.toString());
        }
    }

    /**
     * Performs cursory validation on {@link SurveyResult} entity
     *
     * @param surveyResult the entity to validate
     * @param hasId specifies whether or not missing an id is valid in this context
     *
     * @throws IllegalArgumentException
     */
    private void validateSurveyResult(SurveyResult surveyResult, boolean hasId) throws IllegalArgumentException {

        if(surveyResult == null) {
            throw new IllegalArgumentException("SurveyResult cannot be null");
        }

        boolean hasErrors = false;
        StringBuilder errors = new StringBuilder("Invalid: ");

        if(hasId && surveyResult.getSurveyresultid() <= 0) {
            errors.append("surveyresultid must be greater than 0. ");
            hasErrors = true;
        }

        if(surveyResult.getUserid() == null || surveyResult.getUserid().isEmpty()) {
            // TODO: further validate it should be an email
            // TODO: further validate the user should exist
            errors.append("user must be specified. ");
            hasErrors = true;
        }

        if(surveyResult.getSurveyresult() == null) {
            errors.append("SurveyResult cannot be null. ");
            hasErrors = true;
        }

        // TODO: validate against json schema?

        if(hasErrors) {
            throw new IllegalArgumentException(errors.toString());
        }
    }


    /**
     * Utility method for building 404 responses. A status of 404/NOT_FOUND
     * is used. success is left as false.
     *
     * @param message the message explaining what was not found
     *
     * @return a Response object with the specified information
     */
    private Response buildNotFoundResponse(String message) {
        SurveyResponse surveyResponse = new SurveyResponse();
        surveyResponse.setMessage(message);
        return Response.ok(surveyResponse).status(404).build();
    }

    /**
     * Utility method for building error responses. If a Status is not specified, 500/INTERNAL_SERVER_ERROR
     * is used. success is left as false.
     *
     * @param message the message explaining the error
     * @param status the HTTP response code to use. If not set, 500/INTERNAL SERVER ERROR is used
     *
     * @return a Response object with the specified information
     */
    private Response buildErrorResponse(String message, Status... status) {
        SurveyResponse surveyResponse = new SurveyResponse();
        surveyResponse.setMessage(message);
        Response.Status code = (status.length > 0) ? status[0] : Status.INTERNAL_SERVER_ERROR;
        return Response.ok(surveyResponse).status(code).build();
    }


    /**
     * For bean management from context, or to set a separately initialized DAO
     *
     * @param surveyDao the SurveyDAO to use to access data
     */
    public void setSurveyDao(SurveyDAO surveyDao) {
        this.surveyDao = surveyDao;
    }

}
