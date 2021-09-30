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

import edu.mit.ll.nics.common.entity.FormType;
import java.util.ArrayList;
import java.util.Collection;

import edu.mit.ll.nics.common.entity.Incident;
import edu.mit.ll.nics.common.entity.IncidentType;
import edu.mit.ll.nics.common.entity.IncidentOrg;
import edu.mit.ll.nics.common.entity.Org;
import edu.mit.ll.nics.common.entity.datalayer.Folder;

public class IncidentServiceResponse {

    private String message;

    private Collection<Incident> incidents = new ArrayList<Incident>();

    private Collection<IncidentType> incidentTypes = new ArrayList<IncidentType>();

    private Collection<FormType> formTypes = new ArrayList<>();

    private Collection<Integer> incidentIds = new ArrayList<>();

    private Collection<IncidentOrg> incidentOrgs;

    private Org owningOrg;

    private Folder incidentFolder;

    // TODO: Really used for returning a count REST request; i.e., do not get
    // the list of Incidents just the count.
    private int count;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Collection<Incident> getIncidents() {
        return incidents;
    }

    public void setIncidents(Collection<Incident> incidents) {
        this.incidents = incidents;
        if(this.incidents != null) {
            count = this.incidents.size();
        }
    }

    public Collection<IncidentType> getIncidentTypes() {
        return incidentTypes;
    }

    public void setIncidentTypes(Collection<IncidentType> incidentTypes) {
        this.incidentTypes = incidentTypes;
    }

    public Collection<FormType> getFormTypes() {
        return formTypes;
    }

    public void setFormTypes(Collection<FormType> formTypes) {
        this.formTypes = formTypes;
    }

    public Collection<Integer> getIncidentIds() {
        return incidentIds;
    }

    public void setIncidentIds(Collection<Integer> incidentIds) {
        this.incidentIds = incidentIds;
    }

    public Collection<IncidentOrg> getIncidentOrgs() {
        return incidentOrgs;
    }

    public void setIncidentOrgs(Collection<IncidentOrg> incidentOrgs) {
        this.incidentOrgs = incidentOrgs;
    }

    public Org getOwningOrg() {
        return owningOrg;
    }

    public void setOwningOrg(Org owningOrg) {
        this.owningOrg = owningOrg;
    }

    public void setIncidentFolder(Folder folder) { this.incidentFolder = folder;}

    public Folder getIncidentFolder(){ return this.incidentFolder; }

    public String toString() {
        return "IncidentServiceResponse [Incidents=" + incidents + ", message="
                + message + "]";
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}

