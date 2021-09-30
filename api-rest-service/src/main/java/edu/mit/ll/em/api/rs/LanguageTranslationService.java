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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.annotation.PostConstruct;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;

/**
 * Language Service Interface for managing country language translations.
 */
@Api("Language Translations")
@Path("/translation")
public interface LanguageTranslationService {
    /** The identity header key: {@value} */
    String IDENTITY_HEADER = "X-Remote-User";

    /** Parameter for the language code: {@value} */
    String PARAM_LANGUAGECODE = "code";


    /**
     * Message string for loading Language translation file: {@value}
     */
    String MESSAGE_LOADING_TRANSLATION = "Loading translation file: {}";

    /**
     * Error message string for parsing Language translation file: {@value}
     */
    String MESSAGE_ERROR_TRANSLATION_JSON = "Exception parsing JSON Translation file {}";

    /**
     * Error message string for reading Language translation file: {@value}
     */
    String MESSAGE_ERROR_TRANSLATION_IO = "IOException reading JSON Translation file {}";

    /**
     * Error message string for invalid Language translation configuration: {@value}
     */
    String MESSAGE_INVALID_CONFIG = "Language Translations are not properly configured on this system!";

    /**
     * Success message string: {@value}
     */
    String MESSAGE_SUCCESS = "Success";

    /**
     * Error message string for failing to find Language translation by code: {@value}
     */
    String MESSAGE_BAD_LANGUAGE_CODE = "Failed to find Language Translation with code ";

    /**
     * Error message string for unauthorized Language translation creation: {@value}
     */
    String MESSAGE_NOT_AUTHORIZED_CREATE = "Not authorized to create a Language";

    /**
     * Error message string for unauthorized Language translation modification: {@value}
     */
    String MESSAGE_NOT_AUTHORIZED_MODIFY = "Not authorized to modify Language Translations";

    /**
     * Error message string for missing request parameters: {@value}
     */
    String MESSAGE_MISSING_FIELDS = "Missing required fields!";

    /**
     * Error message string for failure to write new Language translation: {@value}
     */
    String MESSAGE_FAIL_LANGUAGE_WRITE = "Failed to write new Language";

    /**
     * Error message string for failures generating default Language translation map: {@value}
     */
    String MESSAGE_EXCEPTION_DEFAULT_MAP = "Exception generating default translation map!";

    /**
     * Responsible for retrieving all Language Translations
     * @return {@link LanguageTranslationResponse} containing all Language Translations
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved Language Translations"),
        @ApiResponse(code = 500, message = "Error retrieving Language Translation")
    })
    Response getAllLanguageTranslations();

    /**
     * Responsible for retrieving all Language Translations
     * @return {@link LanguageTranslationResponse} containing all Language Translations
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved Language Translation codes"),
        @ApiResponse(code = 500, message = "Error retrieving Language Translation codes")
    })
    @Path("/codes")
    Response getLanguageTranslationCodes();

    /**
     * Responsible for retrieving Language Translations by language code
     * @return {@link LanguageTranslationResponse} containing the Language Translations
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved Language Translation codes"),
        @ApiResponse(code = 404, message = "Language code not found"),
        @ApiResponse(code = 500, message = "Error creating Language")
    })
    @Path("/codes/{code}")
    Response getLanguageTranslationByCode(@PathParam(PARAM_LANGUAGECODE) String code);

    /**
     * Update or create a Language Translation value for key and Language
     * @param code Language code
     * @param translation {@link LanguageTranslationRequest} containing the Language Translation properties
     * @param requestingUser the username of the user making the request
     * @return
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated Language Translations"),
        @ApiResponse(code = 400, message = "Invalid request"),
        @ApiResponse(code = 403, message = "Not authorized to create Language"),
        @ApiResponse(code = 404, message = "Language code not found"),
        @ApiResponse(code = 500, message = "Error creating Language")
    })
    @Path("/{code}")
    Response updateTranslation(@PathParam(PARAM_LANGUAGECODE) String code,
                               LanguageTranslationRequest translation,
                               @HeaderParam(IDENTITY_HEADER) String requestingUser);

    /**
     * Creates a new Language for translations
     * @param translation
     * @param requestingUser the username of the user making the request
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Successfully created Language"),
        @ApiResponse(code = 400, message = "Invalid request"),
        @ApiResponse(code = 403, message = "Not authorized to create Language"),
        @ApiResponse(code = 500, message = "Error creating Language")
    })
    @Path("/")
    Response createLanguage(LanguageTranslationRequest translation,
                            @HeaderParam(IDENTITY_HEADER) String requestingUser);
}
