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

import org.json.*;

/**
 *
 */
public class LanguageTranslation {
    /**
     * Language code
     */
    private String code;

    /**
     * Name of the Language
     */
    private String language;

    /**
     * Default language flag
     */
    private boolean isDefault;

    /**
     * Translated text for the select Org dialog box (shown before loading the full web application)
     */
    private String selectOrgText;

    /** Keys for JSON object */
    public static final String KEY_CODE = "code";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_DEFAULT = "default";
    public static final String KEY_SELECTORG = "selectAnOrg";
    public static final String KEY_TRANSLATIONS = "translations";

    /**
     * Language translation map
     */
    private JSONObject map;

    /**
     * Constructor
     * @param code Language code
     * @param language Language name
     * @param isDefault Default Language flag
     * @param selectOrgText Translated text for the select Org dialog
     * @param map Language translation map
     */
    public LanguageTranslation(String code, String language, boolean isDefault, String selectOrgText, JSONObject map) {
        this.code = code;
        this.language = language;
        this.isDefault = isDefault;
        this.selectOrgText = selectOrgText;
        this.map = map;
    }

    public String getCode() {
        return code;
    }

    public String getLanguage() {
        return language;
    }

    public String getSelectOrgText() {
        return selectOrgText;
    }

    public String getTranslations() {
        return map.toString();
    }

    public void updateTranslation(String translationKey, String translationValue) {
        this.map.put(translationKey, translationValue);
    }

    /**
     * Generates JSON format to be saved to filesystem
     * @return
     */
    public JSONObject toFileFormat() {
        JSONObject out = new JSONObject();
        out.put(KEY_CODE, this.code);
        out.put(KEY_DEFAULT, this.isDefault);
        out.put(KEY_LANGUAGE, this.language);
        out.put(KEY_SELECTORG, this.selectOrgText);
        out.put(KEY_TRANSLATIONS, this.map);

        return out;
    }
}
